package vn.svframe.lively.schedule;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.npc.NpcDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ScheduleSynthesisTest {
    @Test
    void homeAndWorkProduceAFullDailyRoutine() {
        NpcDefinition npc = definition(Map.of(
                "home.structure", "home_a",
                "work.structure", "shop_a",
                "occupation.activity", "trade",
                "schedule.work_start", "480",
                "schedule.work_end", "1020"));
        List<ScheduleEngine.ScheduleEntry> schedule = ScheduleSynthesisBootstrap.synthesize(npc);

        assertFalse(schedule.isEmpty());
        assertTrue(schedule.stream().anyMatch(entry -> entry.activity().equals("sleep") && entry.semanticLocation().equals("home_a") && entry.active(60)));
        assertTrue(schedule.stream().anyMatch(entry -> entry.activity().equals("trade") && entry.semanticLocation().equals("shop_a") && entry.active(600)));
        assertTrue(schedule.stream().anyMatch(entry -> entry.activity().equals("home_routine") && entry.semanticLocation().equals("home_a") && entry.active(1200)));
        assertTrue(schedule.stream().allMatch(entry -> "lively".equals(entry.constraints().get("generated"))));
    }

    @Test
    void missingHomeOrWorkDoesNotInventAPlace() {
        assertTrue(ScheduleSynthesisBootstrap.synthesize(definition(Map.of("home.structure", "home_a"))).isEmpty());
        assertTrue(ScheduleSynthesisBootstrap.synthesize(definition(Map.of("work.structure", "shop_a"))).isEmpty());
    }

    @Test
    void authoredSchedulePresenceIsDetectableAndPreservedByContract() {
        ScheduleEngine engine = new ScheduleEngine();
        ActorId actor = new ActorId(new UUID(110L, 2L), ActorId.Kind.NPC);
        List<ScheduleEngine.ScheduleEntry> authored = List.of(new ScheduleEngine.ScheduleEntry(
                300, 900, "custom_shift", "guild_hall", 99, true, Map.of("authored", "true")));
        engine.setSchedule(actor, authored);
        assertTrue(engine.hasSchedule(actor));
        assertEquals(authored, engine.schedule(actor));
    }

    private static NpcDefinition definition(Map<String, String> metadata) {
        return new NpcDefinition(new UUID(110L, 1L), "Worker", "merchant", NpcDefinition.BodyType.PLAYER,
                "", "", "minecraft:overworld", 0D, 64D, 0D, 0F, 0F,
                true, true, true, true, false, true, metadata);
    }
}
