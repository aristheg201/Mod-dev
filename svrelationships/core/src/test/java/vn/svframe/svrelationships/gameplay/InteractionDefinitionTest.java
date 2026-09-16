package vn.svframe.svrelationships.gameplay;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class InteractionDefinitionTest {
    @Test
    void legacyStateExpressionBecomesValidatedRuntimeStateSet() {
        var definition = new InteractionDefinition(
                "date",
                1_000L,
                Map.of("bond", 10L, "romance", 20L),
                "romance",
                "dating|engaged|married",
                "interaction.date.success"
        );

        assertEquals("dating", definition.requiredState());
        assertEquals(java.util.List.of("dating", "engaged", "married"), definition.requiredStates());
        assertTrue(definition.allowsState("dating"));
        assertTrue(definition.allowsState("engaged"));
        assertTrue(definition.allowsState("married"));
        assertFalse(definition.allowsState("friends"));
    }

    @Test
    void blankRequirementAllowsAnyState() {
        var definition = new InteractionDefinition("talk", 0L, Map.of("bond", 10L), "", "", "interaction.daily_talk.success");
        assertTrue(definition.requiredStates().isEmpty());
        assertTrue(definition.allowsState("friends"));
        assertTrue(definition.allowsState("married"));
    }
}
