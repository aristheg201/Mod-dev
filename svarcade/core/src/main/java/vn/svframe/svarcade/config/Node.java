package vn.svframe.svarcade.config;

import java.util.*;

/** Typed reads carry the source/key path into every validation error. */
public final class Node {
    private final Map<String, Object> values;
    private final String path;
    public Node(Object values, String path) { this.values = Values.map(values); this.path = path; }
    public Map<String, Object> values() { return values; }
    public boolean has(String key) { return values.containsKey(key); }
    public Object require(String key) {
        if (!values.containsKey(key)) throw error(key, "Required value missing");
        return values.get(key);
    }
    public String string(String key) {
        Object v = require(key);
        if (!(v instanceof String s) || s.isBlank()) throw error(key, "Expected nonblank string");
        return s;
    }
    public String string(String key, String fallback) { return has(key) ? string(key) : fallback; }
    public boolean bool(String key, boolean fallback) {
        if (!has(key)) return fallback;
        if (!(require(key) instanceof Boolean b)) throw error(key, "Expected boolean");
        return b;
    }
    public long integer(String key, long min, long max) {
        Object v = require(key);
        if (!(v instanceof Integer || v instanceof Long)) throw error(key, "Expected integer");
        long n = ((Number) v).longValue();
        if (n < min || n > max) throw error(key, "Integer outside " + min + ".." + max);
        return n;
    }
    public Node node(String key) { return new Node(require(key), path + "." + key); }
    public List<?> list(String key) {
        if (!(require(key) instanceof List<?> list)) throw error(key, "Expected list");
        return list;
    }
    public List<Node> nodes(String key) {
        List<Node> result = new ArrayList<>();
        List<?> list = list(key);
        for (int i = 0; i < list.size(); i++) result.add(new Node(list.get(i), path + "." + key + "[" + i + "]"));
        return List.copyOf(result);
    }
    public Set<String> strings(String key) {
        Set<String> out = new LinkedHashSet<>();
        for (Object item : list(key)) {
            if (!(item instanceof String s) || s.isBlank() || !out.add(s)) throw error(key, "Expected unique nonblank strings");
        }
        return Collections.unmodifiableSet(out);
    }
    public void only(String... allowed) {
        Set<String> accepted = Set.of(allowed);
        for (String key : values.keySet()) if (!accepted.contains(key)) throw error(key, "Unknown field");
    }
    public ConfigException error(String key, String message) { return new ConfigException(path + "." + key + ": " + message); }
}
