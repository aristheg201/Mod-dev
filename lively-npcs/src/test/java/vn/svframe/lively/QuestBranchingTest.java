package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.quest.QuestRuntime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class QuestBranchingTest {
    @Test
    void laterStageCannotProgressBeforeRequiredStage() {
        QuestRuntime runtime = new QuestRuntime();
        ActorId player = player(1L);
        QuestRuntime.Quest quest = active(runtime, player, List.of(
                objective("discover", 1, false, Map.of("stage", "1")),
                objective("report", 1, false, Map.of("stage", "2", "requires", "discover"))));

        assertTrue(runtime.progress(quest.id(), "report", 1L).isEmpty());
        assertEquals(0L, runtime.snapshot().quests().get(quest.id()).progress().getOrDefault("report", 0L));
        assertTrue(runtime.progress(quest.id(), "discover", 1L).isPresent());
        assertTrue(runtime.progress(quest.id(), "report", 1L).isPresent());
        assertEquals(QuestRuntime.Status.COMPLETED, runtime.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void choosingOneRequiredBranchLocksTheOtherAndSatisfiesGroup() {
        QuestRuntime runtime = new QuestRuntime();
        ActorId player = player(2L);
        QuestRuntime.Quest quest = active(runtime, player, List.of(
                objective("release", 1, false, Map.of("stage", "1", "branch_group", "ending")),
                objective("commission", 1, false, Map.of("stage", "1", "branch_group", "ending")),
                objective("epilogue", 1, false, Map.of("stage", "2"))));

        assertTrue(runtime.progress(quest.id(), "release", 1L).isPresent());
        QuestRuntime.Quest afterChoice = runtime.snapshot().quests().get(quest.id());
        assertFalse(runtime.isAvailable(afterChoice, afterChoice.objectives().stream().filter(o -> o.id().equals("commission")).findFirst().orElseThrow()));
        assertTrue(runtime.isAvailable(afterChoice, afterChoice.objectives().stream().filter(o -> o.id().equals("epilogue")).findFirst().orElseThrow()));
        assertTrue(runtime.progress(quest.id(), "commission", 1L).isEmpty());
        assertTrue(runtime.progress(quest.id(), "epilogue", 1L).isPresent());
        assertEquals(QuestRuntime.Status.COMPLETED, runtime.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void optionalHiddenObjectiveNeverBlocksMainProgression() {
        QuestRuntime runtime = new QuestRuntime();
        ActorId player = player(3L);
        QuestRuntime.Quest quest = active(runtime, player, List.of(
                objective("main", 1, false, Map.of()),
                new QuestRuntime.Objective("secret", QuestRuntime.ObjectiveType.INVESTIGATION, "secret", 1L, true, true, Map.of())));
        assertTrue(runtime.progress(quest.id(), "main", 1L).isPresent());
        assertEquals(QuestRuntime.Status.COMPLETED, runtime.snapshot().quests().get(quest.id()).status());
    }

    @Test
    void unknownDependencyIsRejectedAtCreation() {
        QuestRuntime runtime = new QuestRuntime();
        assertThrows(IllegalArgumentException.class, () -> runtime.create(null, null, "bad",
                List.of(objective("second", 1, false, Map.of("requires", "missing"))), null, Map.of()));
    }

    private static QuestRuntime.Objective objective(String id, long required, boolean optional, Map<String, String> facts) {
        return new QuestRuntime.Objective(id, QuestRuntime.ObjectiveType.CUSTOM, id, required, optional, false, facts);
    }

    private static QuestRuntime.Quest active(QuestRuntime runtime, ActorId owner, List<QuestRuntime.Objective> objectives) {
        QuestRuntime.Quest offer = runtime.create(null, null, "Branch", objectives, null, Map.of());
        return runtime.claim(offer.id(), owner).orElseThrow();
    }

    private static ActorId player(long value) {
        return new ActorId(new UUID(100L, value), ActorId.Kind.PLAYER);
    }
}
