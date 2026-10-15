package dev.svextract;

import dev.svextract.persistence.RaidJournal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StashJournalTest {
    @TempDir Path dir;
    private RaidJournal open()throws Exception{return new RaidJournal(dir.resolve("stash.sqlite"),16,5,65536);}
    private static final String HUB="svextract:haven9";
    private static final RaidJournal.ReturnPoint HOME=new RaidJournal.ReturnPoint(HUB,0,65,4,180,0);
    @Test void transferCommitsOnlyAfterVerifiedSaveAndExactlyOnce()throws Exception{
        try(var journal=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] original={1},after={2},stored={3};
            journal.prepareStashTransfer(id,player,0,original,after,stored,HUB).join();
            assertEquals(0,journal.stash(player,new byte[]{0}).join().revision());assertEquals(1,journal.pending().join());
            CompletableFuture.allOf(journal.closeStashTransfer(id,()->{}),journal.closeStashTransfer(id,()->{fail("Closed replay must not verify another save");})).join();
            var stash=journal.stash(player,new byte[]{0}).join();assertEquals(1,stash.revision());assertArrayEquals(stored,stash.items());assertEquals(0,journal.pending().join());
            assertTrue(journal.liveStashTransfer(player).join().isEmpty());assertEquals(new RaidJournal.Progress(0,0,0),journal.progress(player).join());
        }
    }
    @Test void restartAfterPrepareRecoversDurableWholeInventoryAndStorage()throws Exception{
        UUID player=UUID.randomUUID(),id=UUID.randomUUID();
        try(var journal=open()){journal.prepareStashTransfer(id,player,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join();}
        try(var journal=open()){
            var transfer=journal.liveStashTransfer(player).join().orElseThrow();assertArrayEquals(new byte[]{2},transfer.afterInventory());assertEquals(HUB,transfer.dimension());
            journal.closeStashTransfer(transfer.id(),()->{}).join();assertEquals(1,journal.stash(player,new byte[]{0}).join().revision());
        }
        try(var journal=open()){assertTrue(journal.liveStashTransfer(player).join().isEmpty());assertArrayEquals(new byte[]{3},journal.stash(player,new byte[]{0}).join().items());}
    }
    @Test void failedSaveRetainsOriginalStashAndBlocksDeployment()throws Exception{
        try(var journal=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();journal.prepareStashTransfer(id,player,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join();
            assertThrows(CompletionException.class,()->journal.closeStashTransfer(id,()->{throw new java.io.IOException("disk full");}).join());
            assertEquals(0,journal.stash(player,new byte[]{0}).join().revision());assertTrue(journal.liveStashTransfer(player).join().isPresent());
            assertThrows(CompletionException.class,()->journal.reserve(UUID.randomUUID(),player,new byte[]{1},HOME,"operations").join());
            journal.closeStashTransfer(id,()->{}).join();journal.reserve(UUID.randomUUID(),player,new byte[]{2},HOME,"operations").join();
        }
    }
    @Test void cannotTransferDuringLiveRaidOrHaveTwoPendingTransfers()throws Exception{
        try(var journal=open()){
            UUID player=UUID.randomUUID();journal.reserve(UUID.randomUUID(),player,new byte[]{1},HOME).join();
            assertThrows(CompletionException.class,()->journal.prepareStashTransfer(UUID.randomUUID(),player,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join());
            UUID other=UUID.randomUUID();journal.prepareStashTransfer(UUID.randomUUID(),other,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join();
            assertThrows(CompletionException.class,()->journal.prepareStashTransfer(UUID.randomUUID(),other,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join());
        }
    }
    @Test void staleRevisionAndChangedReplayCannotMoveItems()throws Exception{
        try(var journal=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();journal.prepareStashTransfer(id,player,0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join();
            assertThrows(CompletionException.class,()->journal.prepareStashTransfer(id,player,0,new byte[]{1},new byte[]{9},new byte[]{3},HUB).join());
            assertThrows(CompletionException.class,()->journal.prepareStashTransfer(id,UUID.randomUUID(),0,new byte[]{1},new byte[]{2},new byte[]{3},HUB).join());
            journal.closeStashTransfer(id,()->{}).join();
            assertThrows(CompletionException.class,()->journal.prepareStashTransfer(UUID.randomUUID(),player,0,new byte[]{2},new byte[]{1},new byte[]{4},HUB).join());
            assertEquals(1,journal.stash(player,new byte[]{0}).join().revision());assertTrue(journal.liveStashTransfer(player).join().isEmpty());
        }
    }
    @Test void queuedTransferOwnsItsInputBytes()throws Exception{
        try(var journal=open()){
            byte[] before={1},after={2},stored={3};var prepared=journal.prepareStashTransfer(UUID.randomUUID(),UUID.randomUUID(),0,before,after,stored,HUB);
            before[0]=9;after[0]=9;stored[0]=9;var transfer=prepared.join();
            assertArrayEquals(new byte[]{1},transfer.beforeInventory());assertArrayEquals(new byte[]{2},transfer.afterInventory());assertArrayEquals(new byte[]{3},transfer.afterStash());
        }
    }
}
