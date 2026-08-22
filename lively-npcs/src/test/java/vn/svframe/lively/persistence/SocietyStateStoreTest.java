package vn.svframe.lively.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.GamblingEngine;
import vn.svframe.lively.law.LawEnforcementEngine;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SocietyStateStoreTest {
    @TempDir Path temp;

    @Test void societyStateSurvivesChecksummedAsyncRoundTrip() {
        ActorId lender = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId debtor = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId officer = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        DebtEngine debts = new DebtEngine();
        debts.issue(lender, debtor, 2_500L, 900, Instant.now().plusSeconds(600), false, Map.of("source", "test"));
        GamblingEngine gambling = new GamblingEngine();
        gambling.record(debtor, UUID.randomUUID(), "tai_xiu", "internal", 200L, 0L,
                GamblingEngine.Result.LOSS, Map.of("test", "true"));

        LawEnforcementEngine law = new LawEnforcementEngine();
        UUID crime = UUID.randomUUID();
        law.raiseWanted(debtor, "test_town", crime, 8, .88D, 25L);
        LawEnforcementEngine.Warrant warrant = law.issueWarrant(debtor, "test_town", Set.of(crime), .88D, Duration.ofHours(1));
        LawEnforcementEngine.Custody custody = law.detain(debtor, officer, warrant, "test_jail", 500L, 250L, true, Map.of());
        LawEnforcementEngine.CourtCase court = law.fileCourtCase(debtor, "test_town", Set.of(crime), custody.id(),
                Instant.now(), 4, Map.of("test", "true"));
        law.decide(court.id(), true, .91D, .05D, 4, 500L, 120L, Map.of("basis", "evidence"));
        law.jail(custody.id(), Instant.now().plusSeconds(120), 500L, 250L, Map.of("case", court.id().toString()));

        Path file = temp.resolve("world/livelynpcs/state/society.json");
        try (SocietyStateStore store = new SocietyStateStore(file)) {
            store.saveAsync(new SocietyStateStore.Bundle(debts.snapshot(), gambling.snapshot(), law.snapshot())).join();
            SocietyStateStore.Bundle loaded = store.load().orElseThrow();
            assertEquals(1, loaded.debts().contracts().size());
            assertEquals(1, loaded.gambling().bets().size());
            assertTrue(loaded.gambling().habits().containsKey(debtor));
            assertEquals(1, loaded.law().wanted().size());
            assertEquals(1, loaded.law().warrants().size());
            assertEquals(1, loaded.law().custody().size());
            assertEquals(1, loaded.law().courtCases().size());
        }
    }
}
