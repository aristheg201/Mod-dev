package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.event.WorldChronicleEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.persistence.WorldHistoryJournal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WorldChronicleEngineTest {
    @Test
    void majorEventsCreateChronicleEntriesAndEraBreaks() {
        WorldChronicleEngine engine = new WorldChronicleEngine();
        Instant now = Instant.now();
        engine.record(new WorldEventEngine.WorldEvent(UUID.randomUUID(), WorldEventEngine.Category.SOCIAL, "market_day", null,
                Set.of(), .40D, now, now.plusSeconds(60), WorldEventEngine.Phase.FINISHED, Map.of()));
        engine.record(new WorldEventEngine.WorldEvent(UUID.randomUUID(), WorldEventEngine.Category.DISASTER, "great_flood", "harbor",
                Set.of(), 1D, now, now.plusSeconds(60), WorldEventEngine.Phase.FINISHED, Map.of("era_break", "true")));

        assertEquals(2, engine.latest(10).size());
        assertEquals(2, engine.eras().size());
        assertEquals("memorial", engine.latest(1).getFirst().facts().get("memorial_suggestion"));
        assertEquals("true", engine.latest(1).getFirst().facts().get("chronicle_book"));
    }

    @Test
    void rebuildUsesVerifiedFinishedJournalRecordsOnly() {
        WorldChronicleEngine engine = new WorldChronicleEngine();
        Instant older = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant recent = Instant.now();
        engine.rebuild(List.of(
                new WorldHistoryJournal.Entry(1L, older, "event_started", "a", Map.of("category", "CRIME", "seed", "ignored")),
                new WorldHistoryJournal.Entry(2L, older, "event_finished", "b", Map.of("category", "CRIME", "seed", "old_case", "intensity", "0.7")),
                new WorldHistoryJournal.Entry(3L, recent, "event_finished", "c", Map.of("category", "MIGRATION", "seed", "eevee_migration", "intensity", "0.8"))));

        assertEquals(2, engine.latest(10).size());
        assertEquals(2, engine.eras().size());
        assertEquals("eevee_migration", engine.latest(1).getFirst().seed());
    }
}
