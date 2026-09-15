package vn.svframe.svrelationships.gameplay;

import org.junit.jupiter.api.Test;
import vn.svframe.svrelationships.relationship.RelationshipKey;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RouteEngineTest {
    @Test
    void enforcesConfiguredTransitions() {
        RouteDefinition route = new RouteDefinition("romance", "friends", Map.of(
                "friends", new RouteDefinition.State("friends", "friends", List.of("dating")),
                "dating", new RouteDefinition.State("dating", "dating", List.of("married")),
                "married", new RouteDefinition.State("married", "married", List.of())
        ));
        RouteEngine engine = new RouteEngine(Map.of("romance", route));
        RelationshipState state = new RelationshipState(new RelationshipKey(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals("friends", engine.currentState(state, "romance"));
        assertTrue(engine.canTransition(state, "romance", "dating"));
        assertEquals("dating", engine.transition(state, "romance", "dating"));
        assertFalse(engine.canTransition(state, "romance", "friends"));
        assertThrows(IllegalStateException.class, () -> engine.transition(state, "romance", "friends"));
    }
}
