package vn.svframe.mythiclibfabric.runtime.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Exact native counterpart of MythicLib 1.7.1 CooldownMap semantics. */
public final class CooldownMapRuntime {
    private final Map<String, CooldownInfoRuntime> map = new HashMap<>();
    private final LongSupplier clock;
    private long nextFlush;

    public CooldownMapRuntime(LongSupplier clock) {
        this.clock = clock;
        this.nextFlush = clock.getAsLong() + 60_000L;
    }

    public CooldownInfoRuntime apply(String rawPath, double seconds) {
        tryFlush();
        String path = enumName(rawPath);
        return map.compute(path, (ignored, previous) -> {
            if (previous == null || previous.remaining() < seconds * 1000d) return new CooldownInfoRuntime(seconds, clock);
            return previous;
        });
    }

    public CooldownInfoRuntime info(String rawPath) { return map.get(enumName(rawPath)); }
    public double cooldownSeconds(String rawPath) {
        CooldownInfoRuntime info = info(rawPath);
        return info == null ? 0d : info.remaining() / 1000d;
    }
    public boolean isOnCooldown(String rawPath) { CooldownInfoRuntime info = info(rawPath); return info != null && !info.hasEnded(); }
    public void reset(String rawPath) { map.remove(enumName(rawPath)); }
    public Set<String> keys() { return Set.copyOf(map.keySet()); }
    public void clear() { map.clear(); }

    private void tryFlush() {
        long now = clock.getAsLong();
        if (now < nextFlush) return;
        nextFlush = now + 60_000L;
        map.values().removeIf(CooldownInfoRuntime::hasEnded);
    }

    static String enumName(String input) {
        if (input == null) throw new IllegalArgumentException("Cooldown key cannot be null");
        return input.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
