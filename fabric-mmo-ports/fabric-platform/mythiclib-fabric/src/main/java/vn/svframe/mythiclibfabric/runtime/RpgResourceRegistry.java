package vn.svframe.mythiclibfabric.runtime;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared native bridge for live RPG resources such as mana and stamina. */
public final class RpgResourceRegistry {
    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private RpgResourceRegistry() {}

    public interface Provider {
        OptionalDouble current(UUID player, String resource);
        OptionalDouble maximum(UUID player, String resource);
        boolean add(UUID player, String resource, double amount);
    }

    public static AutoCloseable register(Provider provider) {
        if (provider == null) throw new IllegalArgumentException("provider");
        PROVIDERS.addIfAbsent(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static OptionalDouble current(UUID player, String resource) {
        String key = normalize(resource);
        for (Provider provider : PROVIDERS) {
            OptionalDouble value = provider.current(player, key);
            if (value != null && value.isPresent()) return value;
        }
        return OptionalDouble.empty();
    }

    public static OptionalDouble maximum(UUID player, String resource) {
        String key = normalize(resource);
        for (Provider provider : PROVIDERS) {
            OptionalDouble value = provider.maximum(player, key);
            if (value != null && value.isPresent()) return value;
        }
        return OptionalDouble.empty();
    }

    public static boolean add(UUID player, String resource, double amount) {
        String key = normalize(resource);
        for (Provider provider : PROVIDERS) {
            OptionalDouble current = provider.current(player, key);
            if (current != null && current.isPresent()) return provider.add(player, key, amount);
        }
        return false;
    }

    public static boolean has(UUID player, String resource, double amount) {
        OptionalDouble value = current(player, resource);
        return value.isPresent() && value.getAsDouble() + 1.0E-9 >= Math.max(0.0, amount);
    }

    public static boolean consume(UUID player, String resource, double amount) {
        double positive = Math.max(0.0, amount);
        if (positive == 0.0) return true;
        if (!has(player, resource, positive)) return false;
        return add(player, resource, -positive);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
