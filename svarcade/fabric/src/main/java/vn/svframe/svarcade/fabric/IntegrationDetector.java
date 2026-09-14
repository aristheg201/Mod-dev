package vn.svframe.svarcade.fabric;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Maps optional platform mod ids to definition integration capabilities. */
final class IntegrationDetector {
    private static final Map<String, List<String>> MOD_IDS = Map.of(
            "cobblemon", List.of("cobblemon"),
            "svquest", List.of("svquest"),
            "svframe", List.of("svframe", "svframelib"),
            "luckperms", List.of("luckperms"),
            "economy", List.of("economy", "beconomy"),
            "placeholder", List.of("placeholder-api", "placeholderapi")
    );
    private IntegrationDetector() { }

    static Set<String> available() {
        FabricLoader loader = FabricLoader.getInstance(); Set<String> result = new LinkedHashSet<>();
        MOD_IDS.forEach((capability, ids) -> { if (ids.stream().anyMatch(loader::isModLoaded)) result.add(capability); });
        return Set.copyOf(result);
    }
}
