package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.quest.QuestWaypointResolver;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class QuestWaypointResolverTest {
    @Test
    void structureEntranceBeatsCenter() {
        SemanticStructureRegistry structures = new SemanticStructureRegistry();
        structures.register(new SemanticStructureRegistry.Structure("market", "shop",
                new SemanticStructureRegistry.Bounds("minecraft:overworld", 0, 60, 0, 10, 70, 10),
                Set.of(), Map.of("entrance", "2.5,64,1.5"), null, null,
                SemanticStructureRegistry.OperationalState.OPEN, 0L));
        QuestRuntime.Objective objective = new QuestRuntime.Objective("visit", QuestRuntime.ObjectiveType.EXPLORATION,
                "market", 1L, false, false, Map.of("structure", "market"));

        var target = QuestWaypointResolver.resolve(quest(objective, Map.of()), structures).orElseThrow();
        assertEquals("minecraft:overworld", target.world());
        assertEquals(2.5D, target.position().x, 0.0001D);
        assertEquals(64D, target.position().y, 0.0001D);
        assertEquals(1.5D, target.position().z, 0.0001D);
    }

    @Test
    void explicitCoordinatesWorkWithoutStructure() {
        QuestRuntime.Objective objective = new QuestRuntime.Objective("trail", QuestRuntime.ObjectiveType.EXPLORATION,
                "migration", 1L, false, false, Map.of("world", "minecraft:overworld", "x", "12.25", "y", "70", "z", "-5.5"));
        var target = QuestWaypointResolver.resolve(quest(objective, Map.of()), new SemanticStructureRegistry()).orElseThrow();
        assertEquals(12.25D, target.position().x, 0.0001D);
        assertEquals(-5.5D, target.position().z, 0.0001D);
    }

    @Test
    void completedObjectiveFallsThroughToNextLocatableObjective() {
        QuestRuntime.Objective first = new QuestRuntime.Objective("first", QuestRuntime.ObjectiveType.EXPLORATION,
                "a", 1L, false, false, Map.of("world", "minecraft:overworld", "x", "1", "y", "64", "z", "1"));
        QuestRuntime.Objective second = new QuestRuntime.Objective("second", QuestRuntime.ObjectiveType.EXPLORATION,
                "b", 1L, false, false, Map.of("world", "minecraft:the_nether", "x", "9", "y", "70", "z", "9"));
        QuestRuntime.Quest quest = new QuestRuntime.Quest(UUID.randomUUID(), null, player(), "Route",
                List.of(first, second), Map.of("first", 1L), QuestRuntime.Status.ACTIVE,
                Instant.now(), null, Map.of(), 1L);
        var target = QuestWaypointResolver.resolve(quest, new SemanticStructureRegistry()).orElseThrow();
        assertEquals("minecraft:the_nether", target.world());
        assertEquals(9D, target.position().x, 0.0001D);
    }

    @Test
    void malformedCoordinatesAreRejected() {
        QuestRuntime.Objective objective = new QuestRuntime.Objective("bad", QuestRuntime.ObjectiveType.EXPLORATION,
                "bad", 1L, false, false, Map.of("world", "minecraft:overworld", "x", "NaN", "y", "64", "z", "0"));
        assertTrue(QuestWaypointResolver.resolve(quest(objective, Map.of()), new SemanticStructureRegistry()).isEmpty());
    }

    private static QuestRuntime.Quest quest(QuestRuntime.Objective objective, Map<String, Long> progress) {
        return new QuestRuntime.Quest(UUID.randomUUID(), null, player(), "Quest target",
                List.of(objective), progress, QuestRuntime.Status.ACTIVE, Instant.now(), null, Map.of(), 1L);
    }

    private static ActorId player() {
        return new ActorId(new UUID(90L, 1L), ActorId.Kind.PLAYER);
    }
}
