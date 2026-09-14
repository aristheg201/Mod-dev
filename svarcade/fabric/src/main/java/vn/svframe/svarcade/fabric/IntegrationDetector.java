package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.SemanticVersion;

/** Maps optional platform mod ids to definition integration capabilities and enforces minimum supported versions. */
final class IntegrationDetector {
    private static final Map<String, List<String>> MOD_IDS = Map.of(
            "svquest", List.of("svquest"),
            "svframe", List.of("svframe", "svframelib"),
            "luckperms", List.of("luckperms"),
            "economy", List.of("economy", "beconomy"),
            "placeholder", List.of("placeholder-api", "placeholderapi")
    );
    private static final int COBBLEMON_MAJOR = 1, COBBLEMON_MINOR = 8, COBBLEMON_PATCH = 1;
    private IntegrationDetector() { }

    static Set<String> available() {
        FabricLoader loader = FabricLoader.getInstance(); Set<String> result = new LinkedHashSet<>();
        if (supportedCobblemon(loader.getModContainer("cobblemon"))) result.add("cobblemon");
        MOD_IDS.forEach((capability, ids) -> { if (ids.stream().anyMatch(loader::isModLoaded)) result.add(capability); });
        return Set.copyOf(result);
    }

    static boolean supportedCobblemon(Optional<ModContainer> container) {
        if (container.isEmpty()) return false;
        Version raw = container.get().getMetadata().getVersion();
        if (!(raw instanceof SemanticVersion version)) return false;
        int major = version.getVersionComponent(0), minor = version.getVersionComponent(1), patch = version.getVersionComponent(2);
        return major > COBBLEMON_MAJOR
                || major == COBBLEMON_MAJOR && minor > COBBLEMON_MINOR
                || major == COBBLEMON_MAJOR && minor == COBBLEMON_MINOR && patch >= COBBLEMON_PATCH;
    }
}
