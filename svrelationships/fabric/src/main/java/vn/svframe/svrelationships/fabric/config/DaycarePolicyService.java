package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class DaycarePolicyService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public DaycarePolicyService(Path root) { this.root = Objects.requireNonNull(root, "root"); }

    public void initialize() {
        try {
            Path target = root.resolve("daycare-policies.yml");
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream input = DaycarePolicyService.class.getResourceAsStream("/defaults/daycare-policies.yml")) {
                    if (input == null) throw new IOException("Missing bundled resource: /defaults/daycare-policies.yml");
                    Files.copy(input, target);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install daycare policies", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load daycare policies: " + result.detail());
    }

    public Snapshot snapshot() { return Objects.requireNonNull(current.get(), "daycare policies not initialized"); }

    public ReloadResult reload() {
        try {
            Map<String, Object> rootMap;
            try (InputStream input = Files.newInputStream(root.resolve("daycare-policies.yml"))) {
                rootMap = mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input), "daycare-policies.yml");
            }
            Policy defaultPolicy = parse(map(rootMap, "default"));
            Map<String, Policy> profiles = new LinkedHashMap<>();
            Object raw = rootMap.get("profiles");
            if (raw != null) {
                for (var entry : mapValue(raw, "profiles").entrySet()) {
                    profiles.put(entry.getKey(), parse(mapValue(entry.getValue(), "profile " + entry.getKey())));
                }
            }
            current.set(new Snapshot(generation.incrementAndGet(), defaultPolicy, profiles));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Policy parse(Map<String, Object> value) {
        String source = string(value, "offspring_species_source");
        if (!source.startsWith("participant:") && !source.startsWith("fixed:")) {
            throw new IllegalArgumentException("offspring_species_source must use participant:<role> or fixed:<species>");
        }
        int maximum = integer(value, "max_active_sessions");
        if (maximum < 1) throw new IllegalArgumentException("max_active_sessions must be >= 1");
        long retry = GameplayDefinitionService.durationMillis(string(value, "retry_delay"));
        if (retry < 1_000L) throw new IllegalArgumentException("retry_delay must be at least 1s");
        String offline = string(value, "offline_completion_policy").toLowerCase(java.util.Locale.ROOT);
        if (!offline.equals("pause_until_online")) throw new IllegalArgumentException("Unsupported offline_completion_policy: " + offline);
        Set<String> requiredPartnerRoles = stringSet(value, "required_partner_roles");
        return new Policy(source, maximum, retry, offline, requiredPartnerRoles);
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return mapValue(source.get(key), key); }
    private static Map<String, Object> mapValue(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>(); raw.forEach((key, item) -> result.put(String.valueOf(key), item)); return result;
    }
    private static String string(Map<String, Object> source, String key) { Object value = source.get(key); if (value == null) throw new IllegalArgumentException("Missing key: " + key); return String.valueOf(value); }
    private static int integer(Map<String, Object> source, String key) { Object value = source.get(key); return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key)); }
    private static Set<String> stringSet(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return Set.of();
        if (!(value instanceof Iterable<?> iterable)) throw new IllegalArgumentException("Expected list at " + key);
        Set<String> result = new LinkedHashSet<>();
        iterable.forEach(item -> result.add(String.valueOf(item)));
        return Set.copyOf(result);
    }

    public record Snapshot(long generation, Policy defaultPolicy, Map<String, Policy> profiles) {
        public Snapshot { profiles = Map.copyOf(profiles); }
        public Policy policy(String daycareId) { return profiles.getOrDefault(daycareId, defaultPolicy); }
    }
    public record Policy(String offspringSpeciesSource, int maxActiveSessions, long retryDelayMillis,
                         String offlineCompletionPolicy, Set<String> requiredPartnerRoles) {
        public Policy { requiredPartnerRoles = Set.copyOf(requiredPartnerRoles); }
    }
    public record ReloadResult(boolean success, String detail) {}
}
