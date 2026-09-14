package vn.svframe.svarcade.persistence;

import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.config.Node;
import vn.svframe.svarcade.runtime.*;

/** Startup recovery scanner. File reads are asynchronous; runtime acquisition/recovery stays on the owner thread. */
public final class RecoveryManager {
    public record Candidate(String key, SessionSnapshot snapshot) { }
    public record PlayerCandidate(String key, UUID player, UUID session, PlayerStateSnapshot snapshot) { }
    public record Failure(String key, String detail) { }
    public record Scan(List<Candidate> candidates, List<PlayerCandidate> players, List<Failure> failures) {
        public Scan { candidates = List.copyOf(candidates); players = List.copyOf(players); failures = List.copyOf(failures); }
    }
    public record Applied(List<UUID> recovered, Map<UUID,UUID> protectedOwners, List<Failure> aborted) {
        public Applied { recovered = List.copyOf(recovered); protectedOwners = Map.copyOf(protectedOwners); aborted = List.copyOf(aborted); }
    }

    private final ThreadGuard thread;
    private final AtomicStore store;
    private final int capacity;

    public RecoveryManager(ThreadGuard thread, AtomicStore store, int capacity) {
        this.thread = Objects.requireNonNull(thread); this.store = Objects.requireNonNull(store);
        if (capacity < 1 || capacity > 100_000) throw new IllegalArgumentException("Recovery capacity"); this.capacity = capacity;
    }

    /** Safe on any thread; all work is delegated to AtomicStore's bounded I/O executor. */
    public CompletableFuture<Scan> scan() {
        return store.keys().thenCompose(keys -> {
            List<String> relevant = keys.stream().filter(key -> key.startsWith("session_") || key.startsWith("player_")).toList();
            if (relevant.size() > capacity * 2L) return CompletableFuture.failedFuture(new IllegalStateException("Recovery candidate capacity"));
            CompletableFuture<Scan> chain = CompletableFuture.completedFuture(new Scan(List.of(), List.of(), List.of()));
            for (String key : relevant) chain = chain.thenCompose(scan -> store.read(key).handle((state, failure) -> parse(scan, key, state, failure)));
            return chain;
        });
    }

    private Scan parse(Scan scan, String key, Optional<Map<String,Object>> state, Throwable failure) {
        List<Candidate> candidates = new ArrayList<>(scan.candidates()); List<PlayerCandidate> players = new ArrayList<>(scan.players()); List<Failure> failures = new ArrayList<>(scan.failures());
        if (failure != null) failures.add(new Failure(key, concise(unwrap(failure))));
        else if (state.isEmpty()) failures.add(new Failure(key, "state disappeared during scan"));
        else try {
            if (key.startsWith("session_")) {
                SessionSnapshot snapshot = SessionSnapshot.decode(state.get()); UUID keyId = PersistenceManager.sessionId(key);
                if (!snapshot.id().equals(keyId)) throw new IllegalArgumentException("state key/session id mismatch"); candidates.add(new Candidate(key, snapshot));
            } else {
                Node n = new Node(state.get(), "player-recovery"); n.only("schema", "player", "session", "snapshot"); n.integer("schema", 1, 1);
                UUID player = UUID.fromString(n.string("player")); if (!player.equals(PersistenceManager.playerId(key))) throw new IllegalArgumentException("state key/player id mismatch");
                String sessionRaw = n.string("session"); UUID session = sessionRaw.isEmpty() ? null : UUID.fromString(sessionRaw);
                players.add(new PlayerCandidate(key, player, session, PlayerStateSnapshot.fromMap(n.node("snapshot").values())));
            }
        } catch (RuntimeException invalid) { failures.add(new Failure(key, concise(invalid))); }
        return new Scan(candidates, players, failures);
    }

    public Applied apply(GenericGameRuntime runtime, PersistenceManager persistence, Scan scan) {
        return apply(runtime, persistence, null, scan);
    }

    /** Owner-thread application. Player snapshots are recovered before sessions so mutation ownership survives a crash. */
    public Applied apply(GenericGameRuntime runtime, PersistenceManager persistence, PlayerStateProtection protection, Scan scan) {
        thread.check(); Objects.requireNonNull(runtime); Objects.requireNonNull(persistence); Objects.requireNonNull(scan);
        List<UUID> recovered = new ArrayList<>(); List<Failure> aborted = new ArrayList<>(scan.failures()); Map<UUID,UUID> owners = new LinkedHashMap<>();
        Set<String> failedKeys = new LinkedHashSet<>(); scan.failures().forEach(failure -> failedKeys.add(failure.key()));
        if (protection != null) for (PlayerCandidate player : scan.players()) {
            try { protection.recover(player.player(), player.snapshot()); persistence.recoveredPlayer(player.player()); if (player.session() != null) owners.put(player.player(), player.session()); }
            catch (RuntimeException failure) { aborted.add(new Failure(player.key(), concise(failure))); failedKeys.add(player.key()); }
        }
        for (Candidate candidate : scan.candidates()) {
            try { GenericSession session = runtime.recover(candidate.snapshot()); persistence.recovered(session); recovered.add(session.id()); }
            catch (RuntimeException failure) { aborted.add(new Failure(candidate.key(), concise(failure))); failedKeys.add(candidate.key()); }
        }
        for (String key : failedKeys) store.delete(key);
        return new Applied(recovered, owners, aborted);
    }

    private static Throwable unwrap(Throwable failure) { if (failure instanceof CompletionException e && e.getCause() != null) return e.getCause(); return failure; }
    private static String concise(Throwable failure) { String text = String.valueOf(failure); return text.length() <= 2048 ? text : text.substring(0, 2048); }
}
