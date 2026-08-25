package vn.svframe.mythiclibfabric;

import vn.svframe.compat.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native registry for MythicLib custom trigger definitions. */
public final class MythicLibTriggerRegistry {
    public record Trigger(String id, boolean silent, Map<String, Object> config) { }

    private static final Map<String, Trigger> TRIGGERS = new ConcurrentHashMap<>();

    private MythicLibTriggerRegistry() { }

    public static synchronized int reload(Path file) throws IOException {
        LinkedHashMap<String, Trigger> next = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                String id = normalize(entry.getKey());
                next.put(id, new Trigger(id, bool(section.get("silent"), false), Map.copyOf(section)));
            }
        }
        TRIGGERS.clear();
        TRIGGERS.putAll(next);
        return TRIGGERS.size();
    }

    public static Trigger get(String id) { return TRIGGERS.get(normalize(id)); }
    public static List<Trigger> values() { return List.copyOf(TRIGGERS.values()); }
    public static boolean contains(String id) { return TRIGGERS.containsKey(normalize(id)); }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
