package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.gameplay.CeremonyDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CeremonyDefinitionService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public CeremonyDefinitionService(Path root) { this.root = Objects.requireNonNull(root, "root"); }

    public void initialize() {
        try {
            Path target = root.resolve("ceremonies.yml");
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream input = CeremonyDefinitionService.class.getResourceAsStream("/defaults/ceremonies.yml")) {
                    if (input == null) throw new IOException("Missing bundled resource: /defaults/ceremonies.yml");
                    Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install ceremony definitions", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load ceremony definitions: " + result.detail());
    }

    public Snapshot snapshot() { return Objects.requireNonNull(current.get(), "ceremonies not initialized"); }

    public ReloadResult reload() {
        try {
            Map<String, Object> rootMap;
            try (InputStream input = Files.newInputStream(root.resolve("ceremonies.yml"))) {
                rootMap = mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input), "ceremonies.yml");
            }
            Map<String, CeremonyDefinition> definitions = new LinkedHashMap<>();
            for (var entry : map(rootMap, "ceremonies").entrySet()) {
                Map<String, Object> value = mapValue(entry.getValue(), "ceremony " + entry.getKey());
                definitions.put(entry.getKey(), new CeremonyDefinition(
                        entry.getKey(), string(value, "required_route"), string(value, "required_state"),
                        string(value, "partnership_milestone"), bool(value, "require_household", false),
                        bool(value, "repeatable", false), GameplayDefinitionService.durationMillis(optionalString(value, "cooldown", "0s")),
                        longMap(value, "progression"), string(value, "message_key")
                ));
            }
            current.set(new Snapshot(generation.incrementAndGet(), definitions));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return mapValue(source.get(key), key); }
    private static Map<String, Object> mapValue(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>(); raw.forEach((key, item) -> result.put(String.valueOf(key), item)); return result;
    }
    private static String string(Map<String, Object> source, String key) { Object value=source.get(key); if(value==null) throw new IllegalArgumentException("Missing key: "+key); return String.valueOf(value); }
    private static String optionalString(Map<String, Object> source, String key, String fallback) { Object value=source.get(key); return value==null?fallback:String.valueOf(value); }
    private static boolean bool(Map<String, Object> source, String key, boolean fallback) { Object value=source.get(key); return value==null?fallback:value instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(value)); }
    private static Map<String, Long> longMap(Map<String, Object> source, String key) {
        Object value=source.get(key); if(value==null) return Map.of(); Map<String,Object> raw=mapValue(value,key); Map<String,Long> result=new LinkedHashMap<>(); raw.forEach((k,v)->result.put(k,v instanceof Number n?n.longValue():Long.parseLong(String.valueOf(v)))); return result;
    }

    public record Snapshot(long generation, Map<String, CeremonyDefinition> definitions) { public Snapshot { definitions=Map.copyOf(definitions); } }
    public record ReloadResult(boolean success, String detail) {}
}
