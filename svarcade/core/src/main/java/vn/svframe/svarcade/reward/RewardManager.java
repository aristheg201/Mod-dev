package vn.svframe.svarcade.reward;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.ThreadGuard;

/** Durable idempotent reward claims over typed providers. */
public final class RewardManager {
    public enum Status { PENDING, APPLIED }
    public record Claim(UUID id, UUID recipient, Id type, Map<String,Object> config, Status status) {
        public Claim { config = Values.map(config); }
    }

    private final ThreadGuard thread;
    private final Registry<RewardProvider> providers;
    private final int capacity;
    private final LinkedHashMap<UUID, Claim> claims = new LinkedHashMap<>();
    private long revision;

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
    public Claim apply(UUID id) {
        thread.check(); Claim claim = claims.get(id); if (claim == null) throw new IllegalArgumentException("Unknown reward claim");
        if (claim.status() == Status.APPLIED) return claim;
        RewardProvider provider = providers.require(claim.type()); Node config = new Node(claim.config(), "reward-claim"); provider.validate(config);
        provider.grant(claim.id(), claim.recipient(), config);
        Claim applied = new Claim(claim.id(), claim.recipient(), claim.type(), claim.config(), Status.APPLIED); claims.put(id, applied); revision = Math.incrementExact(revision); return applied;
    }
    public int retryPending(int maximum) {
        thread.check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Reward retry limit"); int applied = 0;
        for (UUID id : new ArrayList<>(claims.keySet())) {
            if (applied >= maximum) break; if (claims.get(id).status() == Status.PENDING) { apply(id); applied++; }
        }
        return applied;
    }
    public Optional<Claim> get(UUID id) { thread.check(); return Optional.ofNullable(claims.get(id)); }
    public long revision() { thread.check(); return revision; }
    public Map<String,Object> snapshot() {
        thread.check(); List<Object> rows = new ArrayList<>();
        for (Claim claim : claims.values()) rows.add(Map.of("id", claim.id().toString(), "recipient", claim.recipient().toString(), "type", claim.type().toString(), "config", claim.config(), "status", claim.status().name()));
        return Values.map(Map.of("schema", 1, "revision", revision, "claims", rows));
    }
    public void restore(Map<String,Object> state) {
        thread.check(); if (!claims.isEmpty() || revision != 0) throw new IllegalStateException("Rewards already initialized"); Node n = new Node(state, "reward-state");
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
