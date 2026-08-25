package vn.svframe.mythiclibfabric;

import vn.svframe.compat.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Exact legacy MythicLib stats.yml scalar configuration surface. */
public final class MythicLibStatSettings {
    public record Entry(double baseValue, Double minValue, Double maxValue, String decimalPattern) {
        public DecimalFormat decimalFormat() {
            return new DecimalFormat(decimalPattern == null || decimalPattern.isBlank() ? "0.#" : decimalPattern);
        }
    }

    private final Map<String, Entry> entries;

    private MythicLibStatSettings(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static MythicLibStatSettings empty() {
        return new MythicLibStatSettings(Map.of());
    }

    public static MythicLibStatSettings load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return empty();
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> decimals = section(root.get("decimal-format"));
        Map<String, Object> limits = section(root.get("min-max-values"));
        Map<String, Object> bases = section(root.get("base-stat-value"));

        Set<String> stats = new LinkedHashSet<>();
        addKeys(stats, decimals);
        addKeys(stats, limits);
        addKeys(stats, bases);

        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String stat : stats) {
            String normalized = normalize(stat);
            String pattern = string(decimals.get(stat), string(decimals.get(normalized), "0.#"));
            double base = decimal(bases.get(stat), decimal(bases.get(normalized), 0.0d));
            Bounds bounds = parseBounds(limits.containsKey(stat) ? limits.get(stat) : limits.get(normalized));
            entries.put(normalized, new Entry(base, bounds.min(), bounds.max(), pattern));
        }
        return new MythicLibStatSettings(entries);
    }

    public Entry entry(String stat) {
        Entry entry = entries.get(normalize(stat));
        return entry == null ? new Entry(0.0d, null, null, "0.#") : entry;
    }

    public Set<String> configuredStats() {
        return entries.keySet();
    }

    public int size() {
        return entries.size();
    }

    private static Bounds parseBounds(Object value) {
        if (value == null) return new Bounds(null, null);
        String raw = String.valueOf(value).trim();
        String[] split = raw.split("=", -1);
        if (split.length != 2) throw new IllegalArgumentException("Invalid min-max-values entry: " + raw);
        String left = split[0].trim();
        String right = split[1].trim();
        Double min = left.isEmpty() ? null : Double.parseDouble(left);
        Double max = right.isEmpty() ? null : Double.parseDouble(right);
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Minimum stat value exceeds maximum in: " + raw);
        }
        return new Bounds(min, max);
    }

    private static void addKeys(Set<String> target, Map<String, Object> section) {
        for (String key : section.keySet()) target.add(normalize(key));
    }

    private static Map<String, Object> section(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) out.put(normalize(String.valueOf(entry.getKey())), entry.getValue());
        }
        return out;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return fallback;
        return Double.parseDouble(text);
    }

    private static String normalize(String stat) {
        return stat == null ? "" : stat.trim().toUpperCase(Locale.ROOT);
    }

    private record Bounds(Double min, Double max) {}
}
