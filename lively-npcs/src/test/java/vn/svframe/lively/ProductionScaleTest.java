package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.simulation.SimulationLodController;
import vn.svframe.lively.skin.SkinSource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProductionScaleTest {
    @Test
    void tenThousandSavedActorsDoNotBecomeTenThousandPerTickActors() {
        ActorRegistry registry = new ActorRegistry();
        SimulationLodController lod = new SimulationLodController();
        int active = 0;
        int dormant = 0;
        int dormantScheduledAtTick = 0;
        for (int i = 0; i < 10_000; i++) {
            UUID id = new UUID(0L, i + 1L);
            ActorId actor = new ActorId(id, ActorId.Kind.NPC);
            registry.upsert(actor, "NPC_" + i, Map.of("importance", i < 30 ? 1D : 0.1D), Map.of(), Set.of("npc"));
            boolean near = i < 30;
            SimulationLodController.Level level = lod.classify(near ? 16D : 10_000D, near ? 1D : 0.1D, false, false);
            if (level == SimulationLodController.Level.ACTIVE) active++;
            if (level == SimulationLodController.Level.DORMANT) {
                dormant++;
                if (lod.shouldSimulate(600L, level, id.hashCode())) dormantScheduledAtTick++;
            }
        }
        assertEquals(10_000, registry.snapshot().actors().size());
        assertEquals(30, active);
        assertEquals(9_970, dormant);
        assertTrue(dormantScheduledAtTick < 30, "dormant simulation should be staggered, not scanned as active work");
    }

    @Test
    void skinSourceKeepsLegacyNamesAndExplicitSourcesCompatible() {
        assertEquals(SkinSource.Kind.MOJANG, SkinSource.parse("Notch").kind());
        assertEquals(SkinSource.Kind.MOJANG, SkinSource.parse("mojang:Notch").kind());
        assertEquals(SkinSource.Kind.URL, SkinSource.parse("https://namemc.com/skin/abc").kind());
        assertEquals(SkinSource.Kind.URL, SkinSource.parse("url:https://minecraftskins.com/skin/abc").kind());
        assertEquals(SkinSource.Kind.MINESKIN, SkinSource.parse("mineskin:abc").kind());
        SkinSource texture = SkinSource.parse("texture:value|signature");
        assertEquals(SkinSource.Kind.TEXTURE, texture.kind());
        assertEquals("value", texture.value());
        assertEquals("signature", texture.signature());
    }
}
