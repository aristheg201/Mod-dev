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
    public RateLimiter(int maxKeys, double capacity, double perTick) {
        if (maxKeys < 1 || !Double.isFinite(capacity) || capacity < 1 || !Double.isFinite(perTick) || perTick <= 0) throw new IllegalArgumentException("Invalid rate limit");
        this.maxKeys = maxKeys; this.capacity = capacity; this.perTick = perTick;
    }
    public synchronized boolean allow(UUID key, long tick) {
        Objects.requireNonNull(key);
        if (tick < lastTick) throw new IllegalArgumentException("Non-monotonic rate limit time");
        lastTick = tick;
        Bucket previous = buckets.get(key);
        if (previous == null && buckets.size() >= maxKeys) return false;
        double tokens = previous == null ? capacity : Math.min(capacity, previous.tokens() + (tick - previous.tick()) * perTick);
        boolean accepted = tokens >= 1;
        buckets.put(key, new Bucket(accepted ? tokens - 1 : tokens, tick));
        return accepted;
    }
    /** Bounded maintenance off the interaction hot path; only fully refilled keys are removed. */
    public synchronized void prune(long tick) {
        if (tick < lastTick) throw new IllegalArgumentException("Non-monotonic rate limit time");
        lastTick = tick;
        buckets.entrySet().removeIf(e -> e.getValue().tokens() + (tick - e.getValue().tick()) * perTick >= capacity);
    }
    public synchronized int size() { return buckets.size(); }
}
