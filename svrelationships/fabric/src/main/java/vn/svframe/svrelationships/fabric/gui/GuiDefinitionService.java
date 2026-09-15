package vn.svframe.svrelationships.fabric.gui;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class GuiDefinitionService {
    private final Path directory;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(0, Map.of()));

    public GuiDefinitionService(Path root) {
        this.directory = Objects.requireNonNull(root, "root").resolve("gui");
    }

    public ReloadResult reload() {
        try {
            if (!Files.isDirectory(directory)) throw new IllegalArgumentException("GUI directory does not exist: " + directory);
            Map<String, GuiDefinition> definitions = new LinkedHashMap<>();
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                    GuiDefinition definition = parse(load(path));
                    if (definitions.putIfAbsent(definition.id(), definition) != null) {
                        throw new IllegalArgumentException("Duplicate GUI id: " + definition.id());
                    }
                }
            }
            current.set(new Snapshot(generation.incrementAndGet(), definitions));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public Snapshot snapshot() { return current.get(); }

    private GuiDefinition parse(Map<String, Object> root) {
        String id = string(root, "id");
        String screen = string(root, "screen");
        String titleKey = string(root, "title_key");
        Map<String, ComponentDefinition> components = new LinkedHashMap<>();
        for (var entry : map(root, "components").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "component " + entry.getKey());
            int slot = integer(value, "slot");
            if (slot < 0 || slot >= 54) throw new IllegalArgumentException("Invalid slot " + slot + " in GUI " + id);
            components.put(entry.getKey(), new ComponentDefinition(
                    entry.getKey(), slot, string(value, "item"), string(value, "name_key"),
                    stringList(value, "lore_keys"), string(value, "action"), stringMap(value, "args")
            ));
        }
        return new GuiDefinition(id, screen, titleKey, components);
    }

    private Map<String, Object> load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input), path.getFileName().toString());
        }
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return mapValue(source.get(key), key); }
    private static Map<String, Object> mapValue(Object value, String label) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>(); raw.forEach((k,v) -> result.put(String.valueOf(k), v)); return result;
    }
    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) throw new IllegalArgumentException("Missing GUI key: " + key); return String.valueOf(value);
    }
    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key); return value instanceof Number n ? n.intValue() : Integer.parseInt(string(source, key));
    }
    private static List<String> stringList(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected list at " + key);
        return list.stream().map(String::valueOf).toList();
    }
    private static Map<String, String> stringMap(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) return Map.of();
        Map<String, Object> raw = mapValue(value, key); Map<String, String> result = new LinkedHashMap<>(); raw.forEach((k,v) -> result.put(k, String.valueOf(v))); return result;
    }

    public record Snapshot(long generation, Map<String, GuiDefinition> definitions) {
        public Snapshot { definitions = Map.copyOf(definitions); }
    }
    public record GuiDefinition(String id, String screen, String titleKey, Map<String, ComponentDefinition> components) {
        public GuiDefinition { components = Map.copyOf(components); }
    }
    public record ComponentDefinition(String id, int slot, String itemId, String nameKey, List<String> loreKeys,
                                      String action, Map<String, String> args) {
        public ComponentDefinition { loreKeys = List.copyOf(loreKeys); args = Map.copyOf(args); }
    }
    public record ReloadResult(boolean success, String detail) {}
}
