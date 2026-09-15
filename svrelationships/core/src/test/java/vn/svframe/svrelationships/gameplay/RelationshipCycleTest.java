package vn.svframe.svrelationships.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RelationshipCycleTest {
    @Test
    void cyclesAreAnchoredToRelationshipStart() {
        long start = 1_000L;
        var beforeBoundary = RelationshipCycle.resolve(start, 1_999L, 1_000L);
        var onBoundary = RelationshipCycle.resolve(start, 2_000L, 1_000L);
        assertEquals(0L, beforeBoundary.index());
        assertEquals(1L, onBoundary.index());
        assertEquals("anniversary:wedding:1", onBoundary.claimId("wedding"));
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(IllegalArgumentException.class, () -> RelationshipCycle.resolve(1L, 2L, 0L));
    }
}
