package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.SemanticVersion;

/** Lightweight install/version probes. Adapter usability is decided by PlatformIntegrations. */
final class IntegrationDetector {
    private static final Map<String, List<String>> MOD_IDS = Map.of(
            "svquest", List.of("svquest"),
            "svframe", List.of("svframe", "svframelib"),
            "luckperms", List.of("luckperms"),
            "economy", List.of("blanketeconomy", "beconomy", "economy"),
            "placeholder", List.of("placeholder-api", "placeholderapi")
    );
    private static final int COBBLEMON_MAJOR = 1, COBBLEMON_MINOR = 8, COBBLEMON_PATCH = 1;
    private IntegrationDetector() { }

    static boolean installed(String capability) {
        FabricLoader loader = FabricLoader.getInstance();
        if (capability.equals("cobblemon")) return loader.isModLoaded("cobblemon");
        List<String> ids = MOD_IDS.get(capability); return ids != null && ids.stream().anyMatch(loader::isModLoaded);
    }

    static boolean supportedCobblemon(Optional<ModContainer> container) {
        if (container.isEmpty()) return false;
        Version raw = container.get().getMetadata().getVersion();
        if (!(raw instanceof SemanticVersion version) || version.getVersionComponentCount() < 3) return false;
        int major = version.getVersionComponent(0), minor = version.getVersionComponent(1), patch = version.getVersionComponent(2);
        return major > COBBLEMON_MAJOR
                || major == COBBLEMON_MAJOR && minor > COBBLEMON_MINOR
                || major == COBBLEMON_MAJOR && minor == COBBLEMON_MINOR && patch >= COBBLEMON_PATCH;
    }
}
