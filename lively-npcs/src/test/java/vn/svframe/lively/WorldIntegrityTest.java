package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.event.StoryDirector;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.simulation.SimulationLodController;
import vn.svframe.lively.world.SemanticStructureRegistry;
import vn.svframe.lively.world.WorldMutationPolicy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WorldIntegrityTest {
    @Test
    void aiCannotMutatePersistentMinecraftWorld() {
        WorldMutationPolicy policy = WorldMutationPolicy.secureDefaults();
        ActorId npc = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        for (WorldMutationPolicy.ActionKind kind : Set.of(
                WorldMutationPolicy.ActionKind.BLOCK_BREAK,
                WorldMutationPolicy.ActionKind.BLOCK_SET,
                WorldMutationPolicy.ActionKind.EXPLOSION,
                WorldMutationPolicy.ActionKind.FIRE,
                WorldMutationPolicy.ActionKind.FLUID,
                WorldMutationPolicy.ActionKind.CONTAINER_MUTATION,
                WorldMutationPolicy.ActionKind.NBT_MUTATION,
                WorldMutationPolicy.ActionKind.COMMAND)) {
            var result = policy.evaluate(new WorldMutationPolicy.Proposal(
                    npc, WorldMutationPolicy.Source.AI, WorldMutationPolicy.MutationClass.PERSISTENT,
                    kind, "market", null, Map.of()));
            assertFalse(result.allowed(), kind.name());
            assertEquals("ai_world_mutation_forbidden", result.reason());
        }
    }

    @Test
    void semanticDisasterCreatesHistoryWithoutDamagingStructure() {
        SemanticStructureRegistry structures = new SemanticStructureRegistry();
        structures.register(new SemanticStructureRegistry.Structure(
                "market", "market", new SemanticStructureRegistry.Bounds("minecraft:overworld", 0, 0, 0, 20, 100, 20),
                Set.of("trade", "gather"), Map.of("entrance", "10,64,0"), null, "town",
                SemanticStructureRegistry.OperationalState.OPEN, 0L));
        WorldEventEngine events = new WorldEventEngine(structures, WorldMutationPolicy.secureDefaults(), 8);
        var event = events.start(new WorldEventEngine.EventProposal(
                WorldEventEngine.Category.DISASTER, "market_fire", "market", Set.of(), 0.7D,
                Duration.ofMinutes(20), Map.of("presentation", "smoke_only")));
        assertTrue(event.isPresent());
        assertEquals(SemanticStructureRegistry.OperationalState.OPEN, structures.get("market").orElseThrow().state());
    }

    @Test
    void storyDirectorOnlyProducesBoundedEventProposals() {
        StoryDirector director = new StoryDirector();
        var proposals = director.propose(java.util.List.of(
                new StoryDirector.Tension("black_market", WorldEventEngine.Category.CRIME, null,
                        0.9D, 0.7D, Set.of(), Map.of("cause", "economic_crisis"))), 1);
        assertEquals(1, proposals.size());
        assertTrue(proposals.getFirst().duration().compareTo(Duration.ofHours(12)) <= 0);
    }

    @Test
    void distantActorsDegradeToDormantSimulation() {
        SimulationLodController lod = new SimulationLodController();
        assertEquals(SimulationLodController.Level.DORMANT, lod.classify(10_000D, 0.1D, false, false));
        assertEquals(SimulationLodController.Level.ACTIVE, lod.classify(10_000D, 0.1D, true, false));
    }
}
