package vn.svframe.svarcade.config;

import java.util.*;
import java.util.function.Function;

/** Resolves explicit {$ref: 'package-file.yml#/pointer'} before schema validation. */
public final class ReferenceResolver {
    private final Function<String, Object> source;
    private final Map<String, Object> files = new HashMap<>();
    private final Set<String> active = new LinkedHashSet<>();
    private int expanded;
    public ReferenceResolver(Function<String, Object> source) { this.source = Objects.requireNonNull(source); }

    public Object resolve(String file) {
        expanded = 0; active.clear(); files.clear();
        return Values.freeze(reference(file, "", 0));
    }
    private Object reference(String file, String pointer, int depth) {
        String identity = file + "#" + pointer;
        if (!active.add(identity)) throw new ConfigException("Cyclic reference: " + active + " -> " + identity);
        try {
            Object document = files.computeIfAbsent(file, key -> Objects.requireNonNull(source.apply(key)));
            return expand(select(document, pointer, identity), file, depth + 1);
        } finally { active.remove(identity); }
    }
    private Object expand(Object value, String file, int depth) {
        if (depth > 48 || ++expanded > 100_000) throw new ConfigException("Reference expansion exceeds limits");
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("$ref")) {
                if (map.size() != 1 || !(map.get("$ref") instanceof String ref) || ref.isBlank()) throw new ConfigException("A $ref must be a nonblank string and the only field");
                int hash = ref.indexOf('#');
                String target = hash < 0 ? ref : ref.substring(0, hash);
                String pointer = hash < 0 ? "" : ref.substring(hash + 1);
                if (target.isEmpty()) target = file;
                return reference(target, pointer, depth + 1);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new ConfigException("Reference map key must be string");
                result.put(key, expand(entry.getValue(), file, depth + 1));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(expand(item, file, depth + 1));
            return List.copyOf(result);
        }
        return value;
    }
    private static Object select(Object document, String pointer, String identity) {
        if (pointer.isEmpty()) return document;
        if (!pointer.startsWith("/")) throw new ConfigException("Reference fragment must be a JSON pointer: " + identity);
        Object selected = document;
        for (String raw : pointer.substring(1).split("/", -1)) {
            if (raw.matches(".*~(?![01]).*")) throw new ConfigException("Invalid pointer escape: " + identity);
            String key = raw.replace("~1", "/").replace("~0", "~");
            if (selected instanceof Map<?, ?> map && map.containsKey(key)) selected = map.get(key);
            else if (selected instanceof List<?> list && key.matches("0|[1-9][0-9]{0,8}")) {
                int index = Integer.parseInt(key);
                if (index >= list.size()) throw new ConfigException("Reference index missing: " + identity);
                selected = list.get(index);
            } else throw new ConfigException("Reference target missing: " + identity);
        }
        return selected;
    }
}
