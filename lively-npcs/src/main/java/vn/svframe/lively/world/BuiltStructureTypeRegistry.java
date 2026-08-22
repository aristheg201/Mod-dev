package vn.svframe.lively.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven type matching for discovered player-built structures.
 *
 * <p>Rules can require exact block IDs, block tags, semantic capabilities and block-ID suffixes. The registry
 * publishes an immutable snapshot only after the entire file validates, so a broken live edit never leaves half a
 * rule set active.</p>
 */
public final class BuiltStructureTypeRegistry {
    public record Rule(String id, int priority, Map<String, Integer> blocks, Map<String, Integer> tags,
                       Map<String, Integer> capabilities, Map<String, Integer> suffixes,
                       Set<String> addCapabilities) {
        public Rule {
            id = normalizeId(id);
            if (id.isBlank() || id.length() > 96) throw new IllegalArgumentException("invalid building type id");
            priority = Math.max(-10_000, Math.min(10_000, priority));
            blocks = validatedCounts(blocks, "blocks");
            tags = validatedCounts(tags, "tags");
            capabilities = validatedCounts(capabilities, "capabilities");
            suffixes = validatedCounts(suffixes, "suffixes");
            addCapabilities = addCapabilities == null ? Set.of() : addCapabilities.stream()
                    .map(BuiltStructureTypeRegistry::normalizeId).filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        boolean matches(Map<String, Integer> blockCounts, Map<String, Integer> tagCounts,
                        Map<String, Integer> capabilityCounts) {
            return containsAtLeast(blockCounts, blocks)
                    && containsAtLeast(tagCounts, tags)
                    && containsAtLeast(capabilityCounts, capabilities)
                    && suffixes.entrySet().stream().allMatch(required -> countSuffix(blockCounts, required.getKey()) >= required.getValue());
        }
    }

    public record Match(String type, Set<String> addCapabilities) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<Rule> rules = defaultRules();
    private static volatile Path sourceFile;

    private BuiltStructureTypeRegistry() {}

    public static Match classify(Map<String, Integer> blockCounts, Map<String, Integer> tagCounts,
                                 Map<String, Integer> capabilityCounts) {
        for (Rule rule : rules) {
            if (rule.matches(blockCounts, tagCounts, capabilityCounts)) return new Match(rule.id(), rule.addCapabilities());
        }
        return new Match("building", Set.of());
    }

    public static Match classify(Map<String, Integer> blockCounts, Map<String, Integer> capabilityCounts) {
        return classify(blockCounts, Map.of(), capabilityCounts);
    }

    public static Set<String> requiredTags() {
        HashSet<String> result = new HashSet<>();
        for (Rule rule : rules) result.addAll(rule.tags().keySet());
        return Set.copyOf(result);
    }

    public static Set<String> managedCapabilities() {
        HashSet<String> result = new HashSet<>();
        for (Rule rule : rules) result.addAll(rule.addCapabilities());
        return Set.copyOf(result);
    }

    public static List<Rule> snapshot() { return rules; }
    public static Path sourceFile() { return sourceFile; }

    /** Startup/reload entrypoint. The previous snapshot survives any parse/validation failure. */
    public static synchronized int load(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        if (!Files.isRegularFile(normalized)) writeDefaults(normalized);
        List<Rule> parsed;
        try (Reader reader = Files.newBufferedReader(normalized)) {
            parsed = parse(JsonParser.parseReader(reader));
        }
        rules = parsed;
        sourceFile = normalized;
        return parsed.size();
    }

    private static List<Rule> parse(JsonElement root) {
        if (root == null || !root.isJsonObject()) throw new IllegalArgumentException("building-types root must be an object");
        JsonElement listElement = root.getAsJsonObject().get("types");
        if (listElement == null || !listElement.isJsonArray()) throw new IllegalArgumentException("building-types.types must be an array");
        ArrayList<Rule> parsed = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (JsonElement element : listElement.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("building type entry must be an object");
            JsonObject object = element.getAsJsonObject();
            String id = requiredString(object, "id");
            int priority = object.has("priority") ? object.get("priority").getAsInt() : 0;
            Rule rule = new Rule(id, priority, counts(object, "blocks"), counts(object, "tags"),
                    counts(object, "capabilities"), counts(object, "suffixes"), strings(object, "add_capabilities"));
            if (!ids.add(rule.id())) throw new IllegalArgumentException("duplicate building type id: " + rule.id());
            parsed.add(rule);
        }
        if (parsed.size() > 256) throw new IllegalArgumentException("too many building type rules");
        parsed.sort(Comparator.comparingInt(Rule::priority).reversed().thenComparing(Rule::id));
        return List.copyOf(parsed);
    }

    private static Map<String, Integer> counts(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) return Map.of();
        if (!element.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (var entry : element.getAsJsonObject().entrySet()) result.put(entry.getKey(), entry.getValue().getAsInt());
        return result;
    }

    private static Set<String> strings(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) return Set.of();
        if (!element.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        HashSet<String> result = new HashSet<>();
        for (JsonElement value : element.getAsJsonArray()) result.add(value.getAsString());
        return result;
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("missing/invalid " + key);
        }
        return value.getAsString();
    }

    private static Map<String, Integer> validatedCounts(Map<String, Integer> raw, String field) {
        if (raw == null || raw.isEmpty()) return Map.of();
        if (raw.size() > 128) throw new IllegalArgumentException("too many " + field + " requirements");
        HashMap<String, Integer> result = new HashMap<>();
        raw.forEach((key, value) -> {
            String normalized = field.equals("suffixes") ? normalizeSuffix(key) : normalizeId(key);
            if (normalized.isBlank() || value == null || value < 1 || value > 65_536) {
                throw new IllegalArgumentException("invalid " + field + " requirement: " + key + "=" + value);
            }
            result.put(normalized, value);
        });
        return Map.copyOf(result);
    }

    private static boolean containsAtLeast(Map<String, Integer> actual, Map<String, Integer> required) {
        return required.entrySet().stream().allMatch(entry -> actual.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private static int countSuffix(Map<String, Integer> counts, String suffix) {
        return counts.entrySet().stream().filter(entry -> entry.getKey().endsWith(suffix)).mapToInt(Map.Entry::getValue).sum();
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSuffix(String value) {
        String normalized = normalizeId(value);
        if (normalized.contains(":")) normalized = normalized.substring(normalized.indexOf(':') + 1);
        return normalized;
    }

    private static void writeDefaults(Path file) throws IOException {
        JsonObject root = new JsonObject();
        var array = new com.google.gson.JsonArray();
        for (Rule rule : defaultRules()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", rule.id());
            object.addProperty("priority", rule.priority());
            addCounts(object, "blocks", rule.blocks());
            addCounts(object, "tags", rule.tags());
            addCounts(object, "capabilities", rule.capabilities());
            addCounts(object, "suffixes", rule.suffixes());
            if (!rule.addCapabilities().isEmpty()) {
                var additions = new com.google.gson.JsonArray();
                rule.addCapabilities().stream().sorted().forEach(additions::add);
                object.add("add_capabilities", additions);
            }
            array.add(object);
        }
        root.add("types", array);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) { GSON.toJson(root, writer); }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void addCounts(JsonObject object, String key, Map<String, Integer> counts) {
        if (counts.isEmpty()) return;
        JsonObject values = new JsonObject();
        counts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> values.addProperty(entry.getKey(), entry.getValue()));
        object.add(key, values);
    }

    private static List<Rule> defaultRules() {
        ArrayList<Rule> defaults = new ArrayList<>();
        defaults.add(rule("town_center", 100, Map.of("minecraft:bell", 1), Map.of(), Map.of(), Map.of(), Set.of("gather")));
        defaults.add(rule("prison", 95, Map.of("minecraft:iron_bars", 8, "minecraft:iron_door", 1), Map.of(), Map.of(), Map.of("_bed", 1), Set.of("restricted")));
        defaults.add(rule("library", 90, Map.of("minecraft:bookshelf", 16, "minecraft:lectern", 1), Map.of(), Map.of(), Map.of(), Set.of("read", "teach")));
        defaults.add(rule("inn", 85, Map.of("minecraft:jukebox", 1), Map.of(), Map.of("cook", 1), Map.of("_bed", 4), Set.of("residential")));
        defaults.add(rule("infirmary", 80, Map.of(), Map.of(), Map.of("brew", 1, "utility", 1), Map.of("_bed", 1), Set.of("heal")));
        defaults.add(rule("blacksmith", 75, Map.of("minecraft:blast_furnace", 1), Map.of(), Map.of("repair", 1), Map.of(), Set.of("smith", "repair")));
        defaults.add(rule("big_house", 60, Map.of(), Map.of(), Map.of(), Map.of("_bed", 4), Set.of("residential")));
        defaults.add(rule("house", 55, Map.of(), Map.of(), Map.of(), Map.of("_bed", 1), Set.of("residential")));
        defaults.add(rule("workshop", 45, Map.of(), Map.of(), Map.of("smith", 1), Map.of(), Set.of()));
        defaults.add(rule("kitchen", 40, Map.of(), Map.of(), Map.of("cook", 1), Map.of(), Set.of()));
        defaults.add(rule("storage", 35, Map.of(), Map.of(), Map.of("storage", 3), Map.of(), Set.of()));
        defaults.sort(Comparator.comparingInt(Rule::priority).reversed().thenComparing(Rule::id));
        return List.copyOf(defaults);
    }

    private static Rule rule(String id, int priority, Map<String, Integer> blocks, Map<String, Integer> tags,
                             Map<String, Integer> capabilities, Map<String, Integer> suffixes, Set<String> additions) {
        return new Rule(id, priority, blocks, tags, capabilities, suffixes, additions);
    }
}
