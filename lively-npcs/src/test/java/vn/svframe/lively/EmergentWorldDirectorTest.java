package vn.svframe.lively;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.LivingWorldDirectorService;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.simulation.CausalSimulationService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class EmergentWorldDirectorTest {
    @AfterEach
    void reset() { LivelyApi.resetServerSessionState(); }

    @Test
    void underworldCanCreateCrimeWithoutPreexistingCrime() {
        LivelyApi.resetServerSessionState();
        for (int i = 0; i < 4; i++) {
            ActorId owner = new ActorId(new UUID(17L, i + 1L), ActorId.Kind.NPC);
            LivelyApi.actors().upsert(owner, "Dealer" + i,
                    Map.of("ambition", .45D, "morality", .40D, "influence", .25D),
                    Map.of("world", "minecraft:overworld"), Set.of("npc"));
            LivelyApi.economy().ensureWallet(owner, 10_000L);
            LivelyApi.economy().createBusiness(owner, "Hidden" + i, null,
                    Map.of("kind", "black_market", "illegal", "true", "hidden", "true", "risk", "0.65"));
        }

        assertTrue(LivelyApi.crime().snapshot().crimes().isEmpty());
        try (LivingWorldDirectorService director = new LivingWorldDirectorService();
             CausalSimulationService causal = new CausalSimulationService()) {
            director.tick(1200L);
            assertTrue(LivelyApi.events().activeEvents().stream()
                    .anyMatch(event -> event.category() == WorldEventEngine.Category.CRIME));
            assertFalse(LivelyApi.crime().snapshot().crimes().isEmpty());
        }
    }
}
