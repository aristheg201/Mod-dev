package vn.svframe.svarcade.systems.reward;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.*;
import vn.svframe.svarcade.runtime.*;

/** Session-owned durable reward claims over an injected typed-provider registry. */
public final class RewardSystem implements SessionSystem, RewardAccess {
    public static final Id ID = Id.of("svarcade:rewards");
    public record Config(int capacity, Set<Id> allowedTypes) {
        public Config {
            allowedTypes = Set.copyOf(allowedTypes);
            if (capacity < 1 || capacity > 1_000_000 || allowedTypes.isEmpty() || allowedTypes.size() > 64) throw new ConfigException("Reward system limits");
        }
        public static Config parse(Node n) {
            n.only("capacity", "allowed_types"); Set<Id> types = new LinkedHashSet<>(); for (String raw : n.strings("allowed_types")) types.add(Id.of(raw));
            return new Config((int)n.integer("capacity", 1, 1_000_000), types);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        private final Registry<RewardProvider> providers;
        public Plan(Registry<RewardProvider> providers) { this.providers = Objects.requireNonNull(providers); }
        @Override public void validate(Node config) {
            Config parsed = Config.parse(config); for (Id type : parsed.allowedTypes()) providers.require(type);
        }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(RewardAccess.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = Config.parse(config); for (Id type : parsed.allowedTypes()) providers.require(type);
            RewardSystem system = new RewardSystem(session, providers, parsed); session.services().provide(RewardAccess.ACCESS, system); return system;
        }
    }

    private final GenericSession session;
    private final RewardManager manager;
    private final Set<Id> allowedTypes;
    private boolean active, closed;
    private RewardSystem(GenericSession session, Registry<RewardProvider> providers, Config config) {
        this.session = Objects.requireNonNull(session); manager = new RewardManager(session.thread(), providers, config.capacity()); allowedTypes = config.allowedTypes();
    }
    private void requireActive() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Reward system inactive"); }
    private void requireType(Id type) { if (!allowedTypes.contains(type)) throw new IllegalArgumentException("Reward type not allowed: " + type); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Reward system already started/closed"); active = true; }
    @Override public RewardManager.Claim claim(UUID claimId, UUID recipient, Id type, Node config) {
        requireActive(); requireType(type); long before = manager.revision();
        try { return manager.claim(claimId, recipient, type, config); }
        finally { publishChange(before); }
    }
    @Override public RewardManager.Claim apply(UUID claimId) {
        requireActive(); RewardManager.Claim existing = manager.get(claimId).orElseThrow(() -> new IllegalArgumentException("Unknown reward claim")); requireType(existing.type());
        long before = manager.revision(); try { return manager.apply(claimId); } finally { publishChange(before); }
    }
    private void publishChange(long before) { if (manager.revision() != before) session.changed(); }
    @Override public Optional<RewardManager.Claim> get(UUID claimId) { requireActive(); return manager.get(claimId); }
    @Override public long revision() { requireActive(); return manager.revision(); }
    @Override public Map<String,Object> snapshot() { requireActive(); return manager.snapshot(); }
    @Override public int stateSchema() { return 1; }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (schema != 1 || active || closed) throw new ConfigException("Reward system recovery state"); manager.restore(state);
        for (Id type : manager.snapshot().containsKey("claims") ? restoredTypes(state) : Set.<Id>of()) requireType(type); active = true;
    }
    private static Set<Id> restoredTypes(Map<String,Object> state) {
        Node root = new Node(state, "reward-state"); Set<Id> result = new LinkedHashSet<>();
        for (Node row : root.nodes("claims")) result.add(Id.of(row.string("type"))); return Set.copyOf(result);
    }
    @Override public void close() { session.thread().check(); if (closed) return; closed = true; active = false; }
}
