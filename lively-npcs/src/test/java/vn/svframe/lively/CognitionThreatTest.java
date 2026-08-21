package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.ai.LivelyAiEngine;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.WorldSnapshot;
import vn.svframe.lively.social.SocialEngine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class CognitionThreatTest {
    @Test
    void environmentalDangerCanTriggerFleeWithoutAHostileEntity() {
        UUID npcId = new UUID(30L, 1L);
        NpcSnapshot npc = snapshot(npcId, Map.of("brave", 0D));
        WorldSnapshot world = new WorldSnapshot(1L, "minecraft:overworld", 100L, List.of(),
                Map.of("environment_threat", .90D));

        var decision = new LivelyAiEngine().decide(npc, world).orElseThrow();
        assertEquals("respond_to_threat", decision.goal().type());
        assertEquals("flee", decision.action().type());
    }

    @Test
    void braveNpcCanPreferDefensiveStanceAgainstRevealedThreat() {
        UUID npcId = new UUID(30L, 2L);
        NpcSnapshot npc = snapshot(npcId, Map.of("brave", 1D, "loyal", .8D));
        WorldSnapshot world = new WorldSnapshot(2L, "minecraft:overworld", 100L,
                List.of(new WorldSnapshot.ObservedEntity(new UUID(31L, 1L), "player", .90D)), Map.of());

        var decision = new LivelyAiEngine().decide(npc, world).orElseThrow();
        assertEquals("respond_to_threat", decision.goal().type());
        assertEquals("defend", decision.action().type());
    }

    @Test
    void perceptionLookupDoesNotCreateFakeSocialRelationships() {
        SocialEngine social = new SocialEngine();
        ActorId npc = new ActorId(new UUID(32L, 1L), ActorId.Kind.NPC);
        ActorId player = new ActorId(new UUID(32L, 2L), ActorId.Kind.PLAYER);

        assertTrue(social.findRelationship(npc, player).isEmpty());
        assertTrue(social.snapshot().relationships().isEmpty());

        social.apply(npc, player, new SocialEngine.SocialDelta(-.2D, -.3D, 0D, .5D, 0D, 0D, .1D,
                "threatening_contact", Map.of()));
        assertTrue(social.findRelationship(npc, player).isPresent());
        assertEquals(1, social.snapshot().relationships().size());
    }

    private static NpcSnapshot snapshot(UUID id, Map<String, Double> traits) {
        return new NpcSnapshot(id, 1L, Instant.now(), "NPC", "civilian", traits,
                Map.of(), Map.of(), Map.of(), List.of());
    }
}
