package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.persistence.WorldHistoryJournal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class WorldHistoryJournalTest {
    @TempDir Path temp;
    @Test void journalRoundTripsUnicodeAndFacts() throws Exception {
        WorldHistoryJournal journal=new WorldHistoryJournal(temp.resolve("history.lwh"),1024*1024);
        journal.append(new WorldHistoryJournal.Entry(1, Instant.now(),"crime","silverwoods",Map.of("note","Mất Mareep","state","open")));
        var entries=journal.readAll(); assertEquals(1,entries.size()); assertEquals("Mất Mareep",entries.getFirst().facts().get("note"));
    }
}
