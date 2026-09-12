package vn.svframe.svarcade.config;

import java.util.*;

/** Frozen capability registry. Builders are bootstrap-only and reject shadowing. */
public final class Registry<T> {
    private final Map<Id, T> entries;
    public Registry(Map<Id, T> entries) { this.entries = Map.copyOf(entries); }
    public T require(Id id) {
        T value = entries.get(id);
        if (value == null) throw new ConfigException("Unknown capability: " + id);
        return value;
    }
    public boolean contains(Id id) { return entries.containsKey(id); }
    public Set<Id> ids() { return entries.keySet(); }
    public static final class Builder<T> {
        private final Map<Id, T> entries = new LinkedHashMap<>();
        public Builder<T> add(Id id, T value) {
            Objects.requireNonNull(id); Objects.requireNonNull(value);
            if (entries.putIfAbsent(id, value) != null) throw new ConfigException("Duplicate capability: " + id);
            return this;
        }
        public Registry<T> build() { return new Registry<>(entries); }
    }
}
