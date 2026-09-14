package vn.svframe.svarcade.systems.reward;

import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.reward.*;
import vn.svframe.svarcade.runtime.*;

/** Session-owned durable reward claims over an injected typed-provider registry. */
public final class RewardSystem implements SessionSystem, RewardAccess {
    public static final Id ID = Id.of("svarcade:rewards");
    public record Config(int capacity, Set<Id> allowedTypes, int maxCompletionsPerTick, int maxRetriesPerInterval,
                         long retryIntervalTicks, long grantTimeoutMillis) {
        public Config {
            allowedTypes = Set.copyOf(allowedTypes);
            if (capacity < 1 || capacity > 1_000_000 || allowedTypes.isEmpty() || allowedTypes.size() > 64
                    || maxCompletionsPerTick < 1 || maxCompletionsPerTick > 4096 || maxRetriesPerInterval < 1 || maxRetriesPerInterval > 4096
                    || retryIntervalTicks < 1 || retryIntervalTicks > 72_000 || grantTimeoutMillis < 100 || grantTimeoutMillis > 600_000)
                throw new ConfigException("Reward system limits");
        }
        public static Config parse(Node n) {
            n.only("capacity", "allowed_types", "max_completions_per_tick", "max_retries_per_interval", "retry_interval_ticks", "grant_timeout_millis");
            Set<Id> types = new LinkedHashSet<>(); for (String raw : n.strings("allowed_types")) types.add(Id.of(raw));
            return new Config((int)n.integer("capacity", 1, 1_000_000), types,
                    (int)n.integer("max_completions_per_tick", 1, 4096), (int)n.integer("max_retries_per_interval", 1, 4096),
                    n.integer("retry_interval_ticks", 1, 72_000), n.integer("grant_timeout_millis", 100, 600_000));
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        private final Registry<RewardProvider> providers;
        public Plan(Registry<RewardProvider> providers) { this.providers = Objects.requireNonNull(providers); }
        @Override public void validate(Node config) { Config parsed = Config.parse(config); for (Id type : parsed.allowedTypes()) providers.require(type); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(RewardAccess.ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = Config.parse(config); for (Id type : parsed.allowedTypes()) providers.require(type);
            RewardSystem system = new RewardSystem(session, providers, parsed); session.services().provide(RewardAccess.ACCESS, system); return system;
        }
    }

    private final GenericSession session;
    private final RewardManager manager;
    private final Config config;
    private final long grantTimeoutNanos;
    private boolean active, closed;
    private long nextRetryTick;
    private RewardSystem(GenericSession session, Registry<RewardProvider> providers, Config config) {
        this.session = Objects.requireNonNull(session); this.config = config; manager = new RewardManager(session.thread(), providers, config.capacity());
        grantTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(config.grantTimeoutMillis());
    }
    private void requireActive() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Reward system inactive"); }
    private void requireType(Id type) { if (!config.allowedTypes().contains(type)) throw new IllegalArgumentException("Reward type not allowed: " + type); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Reward system already started/closed"); active = true; }
    @Override public void tick(long tick) {
        requireActive(); long before = manager.revision(); manager.pollCompleted(config.maxCompletionsPerTick(), grantTimeoutNanos);
        if (tick >= nextRetryTick) { manager.startPending(config.maxRetriesPerInterval()); nextRetryTick = saturatingAdd(tick, config.retryIntervalTicks()); }
        publishChange(before);
    }
    private static long saturatingAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    @Override public RewardManager.Claim claim(UUID claimId, UUID recipient, Id type, Node configNode) {
        requireActive(); requireType(type); long before = manager.revision();
        try { return manager.claim(claimId, recipient, type, configNode); }
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
        session.thread().check(); if (schema != 1 || active || closed) throw new ConfigException("Reward system recovery state");
        for (Id type : restoredTypes(state)) requireType(type); manager.restore(state); active = true; nextRetryTick = 0;
    }
    private static Set<Id> restoredTypes(Map<String,Object> state) {
        Node root = new Node(state, "reward-state"); Set<Id> result = new LinkedHashSet<>();
        for (Node row : root.nodes("claims")) result.add(Id.of(row.string("type"))); return Set.copyOf(result);
    }
    @Override public void close() { session.thread().check(); if (closed) return; closed = true; active = false; }
}
