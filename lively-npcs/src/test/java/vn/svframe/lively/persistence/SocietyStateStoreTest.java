package vn.svframe.lively.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.GamblingEngine;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SocietyStateStoreTest {
    @TempDir Path temp;

    @Test void societyStateSurvivesChecksummedAsyncRoundTrip() {
        ActorId lender = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId debtor = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        DebtEngine debts = new DebtEngine();
        debts.issue(lender, debtor, 2_500L, 900, Instant.now().plusSeconds(600), false, Map.of("source", "test"));
        GamblingEngine gambling = new GamblingEngine();
        gambling.record(debtor, UUID.randomUUID(), "tai_xiu", "internal", 200L, 0L,
                GamblingEngine.Result.LOSS, Map.of("test", "true"));

        Path file = temp.resolve("world/livelynpcs/state/society.json");
        try (SocietyStateStore store = new SocietyStateStore(file)) {
            store.saveAsync(new SocietyStateStore.Bundle(debts.snapshot(), gambling.snapshot())).join();
            SocietyStateStore.Bundle loaded = store.load().orElseThrow();
            assertEquals(1, loaded.debts().contracts().size());
            assertEquals(1, loaded.gambling().bets().size());
            assertTrue(loaded.gambling().habits().containsKey(debtor));
        }
    }
}
