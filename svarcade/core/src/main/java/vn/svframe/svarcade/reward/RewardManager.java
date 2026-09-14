package vn.svframe.svarcade.reward;

import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.ThreadGuard;

/** Durable idempotent reward claims over typed asynchronous providers. */
public final class RewardManager {
    public enum Status { PENDING, APPLIED }
    public record Claim(UUID id, UUID recipient, Id type, Map<String,Object> config, Status status) {
        public Claim { config = Values.map(config); }
    }
    private record InFlight(CompletableFuture<Void> future, long startedNanos) {
        private InFlight { Objects.requireNonNull(future); }
    }

    private final ThreadGuard thread;
    private final Registry<RewardProvider> providers;
    private final int capacity;
    private final LinkedHashMap<UUID, Claim> claims = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, InFlight> inFlight = new LinkedHashMap<>();
    private long revision, providerFailures, providerTimeouts;

    public RewardManager(ThreadGuard thread, Registry<RewardProvider> providers, int capacity) {
        this.thread = Objects.requireNonNull(thread); this.providers = Objects.requireNonNull(providers);
        if (capacity < 1 || capacity > 1_000_000) throw new IllegalArgumentException("Reward claim capacity"); this.capacity = capacity;
    }
    public Claim claim(UUID id, UUID recipient, Id type, Node config) {
        thread.check(); Objects.requireNonNull(id); Objects.requireNonNull(recipient); Objects.requireNonNull(type); Objects.requireNonNull(config);
        Claim existing = claims.get(id); if (existing != null) return existing;
        if (claims.size() >= capacity) throw new IllegalStateException("Reward claim capacity"); RewardProvider provider = providers.require(type); provider.validate(config);
        Claim pending = new Claim(id, recipient, type, config.values(), Status.PENDING); claims.put(id, pending); revision = Math.incrementExact(revision);
        return apply(id);
    }
    /** Starts a pending grant if needed and settles immediately only when the provider already completed. Never blocks. */
    public Claim apply(UUID id) {
        thread.check(); Claim claim = requireClaim(id); if (claim.status() == Status.APPLIED) return claim;
        if (!inFlight.containsKey(id)) start(claim);
        settle(id, true); return claims.get(id);
    }
    private void start(Claim claim) {
        RewardProvider provider = providers.require(claim.type()); Node config = new Node(claim.config(), "reward-claim"); provider.validate(config);
        CompletionStage<Void> stage = Objects.requireNonNull(provider.grant(claim.id(), claim.recipient(), config), "Reward provider future");
        inFlight.put(claim.id(), new InFlight(stage.toCompletableFuture(), System.nanoTime()));
    }
    private boolean settle(UUID id, boolean propagateFailure) {
        InFlight running = inFlight.get(id); if (running == null || !running.future().isDone()) return false;
        try {
            running.future().join(); Claim claim = requireClaim(id);
            if (claim.status() == Status.PENDING) {
                claims.put(id, new Claim(claim.id(), claim.recipient(), claim.type(), claim.config(), Status.APPLIED)); revision = Math.incrementExact(revision);
            }
            inFlight.remove(id); return true;
        } catch (CompletionException | CancellationException failure) {
            inFlight.remove(id); providerFailures = Math.incrementExact(providerFailures);
            if (propagateFailure) throw new IllegalStateException("Reward provider failed for claim " + id, failure.getCause() == null ? failure : failure.getCause());
            return false;
        }
    }
    /** Polls completed provider futures on the owner thread without expiring unfinished grants. */
    public int pollCompleted(int maximum) { return pollCompleted(maximum, Long.MAX_VALUE); }
    /** Polls completed futures and expires hung futures after the supplied monotonic timeout. Expired claims remain PENDING for idempotent retry. */
    public int pollCompleted(int maximum, long timeoutNanos) {
        thread.check(); if (maximum < 1 || maximum > 4096 || timeoutNanos < 1) throw new IllegalArgumentException("Reward completion limits");
        int applied = 0, inspected = 0; long now = System.nanoTime();
        for (UUID id : new ArrayList<>(inFlight.keySet())) {
            if (inspected++ >= maximum) break; InFlight running = inFlight.get(id); if (running == null) continue;
            if (running.future().isDone()) { if (settle(id, false)) applied++; continue; }
            if (timeoutNanos != Long.MAX_VALUE && now - running.startedNanos() >= timeoutNanos) {
                running.future().cancel(true); inFlight.remove(id); providerTimeouts = Math.incrementExact(providerTimeouts);
            }
        }
        return applied;
    }
    /** Restarts bounded pending claims that are not already in flight. Provider startup failures leave claims pending. */
    public int startPending(int maximum) {
        thread.check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Reward retry limit"); int started = 0;
        for (Claim claim : new ArrayList<>(claims.values())) {
            if (started >= maximum) break;
            if (claim.status() != Status.PENDING || inFlight.containsKey(claim.id())) continue;
            try { start(claim); started++; } catch (RuntimeException failure) { providerFailures = Math.incrementExact(providerFailures); }
        }
        return started;
    }
    /** Compatibility helper: schedules pending claims then polls already-completed futures without blocking. */
    public int retryPending(int maximum) { thread.check(); startPending(maximum); return pollCompleted(maximum); }
    private Claim requireClaim(UUID id) { Claim claim = claims.get(id); if (claim == null) throw new IllegalArgumentException("Unknown reward claim"); return claim; }
    public Optional<Claim> get(UUID id) { thread.check(); return Optional.ofNullable(claims.get(id)); }
    public int inFlight() { thread.check(); return inFlight.size(); }
    public long providerFailures() { thread.check(); return providerFailures; }
    public long providerTimeouts() { thread.check(); return providerTimeouts; }
    public long revision() { thread.check(); return revision; }
    public Map<String,Object> snapshot() {
        thread.check(); List<Object> rows = new ArrayList<>();
        for (Claim claim : claims.values()) rows.add(Map.of("id", claim.id().toString(), "recipient", claim.recipient().toString(), "type", claim.type().toString(), "config", claim.config(), "status", claim.status().name()));
        return Values.map(Map.of("schema", 1, "revision", revision, "claims", rows));
    }
    public void restore(Map<String,Object> state) {
        thread.check(); if (!claims.isEmpty() || !inFlight.isEmpty() || revision != 0) throw new IllegalStateException("Rewards already initialized"); Node n = new Node(state, "reward-state");
        n.only("schema", "revision", "claims"); if (n.integer("schema", 1, 1) != 1) throw new ConfigException("Reward schema"); long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        List<Node> rows = n.nodes("claims"); if (rows.size() > capacity) throw new ConfigException("Reward claim capacity");
        for (Node row : rows) {
            row.only("id", "recipient", "type", "config", "status"); UUID id = UUID.fromString(row.string("id")); Id type = Id.of(row.string("type")); RewardProvider provider = providers.require(type);
            Node config = row.node("config"); provider.validate(config); Status status = Status.valueOf(row.string("status")); Claim claim = new Claim(id, UUID.fromString(row.string("recipient")), type, config.values(), status);
            if (claims.putIfAbsent(id, claim) != null) throw new ConfigException("Duplicate reward claim");
        }
        revision = restoredRevision;
    }
}
