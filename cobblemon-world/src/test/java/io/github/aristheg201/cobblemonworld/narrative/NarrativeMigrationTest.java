package io.github.aristheg201.cobblemonworld.narrative;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NarrativeMigrationTest {
    private NarrativeRegistry.Stage[] campaign() { var r = new NarrativeRegistry(); r.load(); return r.data.campaign(); }

    @Test void stuckPlayerMovesToErrandWithoutBuyingOrLosingHistory() {
        var n = new NarrativeState(); n.schema = 1; n.main = "ren_supplies";
        n.finished.add("hale_phone"); n.chains.put("lost_parcel", "parcel_return");
        n.personality.put("careful", 3); n.claims.put("reward", new NarrativeState.Claim("saved-uuid", "delivered"));
        n.sceneNodes.put("hale_phone", "facts");
        n.transcript.add(new NarrativeState.Turn("hale_phone", "start", "professor_hale", "old-text"));
        var flags = new HashSet<>(Set.of("professor_met", "phone_unlocked"));
        assertTrue(NarrativeMigration.apply(n, flags, campaign()));
        assertEquals("lan_errand", n.main); assertFalse(n.finished.contains("ren_supplies"));
        assertFalse(n.completedChains.contains(NarrativeMigration.OPTIONAL_SUPPLIES));
        assertEquals("parcel_return", n.chains.get("lost_parcel")); assertEquals(3, n.personality.get("careful"));
        assertEquals("saved-uuid", n.claims.get("reward").pokemonUuid()); assertEquals(1, n.transcript.size());
        assertEquals(Set.of("professor_met", "phone_unlocked"), flags);
        String saved = new Gson().toJson(n);
        assertFalse(NarrativeMigration.apply(n, flags, campaign())); assertEquals(saved, new Gson().toJson(n));
    }

    @Test void completedPurchaseIsNotOfferedOrRewardedAgain() {
        var n = new NarrativeState(); n.schema = 1; n.main = "mara_first"; n.finished.add("ren_supplies");
        assertTrue(NarrativeMigration.apply(n, new HashSet<>(), campaign()));
        assertEquals("mara_first", n.main); assertTrue(n.completedChains.contains(NarrativeMigration.OPTIONAL_SUPPLIES));
        assertTrue(n.payments.isEmpty());
    }

    @Test void legacySaveInfersBattleHistoryAndStartsWithoutMandatoryShop() {
        var n = new NarrativeState();
        var flags = new HashSet<>(Set.of("mara_voss_defeated"));
        NarrativeMigration.apply(n, flags, campaign());
        assertEquals("field_fault", n.main); assertTrue(n.finished.contains("battle:mara_voss"));
        assertFalse(n.finished.contains("ren_supplies"));
    }
}
