package vn.svframe.svrelationships.gameplay;

import org.junit.jupiter.api.Test;
import vn.svframe.svrelationships.relationship.RelationshipKey;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionEngineTest {
    private final ProgressionTrackDefinition bond = new ProgressionTrackDefinition(
            "bond", 0, 1000,
            List.of(
                    new ProgressionTrackDefinition.Rank("friend", 200, "rank.friend"),
                    new ProgressionTrackDefinition.Rank("close", 500, "rank.close")
            )
    );

    @Test
    void clampsAndResolvesRanks() {
        ProgressionEngine engine = new ProgressionEngine(Map.of("bond", bond));
        RelationshipState state = new RelationshipState(new RelationshipKey(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(1000, engine.set(state, "bond", 1500));
        assertEquals(0, engine.set(state, "bond", -10));
        assertEquals(500, engine.set(state, "bond", 500));
        assertEquals("close", engine.rank("bond", 500).orElseThrow().id());
        assertTrue(engine.atLeastRank("bond", 500, "friend"));
        assertFalse(engine.atLeastRank("bond", 199, "friend"));
    }
}
