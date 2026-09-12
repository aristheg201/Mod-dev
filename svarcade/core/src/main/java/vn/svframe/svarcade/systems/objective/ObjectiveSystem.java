package vn.svframe.svarcade.systems.objective;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Sparse atomic counter changes and a single terminal result, both explicitly recoverable. */
public final class ObjectiveSystem implements SessionSystem, ObjectiveAccess {
    public static final Id ID = Id.of("svarcade:objectives");
    public static final SessionServices.Key<ObjectiveAccess> ACCESS = new SessionServices.Key<>(ID, ObjectiveAccess.class);
    public record Counter(long initial, long minimum, long maximum, boolean clamp) {
        public Counter { if (minimum > initial || initial > maximum) throw new ConfigException("Invalid objective counter limits"); }
    }
    public record Config(Map<Id, Counter> counters, Set<Id> reasons) {
        public Config {
            counters = Map.copyOf(counters); reasons = Set.copyOf(reasons);
            if (counters.size() > 256 || reasons.isEmpty() || reasons.size() > 256) throw new ConfigException("Objective definition limits");
        }
        public static Config parse(Node n) {
            n.only("counters", "reasons"); Map<Id, Counter> counters = new LinkedHashMap<>();
            Node values = n.node("counters");
            for (String key : values.values().keySet()) {
                Node c = values.node(key); c.only("initial", "minimum", "maximum", "clamp");
                counters.put(Id.of(key), new Counter(c.integer("initial", Long.MIN_VALUE, Long.MAX_VALUE),
                        c.integer("minimum", Long.MIN_VALUE, Long.MAX_VALUE), c.integer("maximum", Long.MIN_VALUE, Long.MAX_VALUE), c.bool("clamp", false)));
            }
            Set<Id> reasons = new LinkedHashSet<>(); n.strings("reasons").forEach(reason -> reasons.add(Id.of(reason)));
            return new Config(counters, reasons);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Set<String> teams = new LinkedHashSet<>();
            session.participants().values().stream().filter(p -> p.kind() != Participant.Kind.SPECTATOR).forEach(p -> teams.add(p.team()));
            ObjectiveSystem system = new ObjectiveSystem(Config.parse(config), teams, session.thread());
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final Set<String> teams;
    private final ThreadGuard thread;
    private final NavigableMap<Id, Long> counters = new TreeMap<>();
    private Result result;
    private long revision;
    private boolean active, closed;
    public ObjectiveSystem(Config config, Set<String> teams, ThreadGuard thread) {
        this.config = Objects.requireNonNull(config); this.teams = Set.copyOf(teams); this.thread = Objects.requireNonNull(thread);
        if (teams.size() > 1024) throw new IllegalArgumentException("Objective team limit");
        for (String team : teams) if (team.isBlank() || team.length() > 160) throw new IllegalArgumentException("Invalid objective team");
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Objective system inactive"); }
    private void validateResult(Result candidate) {
        if (!config.reasons().contains(candidate.reason()) || !teams.containsAll(candidate.winners())) throw new IllegalArgumentException("Unknown outcome reason or winning team");
    }
    @Override public void start() {
        thread.check(); if (active || closed) throw new IllegalStateException("Objective already started/closed");
        config.counters().forEach((id, counter) -> counters.put(id, counter.initial())); active = true;
    }
    @Override public Set<Id> reasons() { thread.check(); return config.reasons(); }
    @Override public long value(Id id) {
        requireActive(); Long value = counters.get(id); if (value == null) throw new IllegalArgumentException("Unknown objective counter: " + id); return value;
    }
    @Override public long revision() { requireActive(); return revision; }
    @Override public Optional<Result> result() { requireActive(); return Optional.ofNullable(result); }
    @Override public StateChange prepare(Map<Id, Long> deltas, Optional<Result> finish) {
        requireActive(); Objects.requireNonNull(finish);
        if (result != null || revision == Long.MAX_VALUE) throw new IllegalStateException("Objective completed or revision exhausted");
        if (deltas.size() > config.counters().size() || deltas.isEmpty() && finish.isEmpty()) throw new IllegalArgumentException("Invalid objective transaction");
        finish.ifPresent(this::validateResult); Map<Id, Long> before = new TreeMap<>(), after = new TreeMap<>();
        for (var delta : deltas.entrySet()) {
            Counter c = config.counters().get(delta.getKey()); if (c == null) throw new IllegalArgumentException("Unknown objective counter");
            long old = counters.get(delta.getKey()), change = Objects.requireNonNull(delta.getValue());
            long next;
            try { next = Math.addExact(old, change); }
            catch (ArithmeticException overflow) {
                if (!c.clamp()) throw overflow;
                next = change < 0 ? c.minimum() : c.maximum();
            }
            if (c.clamp()) next = Math.max(c.minimum(), Math.min(c.maximum(), next));
            else if (next < c.minimum() || next > c.maximum()) throw new IllegalArgumentException("Objective counter outside limits");
            before.put(delta.getKey(), old); after.put(delta.getKey(), next);
        }
        long expected = revision; Result outcome = finish.orElse(null);
        return new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || result != null) throw new IllegalStateException("Stale or reused objective transaction");
                after.forEach(counters::put); result = outcome; revision = expected + 1; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || result != outcome) throw new IllegalStateException("Objective rollback conflict");
                before.forEach(counters::put); result = null; revision = expected; state = 2;
            }
        };
    }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); Map<String, Object> values = new TreeMap<>(); counters.forEach((id, value) -> values.put(id.toString(), value));
        Map<String, Object> state = new LinkedHashMap<>(Map.of("revision", revision, "counters", values));
        if (result != null) state.put("result", Map.of("reason", result.reason().toString(), "winners", result.winners().stream().sorted().toList(), "draw", result.draw()));
        return Values.map(state);
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid objective restore");
        Node n = new Node(saved, "objective-state"); n.only("revision", "counters", "result"); long version = n.integer("revision", 0, Long.MAX_VALUE - 1);
        Node values = n.node("counters"); Set<String> expected = new HashSet<>(); config.counters().keySet().forEach(id -> expected.add(id.toString()));
        if (!values.values().keySet().equals(expected)) throw new ConfigException("Persisted objective counter set mismatch");
        Map<Id, Long> restored = new TreeMap<>(); config.counters().forEach((id, c) -> restored.put(id, values.integer(id.toString(), c.minimum(), c.maximum())));
        Result outcome = null;
        if (n.has("result")) {
            Node r = n.node("result"); r.only("reason", "winners", "draw");
            outcome = new Result(Id.of(r.string("reason")), r.strings("winners"), r.bool("draw", false)); validateResult(outcome);
            if (version == 0) throw new ConfigException("Outcome without a committed transaction");
        }
        counters.clear(); counters.putAll(restored); result = outcome; revision = version; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; counters.clear(); result = null; }
}
