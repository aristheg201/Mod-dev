package vn.svframe.svarcade.security;

import java.util.*;

/** Bounded token buckets using monotonic simulation ticks. Never evicts an active offender. */
public final class RateLimiter {
    private record Bucket(double tokens, long tick) { }
    private final Map<UUID, Bucket> buckets = new HashMap<>();
    private final int maxKeys;
    private final double capacity;
    private final double perTick;
    private long lastTick;
    private long lastCapacityPrune = -1;
    public RateLimiter(int maxKeys, double capacity, double perTick) {
        if (maxKeys < 1 || !Double.isFinite(capacity) || capacity < 1 || !Double.isFinite(perTick) || perTick <= 0) throw new IllegalArgumentException("Invalid rate limit");
        this.maxKeys = maxKeys; this.capacity = capacity; this.perTick = perTick;
    }
    public synchronized boolean allow(UUID key, long tick) {
        Objects.requireNonNull(key);
        checkTime(tick);
        Bucket previous = buckets.get(key);
        // Capacity recovery is owned here, not dependent on a caller remembering prune().
        // At most one full pass per simulation tick even under a stream of unknown actors.
        if (previous == null && buckets.size() >= maxKeys && tick > lastCapacityPrune) {
            prune(tick);
            lastCapacityPrune = tick;
        }
        if (previous == null && buckets.size() >= maxKeys) return false;
        double tokens = previous == null ? capacity : Math.min(capacity, previous.tokens() + (tick - previous.tick()) * perTick);
        boolean accepted = tokens >= 1;
        buckets.put(key, new Bucket(accepted ? tokens - 1 : tokens, tick));
        return accepted;
    }
    private void checkTime(long tick) {
        if (tick < lastTick) throw new IllegalArgumentException("Non-monotonic rate limit time");
        lastTick = tick;
    }
    /** Optional proactive maintenance. Correct admission never depends on calling this. */
    public synchronized void prune(long tick) {
        checkTime(tick);
        buckets.entrySet().removeIf(e -> e.getValue().tokens() + (tick - e.getValue().tick()) * perTick >= capacity);
    }
    public synchronized int size() { return buckets.size(); }
}
