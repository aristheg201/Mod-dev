package vn.svframe.lively.memory;

import vn.svframe.lively.model.NpcSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Importance-aware recall/decay. Source memories remain immutable; policy only ranks and selects them. */
public final class MemoryPolicy {
    public double recallScore(NpcSnapshot.MemoryView memory, Instant now) {
        long ageSeconds = Math.max(0L, Duration.between(memory.occurredAt(), now).toSeconds());
        double halfLifeDays = 0.5D + memory.importance() * 29.5D;
        double decay = Math.pow(0.5D, ageSeconds / (halfLifeDays * 86400D));
        double permanentFloor = memory.importance() >= 0.97D ? 0.85D : 0D;
        return Math.max(permanentFloor, memory.confidence() * (0.35D + memory.importance() * 0.65D) * decay);
    }

    public boolean permanent(NpcSnapshot.MemoryView memory) { return memory.importance() >= 0.97D; }

    public List<NpcSnapshot.MemoryView> recall(List<NpcSnapshot.MemoryView> memories, Instant now, double minScore, int limit) {
        return memories.stream().filter(memory -> recallScore(memory, now) >= minScore)
                .sorted(Comparator.comparingDouble((NpcSnapshot.MemoryView memory) -> recallScore(memory, now)).reversed())
                .limit(Math.max(1, Math.min(256, limit))).toList();
    }
}
