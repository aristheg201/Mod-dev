package vn.svframe.mmocorefabric.runtime.gameplay;

import java.util.*;

public final class QuestRuntime {
    public enum Status { LOCKED, AVAILABLE, ACTIVE, COMPLETE, COOLDOWN }

    public record Objective(String id, int required, String type, List<String> triggers) {
        public Objective {
            id = norm(id);
            if (required < 1) throw new IllegalArgumentException("required < 1");
            type = Objects.requireNonNullElse(type, "");
            triggers = List.copyOf(triggers);
        }
        public Objective(String id, int required) { this(id, required, "", List.of()); }
    }

    public record Definition(String id, List<String> parents, int minLevel, String profession,
                             int professionLevel, long cooldownMillis, List<Objective> objectives) {
        public Definition {
            id = norm(id);
            parents = parents.stream().map(QuestRuntime::norm).toList();
            profession = profession == null ? "" : norm(profession);
            objectives = List.copyOf(objectives);
            if (minLevel < 0 || professionLevel < 0 || cooldownMillis < 0) throw new IllegalArgumentException();
        }
        public Definition(String id, String parent, int minLevel, String profession, int professionLevel,
                          long cooldownMillis, List<Objective> objectives) {
            this(id, parent == null || parent.isBlank() ? List.of() : List.of(parent), minLevel,
                    profession, professionLevel, cooldownMillis, objectives);
        }
    }

    public record Snapshot(Status status, Map<String, Integer> progress, long cooldownUntil) {
        public Snapshot {
            status = Objects.requireNonNull(status);
            progress = Map.copyOf(progress);
            if (cooldownUntil < 0) throw new IllegalArgumentException("cooldownUntil < 0");
        }
    }

    public static final class State {
        private Status status = Status.AVAILABLE;
        private final Map<String, Integer> progress = new HashMap<>();
        private long cooldownUntil;

        public Status status(long now) {
            if (status == Status.COOLDOWN && now >= cooldownUntil) status = Status.AVAILABLE;
            return status;
        }
        public Map<String, Integer> progress() { return Map.copyOf(progress); }
        public long cooldownUntil() { return cooldownUntil; }
        private Snapshot snapshot() { return new Snapshot(status, progress, cooldownUntil); }
        private void restore(Snapshot snapshot) {
            status = snapshot.status();
            progress.clear();
            progress.putAll(snapshot.progress());
            cooldownUntil = snapshot.cooldownUntil();
        }
    }

    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Map<String, State>> states = new HashMap<>();

    public synchronized void register(Definition definition) { definitions.put(definition.id(), definition); }
    public synchronized void clearDefinitions() { definitions.clear(); }
    public synchronized Map<String, Definition> definitions() { return Map.copyOf(definitions); }
    public synchronized State state(UUID player, String id) {
        require(id);
        return states.computeIfAbsent(player, ignored -> new HashMap<>()).computeIfAbsent(norm(id), ignored -> new State());
    }

    public synchronized boolean start(UUID player, String id, int level,
                                      java.util.function.ToIntFunction<String> professionLevel, long now) {
        Definition definition = require(id);
        State state = state(player, id);
        if (state.status(now) != Status.AVAILABLE) return false;
        if (level < definition.minLevel()) return false;
        for (String parent : definition.parents()) if (state(player, parent).status(now) != Status.COMPLETE) return false;
        if (!definition.profession().isEmpty() && professionLevel.applyAsInt(definition.profession()) < definition.professionLevel()) return false;
        state.status = Status.ACTIVE;
        state.progress.clear();
        return true;
    }

    public synchronized boolean progress(UUID player, String id, String objective, int amount, long now) {
        Definition definition = require(id);
        State state = state(player, id);
        if (state.status(now) != Status.ACTIVE || amount <= 0) return false;
        Objective wanted = definition.objectives().stream().filter(o -> o.id().equalsIgnoreCase(objective)).findFirst().orElse(null);
        if (wanted == null) return false;
        state.progress.merge(wanted.id(), amount, (a, b) -> Math.min(wanted.required(), a + b));
        boolean done = definition.objectives().stream().allMatch(o -> state.progress.getOrDefault(o.id(), 0) >= o.required());
        if (done) state.status = Status.COMPLETE;
        return done;
    }

    public synchronized void finish(UUID player, String id, long now) {
        Definition definition = require(id);
        State state = state(player, id);
        if (state.status(now) != Status.COMPLETE) throw new IllegalStateException("quest is not complete");
        if (definition.cooldownMillis() > 0) {
            state.status = Status.COOLDOWN;
            state.cooldownUntil = Math.addExact(now, definition.cooldownMillis());
        }
    }

    public synchronized void cancel(UUID player, String id) {
        State state = state(player, id);
        state.status = Status.AVAILABLE;
        state.progress.clear();
        state.cooldownUntil = 0;
    }

    public synchronized Map<String, Snapshot> snapshot(UUID player) {
        Map<String, Snapshot> out = new HashMap<>();
        states.getOrDefault(player, Map.of()).forEach((key, value) -> out.put(key, value.snapshot()));
        return Map.copyOf(out);
    }

    public synchronized void restore(UUID player, Map<String, Snapshot> snapshot) {
        if (snapshot.isEmpty()) { states.remove(player); return; }
        Map<String, State> out = new HashMap<>();
        snapshot.forEach((key, value) -> {
            State state = new State();
            state.restore(value);
            out.put(norm(key), state);
        });
        states.put(player, out);
    }

    public synchronized void forget(UUID player) { states.remove(player); }

    private Definition require(String id) {
        Definition definition = definitions.get(norm(id));
        if (definition == null) throw new IllegalArgumentException("unknown quest " + id);
        return definition;
    }
    private static String norm(String value) { return Objects.requireNonNull(value).trim().toLowerCase(Locale.ROOT); }
}
