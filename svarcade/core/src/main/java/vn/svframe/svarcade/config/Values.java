package vn.svframe.svarcade.config;

import java.util.*;

/** Copies data into a bounded tree of immutable scalar/list/map values. */
public final class Values {
    private Values() { }
    public static Object freeze(Object value) {
        return freeze(value, new IdentityHashMap<>(), 0, new int[]{0});
    }
    private static Object freeze(Object value, IdentityHashMap<Object, Boolean> active,
                                 int depth, int[] count) {
        if (depth > 48 || ++count[0] > 100_000) throw new ConfigException("Data tree exceeds limits");
        if (value instanceof String text && text.length() > 1_048_576) throw new ConfigException("Data string exceeds limit");
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long) return value;
        if (value instanceof Double d && Double.isFinite(d)) return d;
        if (value == null) throw new ConfigException("Null data value");
        if (active.put(value, Boolean.TRUE) != null) throw new ConfigException("Cyclic data value");
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new ConfigException("Map keys must be strings");
                    out.put(key, freeze(entry.getValue(), active, depth + 1, count));
                }
                return Collections.unmodifiableMap(out);
            }
            if (value instanceof List<?> list) {
                List<Object> out = new ArrayList<>();
                for (Object item : list) out.add(freeze(item, active, depth + 1, count));
                return List.copyOf(out);
            }
            throw new ConfigException("Unsupported data value: " + value.getClass().getName());
        } finally {
            active.remove(value);
        }
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        Object frozen = freeze(value);
        if (!(frozen instanceof Map<?, ?>)) throw new ConfigException("Expected mapping");
        return (Map<String, Object>) frozen;
    }
}
