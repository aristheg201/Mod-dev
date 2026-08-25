package vn.svframe.mythiclibfabric.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared stat bridge used by Fabric adapters such as MMOItems and MMOCore. */
public final class StatProviderRegistry {
    @FunctionalInterface
    public interface Provider {
        double stat(UUID entityId, String stat);
    }

    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private StatProviderRegistry() {}

    public static AutoCloseable register(Provider provider) {
        Provider value = Objects.requireNonNull(provider, "provider");
        PROVIDERS.add(value);
        return () -> PROVIDERS.remove(value);
    }

    public static double stat(UUID entityId, String stat) {
        if (entityId == null || stat == null || stat.isBlank()) return 0.0d;
        double total = 0.0d;
        for (Provider provider : PROVIDERS) {
            double value;
            try {
                value = provider.stat(entityId, stat);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (Double.isFinite(value)) total += value;
        }
        return total;
    }

    public static int providerCount() {
        return PROVIDERS.size();
    }

    public static void clear() {
        PROVIDERS.clear();
    }
}
