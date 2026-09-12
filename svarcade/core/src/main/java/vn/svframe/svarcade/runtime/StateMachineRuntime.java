package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Data-defined FSM with pure reducers; a failed transition publishes no partial state. */
public final class StateMachineRuntime implements SessionSystem {
    public interface Action {
        void validate(Node arguments);
        Map<String, Object> apply(Map<String, Object> state, Node arguments);
    }
    public interface Condition {
        void validate(Node arguments);
        boolean test(Map<String, Object> state, Node arguments);
    }
    private record Call(Id id, Node args) { }
    private record Transition(String target, String event, long afterTicks, Call condition) { }
    private record State(List<Call> enter, List<Call> exit, List<Transition> transitions, boolean terminal) { }
    public static final class Plan implements SystemSchema {
        private final Registry<Action> actions;
        private final Registry<Condition> conditions;
        public Plan(Registry<Action> actions, Registry<Condition> conditions) { this.actions = actions; this.conditions = conditions; }
        @Override public void validate(Node config) { compile(config); }
        private Compiled compile(Node config) {
            config.only("initial", "data", "states");
            String initial = config.string("initial");
            Map<String, Object> data = config.has("data") ? config.node("data").values() : Map.of();
            Node stateNodes = config.node("states");
            if (stateNodes.values().isEmpty() || stateNodes.values().size() > 256) throw config.error("states", "Expected 1..256 states");
            Map<String, State> states = new LinkedHashMap<>();
            for (String key : stateNodes.values().keySet()) {
                if (!key.matches("[A-Za-z0-9_-]{1,80}")) throw config.error("states", "Invalid state identifier");
                Node state = stateNodes.node(key); state.only("enter", "exit", "transitions", "terminal");
                List<Transition> transitions = new ArrayList<>();
                if (state.has("transitions")) for (Node t : state.nodes("transitions")) {
                    t.only("target", "event", "after_ticks", "condition");
                    if (t.has("event") == t.has("after_ticks")) throw t.error("event", "Exactly one event or after_ticks is required");
                    Call condition = t.has("condition") ? call(t.node("condition")) : null;
                    if (condition != null) conditions.require(condition.id()).validate(condition.args());
                    transitions.add(new Transition(t.string("target"), t.string("event", ""),
                            t.has("after_ticks") ? t.integer("after_ticks", 1, Long.MAX_VALUE) : -1, condition));
                }
                boolean terminal = state.bool("terminal", false);
                if (terminal && !transitions.isEmpty()) throw state.error("transitions", "Terminal state cannot transition");
                states.put(key, new State(calls(state, "enter"), calls(state, "exit"), List.copyOf(transitions), terminal));
            }
            if (!states.containsKey(initial)) throw config.error("initial", "Unknown state");
            for (State state : states.values()) for (Transition t : state.transitions()) if (!states.containsKey(t.target())) throw config.error("states", "Unknown transition target: " + t.target());
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(initial);
            while (!queue.isEmpty()) { String s = queue.remove(); if (visited.add(s)) states.get(s).transitions().forEach(t -> queue.add(t.target())); }
            if (visited.size() != states.size()) throw config.error("states", "Unreachable states");
            return new Compiled(initial, data, Map.copyOf(states));
        }
        private List<Call> calls(Node parent, String key) {
            if (!parent.has(key)) return List.of();
            List<Call> result = new ArrayList<>();
            for (Node node : parent.nodes(key)) {
                Call call = call(node); actions.require(call.id()).validate(call.args()); result.add(call);
            }
            return List.copyOf(result);
        }
        private Call call(Node node) {
            node.only("id", "args");
            return new Call(Id.of(node.string("id")), node.has("args") ? node.node("args") : new Node(Map.of(), "args"));
        }
        public StateMachineRuntime create(Node config, ThreadGuard thread, Runnable dirty) {
            return new StateMachineRuntime(compile(config), actions, conditions, thread, dirty);
        }
    }
    private record Compiled(String initial, Map<String, Object> data, Map<String, State> states) { }
    private final Compiled plan;
    private final Registry<Action> actions;
    private final Registry<Condition> conditions;
    private final ThreadGuard thread;
    private final Runnable dirty;
    private String state;
    private Map<String, Object> data;
    private long elapsed;
    private long lastTick = -1;
    private boolean active;
    private StateMachineRuntime(Compiled plan, Registry<Action> actions, Registry<Condition> conditions, ThreadGuard thread, Runnable dirty) {
        this.plan = plan; this.actions = actions; this.conditions = conditions; this.thread = thread; this.dirty = dirty;
    }
    @Override public void start() {
        thread.check(); if (active) throw new IllegalStateException("FSM already active");
        Map<String, Object> candidate = run(plan.states().get(plan.initial()).enter(), plan.data());
        state = plan.initial(); data = candidate; elapsed = 0; lastTick = -1; active = true; dirty.run();
    }
    private Map<String, Object> run(List<Call> calls, Map<String, Object> values) {
        Map<String, Object> candidate = values;
        for (Call call : calls) candidate = Values.map(actions.require(call.id()).apply(candidate, call.args()));
        return candidate;
    }
    public boolean event(String event) { thread.check(); requireActive(); return transition(Objects.requireNonNull(event)); }
    @Override public void tick(long tick) {
        thread.check(); requireActive();
        if (tick < 0 || (lastTick >= 0 && tick <= lastTick)) throw new IllegalArgumentException("Non-increasing FSM tick");
        if (lastTick >= 0) elapsed = Math.addExact(elapsed, tick - lastTick);
        lastTick = tick;
        transition(null);
    }
    private boolean transition(String event) {
        for (Transition t : plan.states().get(state).transitions()) {
            boolean matches = event == null ? t.afterTicks() >= 0 && elapsed >= t.afterTicks() : t.afterTicks() < 0 && t.event().equals(event);
            if (!matches || t.condition() != null && !conditions.require(t.condition().id()).test(data, t.condition().args())) continue;
            Map<String, Object> next = run(plan.states().get(state).exit(), data);
            next = run(plan.states().get(t.target()).enter(), next);
            state = t.target(); data = next; elapsed = 0; dirty.run(); return true;
        }
        return false;
    }
    private void requireActive() { if (!active) throw new IllegalStateException("FSM inactive"); }
    public String state() { thread.check(); requireActive(); return state; }
    public Map<String, Object> data() { thread.check(); requireActive(); return data; }
    public boolean terminal() { thread.check(); requireActive(); return plan.states().get(state).terminal(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { thread.check(); requireActive(); return Map.of("state", state, "elapsed", elapsed, "data", data); }
    @Override public void restore(int schema, Map<String, Object> snapshot) {
        thread.check(); if (active || schema != 1) throw new IllegalArgumentException("Invalid FSM restore");
        Node node = new Node(snapshot, "fsm-state"); node.only("state", "elapsed", "data");
        String restored = node.string("state");
        if (!plan.states().containsKey(restored)) throw node.error("state", "Unknown persisted state");
        long restoredElapsed = node.integer("elapsed", 0, Long.MAX_VALUE);
        Map<String, Object> restoredData = node.node("data").values();
        state = restored; elapsed = restoredElapsed; data = restoredData; lastTick = -1; active = true;
        // Enter actions deliberately do not replay during restore.
    }
    @Override public void close() { thread.check(); active = false; }
}
