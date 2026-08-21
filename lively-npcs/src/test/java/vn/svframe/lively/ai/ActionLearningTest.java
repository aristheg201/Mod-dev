package vn.svframe.lively.ai;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.WorldSnapshot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ActionLearningTest {
    @Test
    void hungryNpcPrefersDirectFoodBeforeLearning() {
        NpcSnapshot npc = snapshot(List.of());
        Decision decision = new LivelyAiEngine().decide(npc, world()).orElseThrow();
        assertEquals("satisfy_hunger", decision.goal().type());
        assertEquals("consume_food", decision.action().type());
    }

    @Test
    void repeatedRealFailuresCanTeachNpcToSeekFoodFirst() {
        Instant now = Instant.now();
        List<NpcSnapshot.MemoryView> memories = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            memories.add(outcome(now.minus(i, ChronoUnit.SECONDS), "consume_food", false));
            memories.add(outcome(now.minus(i, ChronoUnit.SECONDS), "seek_food", true));
        }
        NpcSnapshot npc = snapshot(memories);
        LivelyAiEngine ai = new LivelyAiEngine();

        assertTrue(ai.learnedActionBias(npc, "consume_food", now) < 0D);
        assertTrue(ai.learnedActionBias(npc, "seek_food", now) > 0D);
        Decision decision = ai.decide(npc, world()).orElseThrow();
        assertEquals("seek_food", decision.action().type(), "learned outcomes should overcome the small static utility gap");
    }

    @Test
    void oldWeakOutcomeHasMuchLessInfluenceThanFreshOutcome() {
        Instant now = Instant.now();
        LivelyAiEngine ai = new LivelyAiEngine();
        NpcSnapshot fresh = snapshot(List.of(outcome(now, "seek_food", true)));
        NpcSnapshot old = snapshot(List.of(new NpcSnapshot.MemoryView(UUID.randomUUID(), now.minus(120, ChronoUnit.DAYS),
                "action_outcome", Map.of("action", "seek_food", "success", "true"), .05D, .2D)));
        assertTrue(ai.learnedActionBias(fresh, "seek_food", now) > ai.learnedActionBias(old, "seek_food", now));
    }

    private static NpcSnapshot.MemoryView outcome(Instant at, String action, boolean success) {
        return new NpcSnapshot.MemoryView(UUID.randomUUID(), at, "action_outcome",
                Map.of("action", action, "success", Boolean.toString(success)), success ? .20D : .30D, 1D);
    }

    private static NpcSnapshot snapshot(List<NpcSnapshot.MemoryView> memories) {
        return new NpcSnapshot(new UUID(120L, 1L), 1L, Instant.now(), "Hungry", "civilian",
                Map.of(), Map.of("hunger", .90D), Map.of(), Map.of(), memories);
    }

    private static WorldSnapshot world() {
        return new WorldSnapshot(1L, "minecraft:overworld", 100L, List.of(), Map.of());
    }
}
