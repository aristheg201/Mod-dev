package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class FsmTest {
    Node config() {
        return new Node(Map.of("initial", "READY", "data", Map.of("score", 0L), "states", Map.of(
                "READY", Map.of("enter", List.of(Map.of("id", "svarcade:add", "args", Map.of("key", "score", "amount", 1))),
                        "transitions", List.of(Map.of("target", "ACTIVE", "event", "begin"))),
                "ACTIVE", Map.of("transitions", List.of(Map.of("target", "DONE", "after_ticks", 10))),
                "DONE", Map.of("terminal", true, "enter", List.of(Map.of("id", "svarcade:add", "args", Map.of("key", "score", "amount", 5)))))), "fsm");
    }
    StateMachineRuntime create(Node n) { return new StateMachineRuntime.Plan(ValuePrimitives.actions(), ValuePrimitives.conditions()).create(n, new ThreadGuard(), () -> { }); }
    @Test void arbitraryLifecycleRunsFromDefinition() {
        var fsm = create(config()); fsm.start(); assertEquals("READY", fsm.state()); assertEquals(1L, fsm.data().get("score"));
        assertFalse(fsm.event("unknown")); assertTrue(fsm.event("begin")); fsm.tick(100); fsm.tick(109); assertEquals("ACTIVE", fsm.state());
        fsm.tick(110); assertEquals("DONE", fsm.state()); assertTrue(fsm.terminal()); assertEquals(6L, fsm.data().get("score"));
    }
    @Test void recoveryPreservesRemainingDurationWithoutReplayingEnterActions() {
        var first = create(config()); first.start(); first.event("begin"); first.tick(50); first.tick(54); var saved = first.snapshot();
        var second = create(config()); second.restore(1, saved); second.tick(1); second.tick(6); assertEquals("ACTIVE", second.state());
        second.tick(7); assertEquals("DONE", second.state()); assertEquals(6L, second.data().get("score"));
    }
    @Test void invalidTargetAndUnreachableStateAreRejected() {
        assertThrows(ConfigException.class, () -> create(new Node(Map.of("initial", "A", "states", Map.of("A", Map.of("transitions", List.of(Map.of("event", "go", "target", "MISSING"))))), "bad")));
        assertThrows(ConfigException.class, () -> create(new Node(Map.of("initial", "A", "states", Map.of("A", Map.of(), "B", Map.of())), "bad")));
    }
    @Test void invalidActionAndMalformedTransitionAreRejectedAtLoadTime() {
        assertThrows(ConfigException.class, () -> create(new Node(Map.of("initial", "A", "states", Map.of("A", Map.of("enter", List.of(Map.of("id", "test:unknown"))))), "bad")));
        assertThrows(ConfigException.class, () -> create(new Node(Map.of("initial", "A", "states", Map.of("A", Map.of("transitions", List.of(Map.of("event", "go", "after_ticks", 1, "target", "A"))))), "bad")));
    }
    @Test void failedTransitionDoesNotPublishExitOrEnterPartialState() {
        Registry<StateMachineRuntime.Action> actions = ValuePrimitives.actions();
        Node n = new Node(Map.of("initial", "A", "data", Map.of("x", Long.MAX_VALUE), "states", Map.of(
                "A", Map.of("exit", List.of(Map.of("id", "svarcade:set", "args", Map.of("key", "other", "value", true))), "transitions", List.of(Map.of("target", "B", "event", "go"))),
                "B", Map.of("enter", List.of(Map.of("id", "svarcade:add", "args", Map.of("key", "x", "amount", 1)))))), "atomic");
        AtomicInteger dirty = new AtomicInteger(); var fsm = new StateMachineRuntime.Plan(actions, ValuePrimitives.conditions()).create(n, new ThreadGuard(), dirty::incrementAndGet);
        fsm.start(); assertThrows(ArithmeticException.class, () -> fsm.event("go")); assertEquals("A", fsm.state()); assertFalse(fsm.data().containsKey("other")); assertEquals(1, dirty.get());
    }
    @Test void schemaAndTimeErrorsAreRejected() {
        var fsm = create(config()); assertThrows(IllegalArgumentException.class, () -> fsm.restore(2, Map.of()));
        fsm.start(); fsm.tick(2); assertThrows(IllegalArgumentException.class, () -> fsm.tick(1));
    }
    @Test void yamlIntegerConditionMatchesRuntimeLongArithmetic() {
        var condition = ValuePrimitives.conditions().require(Id.of("svarcade:equals"));
        Node args = new Node(Map.of("key", "score", "value", 1), "condition");
        condition.validate(args);
        assertTrue(condition.test(Map.of("score", 1L), args));
        assertFalse(condition.test(Map.of("score", 2L), args));
    }
}
