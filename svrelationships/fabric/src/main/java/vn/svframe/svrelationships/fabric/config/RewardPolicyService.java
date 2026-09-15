package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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

public final class RewardPolicyService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public RewardPolicyService(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public void initialize() {
        try {
            Path target = root.resolve("reward-policies.yml");
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream input = RewardPolicyService.class.getResourceAsStream("/defaults/reward-policies.yml")) {
                    if (input == null) throw new IOException("Missing bundled resource: /defaults/reward-policies.yml");
                    Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install reward policies", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load reward policies: " + result.detail());
    }

    public Snapshot snapshot() {
        return Objects.requireNonNull(current.get(), "reward policies not initialized");
    }

    public ReloadResult reload() {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> rootMap;
            try (InputStream input = Files.newInputStream(root.resolve("reward-policies.yml"))) {
                rootMap = asMap(yaml.load(input), "reward-policies.yml");
            }
            Policy defaultPolicy = parsePolicy(asMap(rootMap.get("default"), "default"));
            Map<String, Policy> profiles = new LinkedHashMap<>();
            Object rawProfiles = rootMap.get("profiles");
            if (rawProfiles != null) {
                for (var entry : asMap(rawProfiles, "profiles").entrySet()) {
                    profiles.put(entry.getKey(), parsePolicy(asMap(entry.getValue(), "profile " + entry.getKey())));
                }
            }
            current.set(new Snapshot(generation.incrementAndGet(), defaultPolicy, profiles));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Policy parsePolicy(Map<String, Object> value) {
        String offlinePolicy = string(value, "offline_policy").toLowerCase(java.util.Locale.ROOT);
        int maxPendingPeriods = integer(value, "max_pending_periods");
        String overflowPolicy = string(value, "overflow_policy").toLowerCase(java.util.Locale.ROOT);
        if (!offlinePolicy.equals("current_only") && !offlinePolicy.equals("accumulate")) {
            throw new IllegalArgumentException("Unsupported offline_policy: " + offlinePolicy);
        }
        if (maxPendingPeriods < 1) throw new IllegalArgumentException("max_pending_periods must be >= 1");
        if (!overflowPolicy.equals("drop") && !overflowPolicy.equals("deny")) {
            throw new IllegalArgumentException("Unsupported overflow_policy: " + overflowPolicy);
        }
        return new Policy(offlinePolicy, maxPendingPeriods, overflowPolicy);
    }

    private static Map<String, Object> asMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) throw new IllegalArgumentException("Missing key: " + key);
        return String.valueOf(value);
    }

    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key));
    }

    public record Snapshot(long generation, Policy defaultPolicy, Map<String, Policy> profiles) {
        public Snapshot {
            Objects.requireNonNull(defaultPolicy, "defaultPolicy");
            profiles = Map.copyOf(profiles);
        }

        public Policy policy(String profileId) {
            return profiles.getOrDefault(profileId, defaultPolicy);
        }
    }

    public record Policy(String offlinePolicy, int maxPendingPeriods, String overflowPolicy) {}
    public record ReloadResult(boolean success, String detail) {}
}
