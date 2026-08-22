package vn.svframe.lively.world;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Semantic capability catalog used by bounded structure scanning. */
public final class BlockCapabilityRegistry {
    private final ConcurrentHashMap<String, Set<String>> exact = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> suffix = new ConcurrentHashMap<>();

    public BlockCapabilityRegistry() { registerDefaults(); }

    private void registerDefaults() {
        register("minecraft:chest", Set.of("storage"));
        register("minecraft:trapped_chest", Set.of("storage"));
        register("minecraft:barrel", Set.of("storage"));
        register("minecraft:lectern", Set.of("read", "teach"));
        register("minecraft:furnace", Set.of("smelt"));
        register("minecraft:blast_furnace", Set.of("smelt", "smith"));
        register("minecraft:smoker", Set.of("cook"));
        register("minecraft:crafting_table", Set.of("craft"));
        register("minecraft:bell", Set.of("gather"));
        register("minecraft:brewing_stand", Set.of("brew"));
        register("minecraft:anvil", Set.of("repair", "smith"));
        register("minecraft:chipped_anvil", Set.of("repair", "smith"));
        register("minecraft:damaged_anvil", Set.of("repair", "smith"));
        register("minecraft:grindstone", Set.of("repair"));
        register("minecraft:stonecutter", Set.of("craft"));
        register("minecraft:loom", Set.of("craft"));
        register("minecraft:cartography_table", Set.of("craft"));
        register("minecraft:smithing_table", Set.of("smith"));
        register("minecraft:composter", Set.of("farm"));
        register("minecraft:cauldron", Set.of("utility"));
        registerSuffix("_bed", Set.of("sleep"));
        registerSuffix("_door", Set.of("entrance", "openable"));
        registerSuffix("_fence_gate", Set.of("entrance", "openable"));
        registerSuffix("_shulker_box", Set.of("storage"));
    }

    public void register(String id, Set<String> capabilities) {
        exact.put(normalize(id), normalize(capabilities));
    }

    public void registerSuffix(String idSuffix, Set<String> capabilities) {
        if (idSuffix == null || idSuffix.isBlank()) throw new IllegalArgumentException("invalid block capability suffix");
        suffix.put(idSuffix.toLowerCase(Locale.ROOT), normalize(capabilities));
    }

    public Set<String> capabilities(String id) {
        String normalized = normalize(id);
        HashSet<String> result = new HashSet<>(exact.getOrDefault(normalized, Set.of()));
        String path = normalized.contains(":") ? normalized.substring(normalized.indexOf(':') + 1) : normalized;
        suffix.forEach((ending, values) -> { if (path.endsWith(ending)) result.addAll(values); });
        return Set.copyOf(result);
    }

    public Map<String, Set<String>> snapshot() { return Map.copyOf(exact); }
    public Map<String, Set<String>> suffixSnapshot() { return Map.copyOf(suffix); }

    private static Set<String> normalize(Set<String> values) {
        return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
