package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.memory.MemoryPolicy;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class MemoryConsolidationTest {
    @Test
    void oldWeakMemoriesDecayButPermanentAndRecentFloorSurvive() {
        UUID id = new UUID(50L, 1L);
        NpcState state = new NpcState(id, "Archivist", "npc", 256);
        Instant now = Instant.now();
        List<NpcSnapshot.MemoryView> memories = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            memories.add(new NpcSnapshot.MemoryView(UUID.randomUUID(), now.minus(90, ChronoUnit.DAYS),
                    "small_talk", Map.of("i", Integer.toString(i)), .05D, .5D));
        }
        UUID permanentId = UUID.randomUUID();
        memories.add(new NpcSnapshot.MemoryView(permanentId, now.minus(365, ChronoUnit.DAYS),
                "life_changing", Map.of(), .99D, .9D));
        state.importData(new NpcState.StateData(id, 4L, "Archivist", "npc", Map.of(), Map.of(), Map.of(), Map.of(), memories));

        int removed = state.consolidateMemories(new MemoryPolicy(), now, .06D, 16);

        assertTrue(removed > 0);
        var remaining = state.exportData().memories();
        assertTrue(remaining.stream().anyMatch(memory -> memory.id().equals(permanentId)));
        assertTrue(remaining.size() >= 16);
        assertTrue(remaining.size() < memories.size());
    }

    @Test
    void quietNpcKeepsRecentMinimumEvenIfSignalsAreWeak() {
        UUID id = new UUID(50L, 2L);
        NpcState state = new NpcState(id, "Quiet", "npc", 128);
        Instant now = Instant.now();
        List<NpcSnapshot.MemoryView> memories = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            memories.add(new NpcSnapshot.MemoryView(UUID.randomUUID(), now.minus(180, ChronoUnit.DAYS),
                    "routine", Map.of(), .01D, .1D));
        }
        state.importData(new NpcState.StateData(id, 1L, "Quiet", "npc", Map.of(), Map.of(), Map.of(), Map.of(), memories));
        state.consolidateMemories(new MemoryPolicy(), now, .9D, 32);
        assertEquals(32, state.exportData().memories().size());
    }
}
