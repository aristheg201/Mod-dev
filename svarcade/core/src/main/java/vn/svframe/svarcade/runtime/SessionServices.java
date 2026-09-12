package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Typed session-local contracts. Publication is limited to the owning factory. */
public final class SessionServices {
    public record Key<T>(Id id, Class<T> contract) {
        public Key {
            Objects.requireNonNull(id); Objects.requireNonNull(contract);
            if (!contract.isInterface()) throw new IllegalArgumentException("Capability must expose an interface");
        }
    }
    private record Binding(Key<?> key, Id owner, Object value) { }
    private final ThreadGuard thread;
    private final Map<Id, Binding> bindings = new LinkedHashMap<>();
    private Id publishing;
    private Set<Key<?>> exports = Set.of(), imports = Set.of();
    private boolean closed;

    public SessionServices(ThreadGuard thread) { this.thread = Objects.requireNonNull(thread); }

    /** Definitions already have a topological system order; contracts must agree with it. */
    void validatePlan(List<Definition.SystemSpec> specs, Registry<SystemFactory> factories) {
        thread.check();
        Map<Id, Key<?>> available = new LinkedHashMap<>();
        Set<Id> systemIds = new HashSet<>();
        for (Definition.SystemSpec spec : specs) {
            if (!systemIds.add(spec.id())) throw new ConfigException("Duplicate runtime system: " + spec.id());
            SystemFactory factory = factories.require(spec.id());
            for (Key<?> key : factory.requires()) {
                if (!key.equals(available.get(key.id()))) throw new ConfigException("Missing or misordered capability " + key.id() + " for " + spec.id());
            }
            for (Key<?> key : factory.provides()) {
                if (available.putIfAbsent(key.id(), key) != null) throw new ConfigException("Duplicate capability provider: " + key.id());
            }
        }
    }
    void begin(Id owner, SystemFactory factory) {
        thread.check();
        if (closed || publishing != null) throw new IllegalStateException("Capability publication unavailable");
        publishing = owner; exports = Set.copyOf(factory.provides()); imports = Set.copyOf(factory.requires());
    }
    void finish() {
        thread.check();
        for (Key<?> key : exports) {
            Binding binding = bindings.get(key.id());
            if (binding == null || !binding.owner().equals(publishing)) throw new ConfigException("Factory did not publish " + key.id());
        }
        publishing = null; exports = Set.of(); imports = Set.of();
    }
    public <T> void provide(Key<T> key, T value) {
        thread.check(); Objects.requireNonNull(value);
        if (closed || publishing == null || !exports.contains(key)) throw new IllegalStateException("Undeclared capability publication");
        if (!key.contract().isInstance(value)) throw new IllegalArgumentException("Capability implementation type");
        if (bindings.putIfAbsent(key.id(), new Binding(key, publishing, value)) != null) throw new IllegalStateException("Capability already published: " + key.id());
    }
    public <T> T require(Key<T> key) {
        thread.check();
        if (closed) throw new IllegalStateException("Session services closed");
        if (publishing != null && !imports.contains(key) && !exports.contains(key)) throw new IllegalStateException("Undeclared capability dependency");
        Binding binding = bindings.get(key.id());
        if (binding == null || !key.equals(binding.key())) throw new IllegalStateException("Capability unavailable: " + key.id());
        return key.contract().cast(binding.value());
    }
    void stopPublication() { thread.check(); publishing = null; exports = Set.of(); imports = Set.of(); }
    void clear() { thread.check(); stopPublication(); bindings.clear(); closed = true; }
    public int size() { thread.check(); return bindings.size(); }
}
