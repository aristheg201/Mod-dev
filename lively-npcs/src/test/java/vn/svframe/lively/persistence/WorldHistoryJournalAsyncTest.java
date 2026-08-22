package vn.svframe.lively.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorldHistoryJournalAsyncTest {
    @TempDir Path temp;

    @Test
    void asyncAppendsRemainOrderedAndReadableAfterFlush() throws Exception {
        WorldHistoryJournal journal = new WorldHistoryJournal(temp.resolve("world-history.lwh"), 4L * 1024L * 1024L);
        for (int i = 1; i <= 64; i++) {
            journal.appendAsync(new WorldHistoryJournal.Entry(i, Instant.ofEpochMilli(1_000L + i),
                    "event", "subject-" + i, Map.of("index", Integer.toString(i))));
        }
        journal.flush().join();
        var entries = journal.readAll();
        assertEquals(64, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(i + 1L, entries.get(i).sequence());
            assertEquals(Integer.toString(i + 1), entries.get(i).facts().get("index"));
        }
        journal.close();
    }
}
