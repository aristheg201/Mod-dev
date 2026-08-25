package vn.svframe.mythiclibfabric.runtime;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Region-provider bridge used by Fabric RegionCondition without a hard dependency on a Bukkit region plugin. */
public final class NativeRegionRegistry {
    @FunctionalInterface
    public interface Provider {
        Set<String> regions(ServerWorld world, BlockPos pos);
    }

    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private NativeRegionRegistry() { }

    public static AutoCloseable register(Provider provider) {
        if (provider == null) throw new IllegalArgumentException("provider");
        PROVIDERS.addIfAbsent(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static boolean contains(ServerWorld world, BlockPos pos, Set<String> names) {
        if (world == null || pos == null || names == null || names.isEmpty()) return false;
        Set<String> normalized = names.stream()
                .filter(java.util.Objects::nonNull)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (Provider provider : PROVIDERS) {
            Set<String> regions = provider.regions(world, pos);
            if (regions == null || regions.isEmpty()) continue;
            for (String region : regions) {
                if (region != null && normalized.contains(region.trim().toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
