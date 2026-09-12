package vn.svframe.svarcade.runtime;

import java.util.*;
import vn.svframe.svarcade.config.*;

/** Reusable value reducers and predicates. No game or lifecycle names. */
public final class ValuePrimitives {
    private ValuePrimitives() { }
    public static Registry<StateMachineRuntime.Action> actions() {
        return new Registry.Builder<StateMachineRuntime.Action>()
                .add(Id.of("svarcade:set"), new StateMachineRuntime.Action() {
                    public void validate(Node n) { n.only("key", "value"); n.string("key"); n.require("value"); }
                    public Map<String, Object> apply(Map<String, Object> state, Node n) {
                        Map<String, Object> result = new LinkedHashMap<>(state); result.put(n.string("key"), n.require("value")); return result;
                    }
                }).add(Id.of("svarcade:add"), new StateMachineRuntime.Action() {
                    public void validate(Node n) { n.only("key", "amount"); n.string("key"); n.integer("amount", Long.MIN_VALUE, Long.MAX_VALUE); }
                    public Map<String, Object> apply(Map<String, Object> state, Node n) {
                        Object old = state.getOrDefault(n.string("key"), 0L);
                        if (!(old instanceof Long || old instanceof Integer)) throw new IllegalArgumentException("Add requires integer state");
                        Map<String, Object> result = new LinkedHashMap<>(state);
                        result.put(n.string("key"), Math.addExact(((Number) old).longValue(), n.integer("amount", Long.MIN_VALUE, Long.MAX_VALUE)));
                        return result;
                    }
                }).build();
    }
    public static Registry<StateMachineRuntime.Condition> conditions() {
        return new Registry.Builder<StateMachineRuntime.Condition>()
                .add(Id.of("svarcade:equals"), new StateMachineRuntime.Condition() {
                    public void validate(Node n) { n.only("key", "value"); n.string("key"); n.require("value"); }
                    public boolean test(Map<String, Object> state, Node n) {
                        Object actual = state.get(n.string("key")), expected = n.require("value");
                        if (actual instanceof Number && expected instanceof Number) {
                            return new java.math.BigDecimal(actual.toString()).compareTo(new java.math.BigDecimal(expected.toString())) == 0;
                        }
                        return Objects.equals(actual, expected);
                    }
                }).build();
    }
}
