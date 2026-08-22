package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.GamblingEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DebtGamblingTest {
    @Test void debtAccruesBecomesDelinquentAndCanBeRepaid() {
        DebtEngine debts = new DebtEngine();
        ActorId lender = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        ActorId debtor = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        Instant now = Instant.now();
        DebtEngine.Contract contract = debts.issue(lender, debtor, 1_000L, 1_000, now.plusSeconds(30), false, Map.of("source", "test"));
        debts.accrue(now.plus(Duration.ofMinutes(21)), Duration.ofMinutes(20));
        DebtEngine.Contract accrued = debts.get(contract.id()).orElseThrow();
        assertTrue(accrued.outstanding() > 1_000L);
        assertEquals(DebtEngine.Status.DELINQUENT, accrued.status());
        debts.pay(contract.id(), accrued.outstanding());
        assertEquals(DebtEngine.Status.REPAID, debts.get(contract.id()).orElseThrow().status());
    }

    @Test void gamblingHistoryBuildsPersistentHabitState() {
        GamblingEngine gambling = new GamblingEngine();
        ActorId actor = new ActorId(UUID.randomUUID(), ActorId.Kind.NPC);
        gambling.record(actor, UUID.randomUUID(), "tai_xiu", "internal", 100L, 0L, GamblingEngine.Result.LOSS, Map.of());
        gambling.record(actor, UUID.randomUUID(), "tai_xiu", "internal", 100L, 200L, GamblingEngine.Result.WIN, Map.of());
        GamblingEngine.Habit habit = gambling.habit(actor);
        assertTrue(habit.exposure() > 0D);
        assertEquals(200L, habit.lifetimeStake());
        assertEquals(200L, habit.lifetimePayout());
        GamblingEngine copy = new GamblingEngine();
        copy.restore(gambling.snapshot());
        assertEquals(habit.lifetimeStake(), copy.habit(actor).lifetimeStake());
    }
}
