package vn.svframe.svrelationships.household;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseholdMaterializationPlannerTest {
    @Test
    void preservesAlreadyMaterializedBeforeNearestVirtualCandidates() {
        UUID existing = UUID.randomUUID();
        UUID nearest = UUID.randomUUID();
        UUID farther = UUID.randomUUID();
        HouseholdMaterializationPlanner planner = new HouseholdMaterializationPlanner();
        List<UUID> selected = planner.select(List.of(
                new HouseholdMaterializationPlanner.Candidate(existing, 100, true),
                new HouseholdMaterializationPlanner.Candidate(nearest, 1, false),
                new HouseholdMaterializationPlanner.Candidate(farther, 25, false)
        ), 2);
        assertEquals(existing, selected.getFirst());
        assertEquals(nearest, selected.get(1));
    }
}
