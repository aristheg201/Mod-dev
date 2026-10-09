package vn.svframe.svrtp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigTest {
    @TempDir Path dir;
    @Test void defaultsNeverAssumeResourceIsOverworld()throws Exception {
        var c=Config.load(dir.resolve("svrtp.json"));assertEquals("",c.destinations.get("resource").dimension);
        assertEquals(3,c.destinations.size());assertFalse(c.performance.allowChunkGeneration);
    }
    @Test void allMappingsAreEditable() {
        var c=new Config();c.destinations.get("resource").dimension="minigamedim:minigame";
        c.destinations.get("nether").dimension="example:alternate_nether";c.destinations.get("end").dimension="example:alternate_end";
        var read=Config.JSON.fromJson(Config.JSON.toJson(c),Config.class);read.validate();assertEquals(c.destinations.get("nether").dimension,read.destinations.get("nether").dimension);
    }
    @Test void unsafeBudgetsAndRangesFailClosed() {
        var c=new Config();c.performance.maxConcurrentRequests=999;assertThrows(IllegalArgumentException.class,c::validate);
        c=new Config();c.destinations.get("end").minRadius=-1;assertThrows(IllegalArgumentException.class,c::validate);
        c=new Config();c.performance.allowChunkGeneration=true;assertThrows(IllegalArgumentException.class,c::validate);
    }
    @Test void transactionJournalPersistsAmbiguousState()throws Exception {
        var p=dir.resolve("journal.json");var journal=Journal.load(p);var player=java.util.UUID.randomUUID();
        journal.receipts.put(player,new Journal.Receipt("beastcoin","5"));journal.save();
        var loaded=Journal.load(p);assertEquals("prepared",loaded.receipts.get(player).state);
        assertEquals(journal.receipts.get(player).id,loaded.receipts.get(player).id);
    }
    @Test void corruptJournalIsNeverReplaced()throws Exception {
        var p=dir.resolve("journal.json");Files.writeString(p,"null");assertThrows(java.io.IOException.class,()->Journal.load(p));assertEquals("null",Files.readString(p));
    }
}
