package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.quest.QuestRuntime;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class QuestSignalRoutingTest {
    @Test
    void socialSignalCompletesOnlyMatchingNpcObjective() {
        QuestRuntime quests = new QuestRuntime();
        ActorId player = player(1L);
        String npc = new UUID(61L, 1L).toString();
        QuestRuntime.Quest quest = active(quests, player, new QuestRuntime.Objective(
                "talk", QuestRuntime.ObjectiveType.SOCIAL, npc, 1L, false, false, Map.of("npc", npc)));

        assertEquals(0, quests.signal(player, QuestRuntime.ObjectiveType.SOCIAL, new UUID(61L, 2L).toString(), 1L, Map.of()));
        assertEquals(QuestRuntime.Status.ACTIVE, quests.snapshot().quests().get(quest.id()).status());
        assertEquals(1, quests.signal(player, QuestRuntime.ObjectiveType.SOCIAL, npc, 1L, Map.of("actor", npc)));
        assertEquals(QuestRuntime.Status.COMPLETED, quests.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void investigationEventAliasProgressesGeneratedObjective() {
        QuestRuntime quests = new QuestRuntime();
        ActorId player = player(2L);
        String event = new UUID(62L, 1L).toString();
        QuestRuntime.Quest quest = active(quests, player, new QuestRuntime.Objective(
                "main", QuestRuntime.ObjectiveType.INVESTIGATION, event, 1L, false, false, Map.of("event", event)));

        assertEquals(1, quests.signal(player, QuestRuntime.ObjectiveType.INVESTIGATION, new UUID(62L, 2L).toString(), 1L,
                Map.of("event", event, "crime", new UUID(62L, 3L).toString())));
        assertEquals(QuestRuntime.Status.COMPLETED, quests.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void repeatedCollectionSignalsRespectRequiredAmount() {
        QuestRuntime quests = new QuestRuntime();
        ActorId player = player(3L);
        QuestRuntime.Quest quest = active(quests, player, new QuestRuntime.Objective(
                "catch", QuestRuntime.ObjectiveType.COLLECTION, "cobblemon:eevee", 2L, false, false,
                Map.of("species", "cobblemon:eevee")));

        assertEquals(1, quests.signal(player, QuestRuntime.ObjectiveType.COLLECTION, "cobblemon:eevee", 1L,
                Map.of("species", "cobblemon:eevee")));
        assertEquals(QuestRuntime.Status.ACTIVE, quests.snapshot().quests().get(quest.id()).status());
        assertEquals(1L, quests.snapshot().quests().get(quest.id()).progress().get("catch"));
        assertEquals(1, quests.signal(player, QuestRuntime.ObjectiveType.COLLECTION, "cobblemon:eevee", 1L,
                Map.of("species", "cobblemon:eevee")));
        assertEquals(QuestRuntime.Status.COMPLETED, quests.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void unrelatedSignalCannotCompleteQuestThroughLooseFacts() {
        QuestRuntime quests = new QuestRuntime();
        ActorId player = player(4L);
        QuestRuntime.Quest quest = active(quests, player, new QuestRuntime.Objective(
                "fight", QuestRuntime.ObjectiveType.COMBAT, "cobblemon:battle", 1L, false, false,
                Map.of("location", "arena_a")));

        assertEquals(0, quests.signal(player, QuestRuntime.ObjectiveType.COMBAT, "minecraft:kill", 1L,
                Map.of("location", "arena_b", "battle", "other")));
        assertEquals(QuestRuntime.Status.ACTIVE, quests.snapshot().quests().get(quest.id()).status());
    }

    private static QuestRuntime.Quest active(QuestRuntime quests, ActorId owner, QuestRuntime.Objective objective) {
        QuestRuntime.Quest offered = quests.create(null, null, "Quest", List.of(objective), Duration.ofHours(1), Map.of());
        return quests.claim(offered.id(), owner).orElseThrow();
    }

    private static ActorId player(long id) {
        return new ActorId(new UUID(60L, id), ActorId.Kind.PLAYER);
    }
}
