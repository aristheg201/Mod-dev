package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.law.LawEnforcementEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LawEnforcementEngineTest {
    @Test void repeatedReviewOfSameCrimeDoesNotInflateWantedStateAndResolvedCrimeCanBeRemoved() {
        LawEnforcementEngine law = new LawEnforcementEngine();
        ActorId suspect = npc();
        UUID crime = UUID.randomUUID();

        LawEnforcementEngine.WantedRecord first = law.raiseWanted(suspect, "valentine", crime, 8, .82D, 25L);
        LawEnforcementEngine.WantedRecord second = law.raiseWanted(suspect, "valentine", crime, 8, .82D, 25L);

        assertEquals(first.points(), second.points());
        assertEquals(first.bounty(), second.bounty());
        assertEquals(Set.of(crime), second.crimeIds());

        UUID anotherCrime = UUID.randomUUID();
        LawEnforcementEngine.WantedRecord third = law.raiseWanted(suspect, "valentine", anotherCrime, 5, .75D, 25L);
        assertTrue(third.points() > second.points());
        assertTrue(third.bounty() > second.bounty());
        assertEquals(Set.of(crime, anotherCrime), third.crimeIds());

        LawEnforcementEngine.WantedRecord remaining = law.removeWantedCrime(suspect, "valentine", crime).orElseThrow();
        assertEquals(Set.of(anotherCrime), remaining.crimeIds());
        assertTrue(remaining.points() < third.points());
        assertTrue(remaining.bounty() < third.bounty());

        law.removeWantedCrime(suspect, "valentine", anotherCrime);
        assertTrue(law.wanted(suspect, "valentine").isEmpty());
    }

    @Test void warrantCustodyCourtAndReleaseArePersistentStateTransitions() {
        LawEnforcementEngine law = new LawEnforcementEngine();
        ActorId suspect = npc();
        ActorId officer = npc();
        UUID crime = UUID.randomUUID();
        LawEnforcementEngine.Warrant warrant = law.issueWarrant(suspect, "valentine", Set.of(crime), .88D, Duration.ofHours(1));

        LawEnforcementEngine.Custody custody = law.detain(suspect, officer, warrant, "valentine_jail", 500L, 250L, true, Map.of());
        assertEquals(LawEnforcementEngine.WarrantStatus.SERVED, law.warrant(warrant.id()).orElseThrow().status());
        assertTrue(law.activeCustody(suspect).isPresent());

        LawEnforcementEngine.CourtCase court = law.fileCourtCase(suspect, "valentine", Set.of(crime), custody.id(), Instant.now(), 5, Map.of());
        law.decide(court.id(), true, .91D, .08D, 5, 500L, 120L, Map.of());
        law.jail(custody.id(), Instant.now().plusSeconds(120), 500L, 250L, Map.of());
        assertEquals(LawEnforcementEngine.CourtStatus.CONVICTED, law.courtCase(court.id()).orElseThrow().status());
        assertEquals(LawEnforcementEngine.CustodyStatus.JAILED, law.custody(custody.id()).orElseThrow().status());

        law.overturn(court.id(), .35D, .82D, 7, "new_evidence");
        law.release(custody.id(), "overturned");
        assertEquals(LawEnforcementEngine.CourtStatus.OVERTURNED, law.courtCase(court.id()).orElseThrow().status());
        assertFalse(law.activeCustody(suspect).isPresent());
    }

    private static ActorId npc() { return new ActorId(UUID.randomUUID(), ActorId.Kind.NPC); }
}
