package vn.svframe.mmocorefabric.runtime.gameplay;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Server-authoritative profession progression state.
 *
 * This is the M1.0/M1.7 runtime materialized as normal source so Fabric event
 * adapters can address the single live runtime without reflection or a second
 * progression store.
 */
public final class ProfessionRuntime {
    private static volatile ProfessionRuntime active;

    public record Definition(String id, int maxLevel, String curve, Set<String> sources) {
        public Definition {
            id = norm(id);
            sources = sources.stream().map(ProfessionRuntime::norm).collect(Collectors.toUnmodifiableSet());
        }
    }

    public record State(int level, double exp) {
        public State {
            if (level < 1 || exp < 0 || !Double.isFinite(exp)) throw new IllegalArgumentException();
        }
    }

    private final Map<String, Definition> defs = new LinkedHashMap<>();
    private final Map<UUID, Map<String, State>> states = new HashMap<>();

    public ProfessionRuntime() {
        active = this;
    }

    public static ProfessionRuntime active() {
        ProfessionRuntime value = active;
        if (value == null) throw new IllegalStateException("MMOCore profession runtime is not initialized");
        return value;
    }

    public synchronized void register(Definition definition) {
        if (defs.putIfAbsent(definition.id(), definition) != null) throw new IllegalStateException("duplicate profession");
    }

    public synchronized void clearDefinitions() {
        defs.clear();
    }

    public synchronized State state(UUID player, String id) {
        Definition definition = require(id);
        return states.computeIfAbsent(player, ignored -> new HashMap<>()).getOrDefault(definition.id(), new State(1, 0));
    }

    public synchronized int grant(UUID player, String id, String source, double amount, IntToDoubleFunction required) {
        Definition definition = require(id);
        if (!definition.sources().isEmpty() && !definition.sources().contains(norm(source))) return 0;
        if (amount < 0 || !Double.isFinite(amount)) throw new IllegalArgumentException();
        State old = state(player, id);
        int level = old.level();
        double exp = old.exp() + amount;
        int gained = 0;
        while (level < definition.maxLevel()) {
            double need = required.applyAsDouble(level);
            if (need <= 0 || !Double.isFinite(need) || exp < need) break;
            exp -= need;
            level++;
            gained++;
        }
        states.get(player).put(definition.id(), new State(level, exp));
        return gained;
    }

    public synchronized Map<String, State> snapshot(UUID player) {
        return Map.copyOf(states.getOrDefault(player, Map.of()));
    }

    public synchronized void restore(UUID player, Map<String, State> snapshot) {
        Map<String, State> copy = new HashMap<>();
        snapshot.forEach((key, value) -> copy.put(norm(key), value));
        if (copy.isEmpty()) states.remove(player);
        else states.put(player, copy);
    }

    public synchronized void forget(UUID player) {
        states.remove(player);
    }

    public synchronized Map<String, Definition> definitions() {
        return Map.copyOf(defs);
    }

    private Definition require(String id) {
        Definition definition = defs.get(norm(id));
        if (definition == null) throw new IllegalArgumentException("unknown profession " + id);
        return definition;
    }

    private static String norm(String value) {
        return Objects.requireNonNull(value).trim().toLowerCase(Locale.ROOT);
    }
}
