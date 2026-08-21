package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.social.SocialEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic load gate for large dormant-world state without Minecraft world access. */
final class ProductionStressTest {
    @Test
    void largeSocialEconomyCrimeStateRemainsConsistent() {
        final int actors = 5_000;
        ActorRegistry registry = new ActorRegistry();
        SocialEngine social = new SocialEngine();
        EconomyEngine economy = new EconomyEngine();
        CrimeEngine crime = new CrimeEngine();
        List<ActorId> ids = new ArrayList<>(actors);

        for (int i = 0; i < actors; i++) {
            ActorId actor = new ActorId(new UUID(7L, i + 1L), ActorId.Kind.NPC);
            ids.add(actor);
            registry.upsert(actor, "NPC_" + i, Map.of("friendly", (i % 10) / 10D), Map.of("town", "town_" + (i % 20)), java.util.Set.of("npc"));
            economy.ensureWallet(actor, 100_000L);
        }

        for (int i = 0; i < 25_000; i++) {
            ActorId a = ids.get(i % actors);
            ActorId b = ids.get((i * 37 + 17) % actors);
            if (a.equals(b)) b = ids.get((b.uuid().hashCode() + 1 & Integer.MAX_VALUE) % actors);
            social.apply(a, b, new SocialEngine.SocialDelta(.001D, .002D, .001D, 0D, 0D, .001D, .005D, "stress_contact", Map.of()));
        }

        ActorId market = new ActorId(new UUID(8L, 1L), ActorId.Kind.SYSTEM);
        economy.ensureWallet(market, 1_000_000_000L);
        for (int i = 0; i < actors; i++) {
            assertTrue(economy.transfer(EconomyEngine.TransactionType.WAGE, market, ids.get(i), 10L, "stress-wage").isPresent());
        }

        for (int i = 0; i < 1_000; i++) {
            ActorId suspect = ids.get(i);
            CrimeEngine.Crime c = crime.create(CrimeEngine.CrimeType.THEFT, suspect, ids.get((i + 1) % actors), "market_" + (i % 20), Map.of("stress", "true"));
            crime.addEvidence(c.id(), new CrimeEngine.Evidence(UUID.randomUUID(), CrimeEngine.EvidenceType.WITNESS, suspect,
                    .7D, java.time.Instant.now(), "stress witness", Map.of()));
        }

        SocialEngine.Rumor rumor = social.createRumor("stress", ids.get(1), ids.get(0), "test rumor", .9D, .5D, Duration.ofHours(1));
        for (int i = 1; i < 12; i++) social.propagate(rumor.id(), ids.get(i - 1), ids.get(i));

        assertEquals(actors, registry.snapshot().actors().size());
        assertTrue(social.snapshot().relationships().size() >= 4_000);
        assertEquals(actors + 1, economy.snapshot().wallets().size());
        assertEquals(5_000, economy.snapshot().ledger().size());
        assertEquals(1_000, crime.snapshot().crimes().size());
        assertTrue(crime.snapshot().evidence().size() >= 1_000);
    }
}
