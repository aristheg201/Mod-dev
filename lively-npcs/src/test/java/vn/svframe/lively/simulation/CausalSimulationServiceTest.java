package vn.svframe.lively.simulation;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.WorldEventEngine;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CausalSimulationServiceTest {
    @Test
    void crimeEventMaterializesCrimeEvidenceAndInvestigation() {
        ActorId suspect = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId victim = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId witness = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        LivelyApi.actors().upsert(suspect, "Suspect", Map.of("ambition", 1D, "morality", 0D, "fear", 0D), Map.of(), Set.of("npc"));
        LivelyApi.actors().upsert(victim, "Victim", Map.of("ambition", 0D, "morality", 1D, "fear", 1D), Map.of(), Set.of("npc"));
        LivelyApi.actors().upsert(witness, "Witness", Map.of("ambition", 0.2D, "morality", 0.8D), Map.of(), Set.of("npc"));

        int before = LivelyApi.crime().snapshot().crimes().size();
        try (CausalSimulationService service = new CausalSimulationService()) {
            var event = LivelyApi.events().start(new WorldEventEngine.EventProposal(
                    WorldEventEngine.Category.CRIME, "murder_case", null,
                    Set.of(suspect, victim, witness), 0.95D, Duration.ofHours(1), Map.of("crime_type", "MURDER")));
            assertTrue(event.isPresent());
            assertEquals(before + 1, LivelyApi.crime().snapshot().crimes().size());
            var crime = LivelyApi.crime().snapshot().crimes().values().stream()
                    .filter(c -> event.get().id().toString().equals(c.facts().get("event"))).findFirst().orElseThrow();
            assertEquals(suspect, crime.perpetrator());
            assertEquals(victim, crime.victim());
            assertTrue(LivelyApi.crime().evidence(crime.id()).size() >= 3);
        }
    }

    @Test
    void economicEventChangesSemanticStockWithoutWorldMutation() {
        ActorId owner = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        var business = LivelyApi.economy().createBusiness(owner, "Market", null, Map.of());
        var stock = LivelyApi.economy().setStock(business.id(), "minecraft:bread", 100, 100, 10, 0.5D, 0.8D);
        try (CausalSimulationService service = new CausalSimulationService()) {
            assertTrue(LivelyApi.events().start(new WorldEventEngine.EventProposal(
                    WorldEventEngine.Category.ECONOMIC, "supply_crisis", null, Set.of(owner), 0.8D,
                    Duration.ofHours(2), Map.of())).isPresent());
            var changed = LivelyApi.economy().snapshot().stocks().get(stock.key());
            assertTrue(changed.quantity() < stock.quantity());
            assertTrue(changed.demand() > stock.demand());
            assertTrue(changed.supply() < stock.supply());
        }
    }
}
