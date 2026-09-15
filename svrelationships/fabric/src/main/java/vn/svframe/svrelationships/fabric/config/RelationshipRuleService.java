package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.gameplay.PartnershipDefinition;
import vn.svframe.svrelationships.gameplay.PokemonSelectorDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RelationshipRuleService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public RelationshipRuleService(Path root) { this.root = root; }

    public void initialize() {
        try {
            install("/defaults/selectors.yml", root.resolve("selectors.yml"));
            install("/defaults/partnership.yml", root.resolve("partnership.yml"));
        } catch (IOException e) { throw new IllegalStateException("Unable to install relationship rule config", e); }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load relationship rules: " + result.detail());
    }

    public Snapshot snapshot() { return current.get(); }

    public ReloadResult reload() {
        try {
            Map<String, PokemonSelectorDefinition> selectors = parseSelectors(load(root.resolve("selectors.yml")));
            PartnershipDefinition partnership = parsePartnership(load(root.resolve("partnership.yml")));
            if (!partnership.routeId().isBlank() && partnership.milestones().isEmpty()) throw new IllegalArgumentException("Partnership has no milestones");
            for (PartnershipDefinition.Milestone milestone : partnership.milestones().values()) {
                if (!selectors.containsKey(milestone.selectorId())) throw new IllegalArgumentException("Unknown partnership selector: " + milestone.selectorId());
            }
            current.set(new Snapshot(generation.incrementAndGet(), selectors, partnership));
            return new ReloadResult(true, "");
        } catch (Exception e) { return new ReloadResult(false, e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private Map<String, PokemonSelectorDefinition> parseSelectors(Map<String, Object> rootMap) {
        Map<String, PokemonSelectorDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "selectors").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "selector " + entry.getKey());
            result.put(entry.getKey(), new PokemonSelectorDefinition(
                    entry.getKey(), set(value, "species"), set(value, "forms"), set(value, "required_aspects"), set(value, "genders"),
                    integer(value, "minimum_level", 0), integer(value, "maximum_level", Integer.MAX_VALUE)
            ));
        }
        return result;
    }

    private PartnershipDefinition parsePartnership(Map<String, Object> rootMap) {
        Map<String, Object> value = map(rootMap, "partnership");
        Map<String, PartnershipDefinition.Milestone> milestones = new LinkedHashMap<>();
        for (var entry : map(value, "milestones").entrySet()) {
            Map<String, Object> milestone = mapValue(entry.getValue(), "milestone " + entry.getKey());
            milestones.put(entry.getKey(), new PartnershipDefinition.Milestone(
                    entry.getKey(), string(milestone, "target_state"), string(milestone, "selector"), stringMap(milestone, "required_ranks"),
                    bool(milestone, "require_household", false), bool(milestone, "creates_partnership", false), string(milestone, "message_key")
            ));
        }
        return new PartnershipDefinition(string(value, "route"), milestones);
    }

    private Map<String, Object> load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input), path.getFileName().toString());
        }
    }

    private void install(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        try (InputStream input = RelationshipRuleService.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing resource " + resource);
            Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return mapValue(source.get(key), key); }
    private static Map<String, Object> mapValue(Object value, String label) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>(); raw.forEach((k,v) -> result.put(String.valueOf(k), v)); return result;
    }
    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) throw new IllegalArgumentException("Missing key " + key); return String.valueOf(value);
    }
    private static int integer(Map<String, Object> source, String key, int fallback) {
        Object value = source.get(key); if (value == null) return fallback; return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }
    private static boolean bool(Map<String, Object> source, String key, boolean fallback) {
        Object value = source.get(key); if (value == null) return fallback; return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
    private static Set<String> set(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) return Set.of();
        if (!(value instanceof Iterable<?> iterable)) throw new IllegalArgumentException("Expected list at " + key);
        Set<String> result = new LinkedHashSet<>(); iterable.forEach(item -> result.add(String.valueOf(item))); return result;
    }
    private static Map<String, String> stringMap(Map<String, Object> source, String key) {
        Map<String, Object> raw = map(source, key); Map<String, String> result = new LinkedHashMap<>(); raw.forEach((k,v) -> result.put(k, String.valueOf(v))); return result;
    }

    public record Snapshot(long generation, Map<String, PokemonSelectorDefinition> selectors, PartnershipDefinition partnership) {
        public Snapshot { selectors = Map.copyOf(selectors); }
    }
    public record ReloadResult(boolean success, String detail) {}
}
