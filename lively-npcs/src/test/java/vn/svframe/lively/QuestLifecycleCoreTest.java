package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.quest.QuestRuntime;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class QuestLifecycleCoreTest {
    @Test
    void completionEmitsProgressStatusAndCompletedExactlyOnce() {
        QuestRuntime quests = new QuestRuntime();
        ActorId issuer = new ActorId(new UUID(20L, 1L), ActorId.Kind.NPC);
        ActorId player = new ActorId(new UUID(20L, 2L), ActorId.Kind.PLAYER);
        AtomicInteger claimed = new AtomicInteger();
        AtomicInteger progressed = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger status = new AtomicInteger();
        quests.addListener(new QuestRuntime.Listener() {
            @Override public void onClaimed(QuestRuntime.Quest quest) { claimed.incrementAndGet(); }
            @Override public void onProgressed(QuestRuntime.Quest before, QuestRuntime.Quest after) { progressed.incrementAndGet(); }
            @Override public void onStatusChanged(QuestRuntime.Quest before, QuestRuntime.Quest after) { status.incrementAndGet(); }
            @Override public void onCompleted(QuestRuntime.Quest quest) { completed.incrementAndGet(); }
        });

        QuestRuntime.Quest quest = quests.create(issuer, null, "Find it",
                java.util.List.of(new QuestRuntime.Objective("main", QuestRuntime.ObjectiveType.EXPLORATION, "ruins", 2L, false, false, Map.of())),
                Duration.ofHours(1), Map.of("reward_budget", "500"));
        assertTrue(quests.claim(quest.id(), player).isPresent());
        assertEquals(1, claimed.get());
        assertEquals(1, status.get());

        assertEquals(QuestRuntime.Status.ACTIVE, quests.progress(quest.id(), "main", 1L).orElseThrow().status());
        assertEquals(QuestRuntime.Status.COMPLETED, quests.progress(quest.id(), "main", 1L).orElseThrow().status());
        assertEquals(2, progressed.get());
        assertEquals(1, completed.get());
        assertEquals(2, status.get());
        assertTrue(quests.progress(quest.id(), "main", 1L).isEmpty());
        assertEquals(1, completed.get());
    }

    @Test
    void transferOnceSurvivesSnapshotRestoreWithoutDoubleCredit() {
        ActorId treasury = new ActorId(new UUID(21L, 1L), ActorId.Kind.SYSTEM);
        ActorId player = new ActorId(new UUID(21L, 2L), ActorId.Kind.PLAYER);
        EconomyEngine first = new EconomyEngine();
        first.ensureWallet(treasury, 10_000L);
        first.ensureWallet(player, 0L);

        var tx = first.transferOnce(EconomyEngine.TransactionType.GIFT, treasury, player, 750L, "quest:q1:reward").orElseThrow();
        assertEquals(750L, first.snapshot().wallets().get(player).balance());
        assertEquals(tx.id(), first.transferOnce(EconomyEngine.TransactionType.GIFT, treasury, player, 750L, "quest:q1:reward").orElseThrow().id());
        assertEquals(750L, first.snapshot().wallets().get(player).balance());

        EconomyEngine restored = new EconomyEngine();
        restored.restore(first.snapshot());
        var retried = restored.transferOnce(EconomyEngine.TransactionType.GIFT, treasury, player, 750L, "quest:q1:reward").orElseThrow();
        assertEquals(tx.id(), retried.id());
        assertEquals(750L, restored.snapshot().wallets().get(player).balance());
        assertEquals(1, restored.snapshot().ledger().stream().filter(entry -> "quest:q1:reward".equals(entry.reference())).count());
    }

    @Test
    void questFactMarkerIsAtomicAndPersistent() {
        QuestRuntime quests = new QuestRuntime();
        QuestRuntime.Quest quest = quests.create(null, null, "Marker",
                java.util.List.of(new QuestRuntime.Objective("main", QuestRuntime.ObjectiveType.CUSTOM, "x", 1, false, false, Map.of())),
                null, Map.of());
        assertTrue(quests.markFactIfAbsent(quest.id(), "reward_paid", "virtual:one"));
        assertFalse(quests.markFactIfAbsent(quest.id(), "reward_paid", "virtual:two"));
        assertEquals("virtual:one", quests.snapshot().quests().get(quest.id()).facts().get("reward_paid"));
    }
}
