package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigService {
    private final Path root;
    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();

    public ConfigService(Path root) { this.root = Objects.requireNonNull(root, "root"); }

    public void initialize() {
        try {
            installDefault("/defaults/main.yml", root.resolve("main.yml"));
            installDefault("/defaults/households.yml", root.resolve("households.yml"));
            installDefault("/defaults/lang/en_us.yml", root.resolve("lang/en_us.yml"));
            installDefault("/defaults/lang/vi_vn.yml", root.resolve("lang/vi_vn.yml"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install SVRelationships defaults", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load SVRelationships configuration: " + result.detail());
    }

    public ConfigSnapshot snapshot() { return Objects.requireNonNull(snapshot.get(), "configuration not initialized"); }

    public ReloadResult reload() {
        try {
            Map<String, Object> main = load(root.resolve("main.yml"));
            Map<String, Object> households = load(root.resolve("households.yml"));
            String locale = string(main, "locale");
            Map<String, Object> commands = map(main, "commands");
            Map<String, Object> householdRoot = map(main, "household");
            Map<String, Object> economy = map(main, "economy");
            String adminPermission = string(commands, "admin_permission");
            String defaultProfile = string(householdRoot, "default_profile");
            List<String> economyPriority = stringList(economy, "priority");
            Map<String, ConfigSnapshot.HouseholdProfile> profiles = parseHouseholds(households);
            if (!profiles.containsKey(defaultProfile)) throw new IllegalArgumentException("Unknown default household profile: " + defaultProfile);
            Map<String, String> messages = loadMessages(root.resolve("lang").resolve(locale + ".yml"));
            snapshot.set(new ConfigSnapshot(generation.incrementAndGet(), locale, adminPermission, defaultProfile, profiles, economyPriority, messages));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Map<String, ConfigSnapshot.HouseholdProfile> parseHouseholds(Map<String, Object> rootMap) {
        Map<String, ConfigSnapshot.HouseholdProfile> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(rootMap, "profiles").entrySet()) {
            Map<String, Object> values = mapValue(entry.getValue(), "household profile " + entry.getKey());
            int active = integer(values, "active_radius");
            int deactivate = integer(values, "deactivation_radius");
            int maxMaterialized = integer(values, "max_materialized_partners");
            String boundary = string(values, "boundary");
            int interval = optionalInteger(values, "evaluation_interval_ticks", 20);
            if (active < 0 || deactivate < active || maxMaterialized < 0 || interval < 1) {
                throw new IllegalArgumentException("Invalid household spatial limits: " + entry.getKey());
            }
            result.put(entry.getKey(), new ConfigSnapshot.HouseholdProfile(active, deactivate, maxMaterialized, boundary, interval));
        }
        return result;
    }

    private Map<String, String> loadMessages(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        load(path).forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }

    private Map<String, Object> load(Path path) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(path)) { return mapValue(yaml.load(input), path.getFileName().toString()); }
    }

    private void installDefault(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        try (InputStream input = ConfigService.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return mapValue(source.get(key), key); }
    private static Map<String, Object> mapValue(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key); if (value == null) throw new IllegalArgumentException("Missing key: " + key); return String.valueOf(value);
    }
    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key); return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key));
    }
    private static int optionalInteger(Map<String, Object> source, String key, int fallback) {
        Object value = source.get(key); return value == null ? fallback : value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
    private static List<String> stringList(Map<String, Object> source, String key) {
        Object value = source.get(key); if (!(value instanceof List<?> raw)) throw new IllegalArgumentException("Expected list at " + key);
        List<String> result = new ArrayList<>(raw.size()); raw.forEach(item -> result.add(String.valueOf(item))); return result;
    }
    public record ReloadResult(boolean success, String detail) {}
}
