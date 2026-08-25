package vn.svframe.mythiclibfabric;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric-side counterpart of MythicLib CooldownMap. */
public final class LegacyCooldownMap {
    private static final long FLUSH_INTERVAL = 60_000L;
    private final Map<String, LegacyCooldownInfo> map = new ConcurrentHashMap<>();
    private volatile long nextFlush = System.currentTimeMillis() + FLUSH_INTERVAL;

    public LegacyCooldownInfo apply(String key, double seconds) {
        tryFlush();
        String normalized = normalize(key);
        return map.compute(normalized, (ignored, current) -> {
            if (current == null) return new LegacyCooldownInfo(seconds);
            if (current.remaining() >= seconds * 1000.0D) return current;
            return new LegacyCooldownInfo(seconds);
        });
    }

    public LegacyCooldownInfo info(String key) {
        return map.get(normalize(key));
    }

    public double cooldownSeconds(String key) {
        LegacyCooldownInfo info = info(key);
        return info == null ? 0D : info.remaining() / 1000.0D;
    }

    public boolean isOnCooldown(String key) {
        LegacyCooldownInfo info = info(key);
        return info != null && !info.ended();
    }

    public void reset(String key) {
        map.remove(normalize(key));
    }

    public Set<String> keys() {
        return Set.copyOf(map.keySet());
    }

    public void clear() {
        map.clear();
    }

    private void tryFlush() {
        long now = System.currentTimeMillis();
        if (now < nextFlush) return;
        nextFlush = now + FLUSH_INTERVAL;
        map.values().removeIf(LegacyCooldownInfo::ended);
    }

    private static String normalize(String key) {
        if (key == null) throw new IllegalArgumentException("Cooldown key cannot be null");
        return key.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
