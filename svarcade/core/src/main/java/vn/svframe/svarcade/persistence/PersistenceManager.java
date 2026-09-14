package vn.svframe.svarcade.persistence;

import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.config.Values;
import vn.svframe.svarcade.runtime.*;

/** Dirty, bounded, non-blocking persistence coordinator. Runtime DTO capture occurs on owner thread; disk I/O does not. */
public final class PersistenceManager implements AutoCloseable {
    public record Failure(String key, String operation, String detail) { }
    private enum Kind { SESSION_WRITE, PLAYER_WRITE, DELETE }
    private record Completion(String key, Kind kind, long version, Throwable failure) { }

    private final ThreadGuard thread;
    private final AtomicStore store;
    private final int maxInFlight;
    private final Map<UUID, Long> persistedSessions = new HashMap<>();
    private final Set<UUID> knownSessions = new HashSet<>(), persistedPlayers = new HashSet<>(), knownPlayers = new HashSet<>();
    private final Set<String> inFlight = new HashSet<>();
    private final ConcurrentLinkedQueue<Completion> completions = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Failure> failures = new ArrayDeque<>();
    private boolean closed;

    public PersistenceManager(ThreadGuard thread, AtomicStore store, int maxInFlight) {
        this.thread = Objects.requireNonNull(thread); this.store = Objects.requireNonNull(store);
        if (maxInFlight < 1 || maxInFlight > 4096) throw new IllegalArgumentException("Persistence in-flight limit"); this.maxInFlight = maxInFlight;
    }

    public int tick(Collection<GenericSession> sessions, int maxWrites) { return tick(sessions, null, maxWrites); }

    /** Call from owner tick. At most maxWrites total session/player writes or deletes are submitted. */
    public int tick(Collection<GenericSession> sessions, PlayerStateProtection protection, int maxWrites) {
        thread.check(); requireOpen(); if (maxWrites < 1 || maxWrites > 4096) throw new IllegalArgumentException("Persistence write budget");
        drain(); Map<UUID, GenericSession> live = new LinkedHashMap<>();
        for (GenericSession session : sessions) if (session.status() == GenericSession.Status.RUNNING) live.put(session.id(), session);
        int submitted = 0;
        for (GenericSession session : live.values()) {
            knownSessions.add(session.id()); String key = sessionKey(session.id());
            if (!canSubmit(submitted, maxWrites, key)) continue;
            long dirty = session.dirtyVersion(); if (persistedSessions.getOrDefault(session.id(), -1L) >= dirty) continue;
            SessionSnapshot snapshot = SessionSnapshot.capture(session); inFlight.add(key); submitted++;
            store.write(key, snapshot.encode()).whenComplete((ignored, failure) -> completions.add(new Completion(key, Kind.SESSION_WRITE, dirty, unwrap(failure))));
        }
        for (UUID stale : new ArrayList<>(knownSessions)) {
            String key = sessionKey(stale); if (!canSubmit(submitted, maxWrites, key)) break;
            if (live.containsKey(stale)) continue; inFlight.add(key); submitted++;
            store.delete(key).whenComplete((ignored, failure) -> completions.add(new Completion(key, Kind.DELETE, -1, unwrap(failure))));
        }
        if (protection != null && submitted < maxWrites) submitted += persistPlayers(live.values(), protection, maxWrites - submitted);
        return submitted;
    }

    private int persistPlayers(Collection<GenericSession> sessions, PlayerStateProtection protection, int budget) {
        Map<UUID, PlayerStateSnapshot> snapshots = protection.snapshots(); int submitted = 0;
        for (Map.Entry<UUID, PlayerStateSnapshot> entry : snapshots.entrySet()) {
            UUID player = entry.getKey(); knownPlayers.add(player); String key = playerKey(player);
            if (submitted >= budget || inFlight.size() >= maxInFlight || inFlight.contains(key) || persistedPlayers.contains(player)) continue;
            String session = sessions.stream().filter(s -> s.participants().containsKey(player)).map(s -> s.id().toString()).findFirst().orElse("");
            Map<String,Object> envelope = Values.map(Map.of("schema", 1, "player", player.toString(), "session", session, "snapshot", entry.getValue().toMap()));
            inFlight.add(key); submitted++;
            store.write(key, envelope).whenComplete((ignored, failure) -> completions.add(new Completion(key, Kind.PLAYER_WRITE, 0, unwrap(failure))));
        }
        for (UUID stale : new ArrayList<>(knownPlayers)) {
            if (submitted >= budget || inFlight.size() >= maxInFlight) break; String key = playerKey(stale);
            if (snapshots.containsKey(stale) || inFlight.contains(key)) continue; inFlight.add(key); submitted++;
            store.delete(key).whenComplete((ignored, failure) -> completions.add(new Completion(key, Kind.DELETE, -1, unwrap(failure))));
        }
        return submitted;
    }

    private boolean canSubmit(int submitted, int budget, String key) { return submitted < budget && inFlight.size() < maxInFlight && !inFlight.contains(key); }

    /** Marks recovered state as already durable, preventing an immediate redundant write. */
    public void recovered(GenericSession session) {
        thread.check(); requireOpen(); knownSessions.add(session.id()); persistedSessions.put(session.id(), session.dirtyVersion());
    }
    public void recoveredPlayer(UUID player) { thread.check(); requireOpen(); knownPlayers.add(player); persistedPlayers.add(player); }

    public void drain() {
        thread.check(); Completion completion;
        while ((completion = completions.poll()) != null) {
            inFlight.remove(completion.key());
            if (completion.failure() != null) { record(completion.key(), completion.kind().name().toLowerCase(Locale.ROOT), completion.failure()); continue; }
            switch (completion.kind()) {
                case SESSION_WRITE -> persistedSessions.merge(sessionId(completion.key()), completion.version(), Math::max);
                case PLAYER_WRITE -> persistedPlayers.add(playerId(completion.key()));
                case DELETE -> {
                    if (completion.key().startsWith("session_")) { UUID id = sessionId(completion.key()); knownSessions.remove(id); persistedSessions.remove(id); }
                    else if (completion.key().startsWith("player_")) { UUID id = playerId(completion.key()); knownPlayers.remove(id); persistedPlayers.remove(id); }
                }
            }
        }
    }

    public int inFlight() { thread.check(); drain(); return inFlight.size(); }
    public List<Failure> failures() { thread.check(); drain(); return List.copyOf(failures); }
    private void record(String key, String operation, Throwable failure) {
        if (failures.size() == 128) failures.removeFirst(); String text = String.valueOf(failure);
        failures.addLast(new Failure(key, operation, text.length() <= 2048 ? text : text.substring(0, 2048)));
    }
    static String sessionKey(UUID id) { return "session_" + encoded(id); }
    static String playerKey(UUID id) { return "player_" + encoded(id); }
    static UUID sessionId(String key) { return decoded(key, "session_"); }
    static UUID playerId(String key) { return decoded(key, "player_"); }
    private static String encoded(UUID id) { return id.toString().replace('-', '_'); }
    private static UUID decoded(String key, String prefix) {
        if (!key.startsWith(prefix)) throw new IllegalArgumentException("Unexpected state key"); return UUID.fromString(key.substring(prefix.length()).replace('_', '-'));
    }
    private static Throwable unwrap(Throwable failure) { if (failure instanceof CompletionException e && e.getCause() != null) return e.getCause(); return failure; }
    private void requireOpen() { if (closed) throw new IllegalStateException("Persistence manager closed"); }
    @Override public void close() { thread.check(); if (!closed) { drain(); closed = true; store.close(); } }
}
