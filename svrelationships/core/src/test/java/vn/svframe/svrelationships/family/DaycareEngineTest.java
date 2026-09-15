package vn.svframe.svrelationships.family;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DaycareEngineTest {
    @Test
    void createsDeadlineWithoutTickCountdown() {
        DaycareDefinition definition = new DaycareDefinition(
                "family", "partner_family", List.of("partner"), 86_400_000L,
                "default", "", "", "", 0
        );
        DaycareEngine engine = new DaycareEngine(Map.of("family", definition));
        UUID owner = UUID.randomUUID();
        UUID pokemon = UUID.randomUUID();
        DaycareSession session = engine.start(owner, "family", List.of(pokemon), 1000L);

        assertEquals(86_401_000L, session.completeAtMillis());
        assertFalse(session.due(86_400_999L));
        assertTrue(session.due(86_401_000L));
        assertThrows(IllegalArgumentException.class, () -> engine.start(owner, "family", List.of(pokemon, UUID.randomUUID()), 0));
    }
}
