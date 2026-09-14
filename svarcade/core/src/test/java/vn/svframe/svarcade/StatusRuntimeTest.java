package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.combat.CombatAccess.*;
import static org.junit.jupiter.api.Assertions.*;

class StatusRuntimeTest {
    static final Id BURN = Id.of("test:burn"), SLOW = Id.of("test:slow");
    StatusDefinition definition(StatusKind kind, Stacking stacking) { return new StatusDefinition(kind, 10, 2, kind == StatusKind.DOT ? 3 : 0.25, stacking, 3, Set.of()); }
    AppliedStatus attempt(Id id, StatusDefinition d, int stacks) { return new AppliedStatus(id, d.kind(), 10, d.tickInterval(), d.magnitude(), d.stacking(), stacks); }
    @Test void dotUsesExactDeadlinesAndExpiresWithoutBoundaryPulse() {
        var d = definition(StatusKind.DOT, Stacking.STACK); var runtime = new StatusRuntime(Map.of(BURN, d), 8);
        runtime.apply(attempt(BURN, d, 1)); assertEquals(0, runtime.advance(1)); assertEquals(3, runtime.advance(1));
        assertEquals(9, runtime.advance(8)); assertTrue(runtime.ids().isEmpty()); assertEquals(0, runtime.advance(1000));
    }
    @Test void restartRetainsRemainingDurationAndPulsePhase() {
        var d = definition(StatusKind.DOT, Stacking.STACK); var original = new StatusRuntime(Map.of(BURN, d), 8);
        original.apply(attempt(BURN, d, 2)); original.advance(3); var restored = new StatusRuntime(Map.of(BURN, d), 8);
        restored.restore(original.snapshot()); assertEquals(original.active(), restored.active());
        for (int i = 0; i < 8; i++) assertEquals(original.advance(1), restored.advance(1));
        assertEquals(original.snapshot(), restored.snapshot());
    }
    @Test void stackingCapsAndRefreshDoesNotPostponeExistingPulse() {
        var d = definition(StatusKind.DOT, Stacking.STACK); var runtime = new StatusRuntime(Map.of(BURN, d), 8);
        runtime.apply(attempt(BURN, d, 2)); runtime.advance(1); runtime.apply(attempt(BURN, d, 2));
        assertEquals(9, runtime.advance(1)); assertEquals(3, runtime.active().getFirst().stacks());
    }
    @Test void replaceAndIgnoreHaveDistinctTiming() {
        for (Stacking stacking : List.of(Stacking.REPLACE, Stacking.IGNORE, Stacking.REFRESH)) {
            var d = definition(StatusKind.DOT, stacking); var runtime = new StatusRuntime(Map.of(BURN, d), 8);
            runtime.apply(attempt(BURN, d, 1)); runtime.advance(1);
            assertEquals(stacking != Stacking.IGNORE, runtime.apply(attempt(BURN, d, 1)));
            assertEquals(stacking == Stacking.REPLACE ? 0 : 3, runtime.advance(1));
        }
    }
    @Test void controlAndBuffContributionsDisappearAtExpiry() {
        for (StatusKind kind : List.of(StatusKind.SLOW, StatusKind.STUN, StatusKind.BUFF, StatusKind.DEBUFF)) {
            var d = definition(kind, Stacking.STACK); var runtime = new StatusRuntime(Map.of(SLOW, d), 8);
            runtime.apply(attempt(SLOW, d, 2)); assertTrue(runtime.has(kind)); assertEquals(0.5, runtime.magnitude(SLOW));
            assertEquals(0, runtime.advance(10)); assertFalse(runtime.has(kind)); assertEquals(0, runtime.magnitude(SLOW));
        }
    }
    @Test void invalidSnapshotAndForgedResolutionCannotMutateState() {
        var d = definition(StatusKind.DOT, Stacking.STACK); var runtime = new StatusRuntime(Map.of(BURN, d), 1);
        runtime.apply(attempt(BURN, d, 1)); var before = runtime.snapshot();
        assertThrows(IllegalArgumentException.class, () -> runtime.apply(new AppliedStatus(BURN, StatusKind.DOT, 10, 2, 999, Stacking.STACK, 1)));
        assertThrows(ConfigException.class, () -> runtime.restore(Map.of("schema", 1, "active", List.of(Map.of("id", BURN.toString(), "stacks", 1, "remaining", 0, "next_pulse", 1)))));
        assertEquals(before, runtime.snapshot());
    }
    @Test void catchUpIsBoundedByStatusCountAndMatchesSmallAdvances() {
        var d = new StatusDefinition(StatusKind.DOT, 1_000_000, 1, 1, Stacking.STACK, 3, Set.of());
        var runtime = new StatusRuntime(Map.of(BURN, d), 1); runtime.apply(new AppliedStatus(BURN, d.kind(), 100_000_000, 1, 1, d.stacking(), 1));
        assertEquals(99_999_999, runtime.advance(100_000_000)); assertTrue(runtime.ids().isEmpty());
    }
}
