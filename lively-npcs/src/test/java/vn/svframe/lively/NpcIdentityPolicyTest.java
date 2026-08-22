package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcIdentityPolicy;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class NpcIdentityPolicyTest {
    @Test
    void publicAliasRemainsUntilTrustOrReputationUnlocksIdentity() {
        NpcDefinition npc = definition(Map.of(
                "identity.real_name", "Mara Venn",
                "identity.reveal_trust", "0.65",
                "identity.reveal_reputation", "0.80"));

        var hidden = NpcIdentityPolicy.resolve(npc, .64D, .79D);
        assertFalse(hidden.revealed());
        assertEquals("Archivist", hidden.displayName());

        var trusted = NpcIdentityPolicy.resolve(npc, .66D, -.20D);
        assertTrue(trusted.revealed());
        assertEquals("Mara Venn", trusted.displayName());

        var reputable = NpcIdentityPolicy.resolve(npc, .05D, .81D);
        assertTrue(reputable.revealed());
        assertEquals("Mara Venn", reputable.displayName());
    }

    @Test
    void ordinaryNpcNeverInventsSecretIdentity() {
        NpcDefinition npc = definition(Map.of());
        var result = NpcIdentityPolicy.resolve(npc, 1D, 1D);
        assertFalse(result.revealed());
        assertEquals("Archivist", result.displayName());
    }

    private static NpcDefinition definition(Map<String, String> metadata) {
        return new NpcDefinition(new UUID(4L, 5L), "Archivist", "mysterious", NpcDefinition.BodyType.PLAYER,
                "", "", "minecraft:overworld", 0D, 64D, 0D, 0F, 0F,
                true, true, true, true, false, true, metadata);
    }
}
