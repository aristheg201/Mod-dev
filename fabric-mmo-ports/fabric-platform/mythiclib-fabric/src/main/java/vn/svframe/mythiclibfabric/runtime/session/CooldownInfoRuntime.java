package vn.svframe.mythiclibfabric.runtime.session;

import java.util.function.LongSupplier;

/** Exact native counterpart of MythicLib 1.7.1 CooldownInfo. */
public final class CooldownInfoRuntime {
    private final long initialCooldown;
    private final long castTime;
    private long nextUse;
    private final LongSupplier clock;

    public CooldownInfoRuntime(double seconds, LongSupplier clock) {
        this.clock = clock;
        this.castTime = clock.getAsLong();
        this.initialCooldown = (long) (seconds * 1000d);
        this.nextUse = castTime + initialCooldown;
    }

    public long castTime() { return castTime; }
    public long initialCooldown() { return initialCooldown; }
    public long nextUse() { return nextUse; }
    public long remaining() { return Math.max(0L, nextUse - clock.getAsLong()); }
    public boolean hasEnded() { return clock.getAsLong() > nextUse; }

    public void reduceRemainingCooldown(double p) {
        validatePercentage(p);
        nextUse -= (long) (remaining() * p);
    }

    public void reduceInitialCooldown(double p) {
        validatePercentage(p);
        nextUse -= (long) (initialCooldown * p);
    }

    public void reduceFlat(double seconds) { nextUse -= (long) (1000d * seconds); }

    private static void validatePercentage(double p) {
        if (p < 0d || p > 1d) throw new IllegalArgumentException("p must be between 0 and 1");
    }
}
