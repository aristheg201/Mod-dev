package vn.svframe.mythiclibfabric;

/** Millisecond-accurate counterpart of MythicLib CooldownInfo. */
public final class LegacyCooldownInfo {
    private final long initialCooldown;
    private final long castTime;
    private long nextUse;

    public LegacyCooldownInfo(double seconds) {
        castTime = System.currentTimeMillis();
        initialCooldown = (long) (seconds * 1000.0D);
        nextUse = castTime + initialCooldown;
    }

    public long castTime() { return castTime; }
    public long initialCooldown() { return initialCooldown; }
    public long nextUse() { return nextUse; }
    public long remaining() { return Math.max(0L, nextUse - System.currentTimeMillis()); }
    public boolean ended() { return System.currentTimeMillis() > nextUse; }

    public void reduceRemaining(double proportion) {
        validateProportion(proportion);
        nextUse -= (long) (remaining() * proportion);
    }

    public void reduceInitial(double proportion) {
        validateProportion(proportion);
        nextUse -= (long) (initialCooldown * proportion);
    }

    public void reduceFlat(double seconds) {
        nextUse -= (long) (1000.0D * seconds);
    }

    private static void validateProportion(double value) {
        if (value < 0D || value > 1D) throw new IllegalArgumentException("p must be between 0 and 1");
    }
}
