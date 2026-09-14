package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.Id;
import vn.svframe.svarcade.runtime.ThreadGuard;
import vn.svframe.svarcade.stats.StatsManager;
import static org.junit.jupiter.api.Assertions.*;

class StatsManagerTest {
    @Test void metricsAggregatePersistAndRankGenerically() {
        ThreadGuard thread = new ThreadGuard(); Id score = Id.of("test:score"), best = Id.of("test:best");
        List<StatsManager.Metric> metrics = List.of(new StatsManager.Metric(score, StatsManager.Aggregation.SUM, 0, 1_000_000),
                new StatsManager.Metric(best, StatsManager.Aggregation.MAX, 0, 1_000_000));
        StatsManager first = new StatsManager(thread, metrics, 100); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        first.record(a, score, 5); first.record(a, score, 7); first.record(b, score, 20); first.record(a, best, 4); first.record(a, best, 3);
        assertEquals(12, first.value(a, score)); assertEquals(4, first.value(a, best)); assertEquals(b, first.leaderboard(score, 10).getFirst().subject());
        Map<String,Object> saved = first.snapshot(); StatsManager restored = new StatsManager(thread, metrics, 100); restored.restore(saved);
        assertEquals(12, restored.value(a, score)); assertEquals(20, restored.value(b, score)); assertEquals(first.revision(), restored.revision());
    }
}
