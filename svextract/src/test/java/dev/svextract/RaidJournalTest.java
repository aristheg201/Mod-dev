package dev.svextract;

import dev.svextract.persistence.RaidJournal;
import dev.svextract.mission.RaidState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidJournalTest {
    @TempDir Path dir;
    private static final RaidJournal.ReturnPoint HOME=new RaidJournal.ReturnPoint("svextract:haven9",0,65,4,180,0);
    private RaidJournal open() throws Exception{return new RaidJournal(dir.resolve("test.sqlite"),16,5,65536);}
    @Test void concurrentSettlementAwardsExactlyOnce() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] data={1,2,3};
            j.reserve(id,player,data,HOME).join();j.activate(id).join();
            CompletableFuture.allOf(j.prepareSettlement(id,data,"EXTRACTED"),j.prepareSettlement(id,data,"EXTRACTED")).join();
            CompletableFuture.allOf(j.closeAfterPlayerSave(id),j.closeAfterPlayerSave(id),j.closeAfterPlayerSave(id)).join();
            assertEquals(new RaidJournal.Progress(1,1,1),j.progress(player).join());assertEquals(0,j.pending().join());
        }
    }
    @Test void operationsCheckpointSurvivesRestartWithoutRecreatingOriginalAmmunition() throws Exception{
        UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] original={17,3},spent={2,1};
        try(RaidJournal j=open()){
            j.reserve(id,player,original,HOME,"operations").join();j.activate(id).join();
            j.checkpoint(id,spent).join();
            assertThrows(CompletionException.class,()->j.reserve(id,player,original,HOME,"training").join());
        }
        try(RaidJournal j=open()){
            var recovered=j.live(player).join().orElseThrow();
            assertEquals("operations",recovered.mode());assertArrayEquals(original,recovered.reserved());assertArrayEquals(spent,recovered.checkpoint());
            j.prepareSettlement(id,spent,"RECOVERED").join();
            assertThrows(CompletionException.class,()->j.checkpoint(id,original).join());
            j.closeAfterPlayerSave(id).join();assertEquals(0,j.progress(player).join().extractions());
        }
    }
    @Test void operationsExtractionIsIdempotentAndDoesNotInventCurrency() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();
            j.reserve(id,player,new byte[]{1},HOME,"operations").join();j.activate(id).join();j.prepareSettlement(id,new byte[]{2},"EXTRACTED").join();
            CompletableFuture.allOf(j.closeAfterPlayerSave(id),j.closeAfterPlayerSave(id)).join();
            assertEquals(new RaidJournal.Progress(1,0,1),j.progress(player).join());
        }
    }
    @Test void versusResultDoesNotAwardExtractionProgress() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] data={3};
            j.reserve(id,player,data,HOME,"versus").join();j.activate(id).join();j.prepareSettlement(id,data,"WON").join();j.closeAfterPlayerSave(id).join();
            assertEquals(new RaidJournal.Progress(0,0,0),j.progress(player).join());
        }
    }
    @Test void samePlayerCannotReserveTwoLiveInventories() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID();
            j.reserve(UUID.randomUUID(),player,new byte[]{1},HOME).join();
            assertThrows(CompletionException.class,()->j.reserve(UUID.randomUUID(),player,new byte[]{2},HOME).join());
            assertEquals(1,j.pending().join());
        }
    }
    @Test void journalSurvivesRestartDuringExtraction() throws Exception{
        UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] data={7,8,9};
        try(RaidJournal j=open()){
            j.reserve(id,player,data,HOME).join();j.activate(id).join();j.prepareSettlement(id,data,"EXTRACTED").join();
        }
        try(RaidJournal j=open()){
            RaidJournal.Entry e=j.live(player).join().orElseThrow();assertEquals(RaidState.SETTLING,e.state());assertArrayEquals(data,e.settlement());
            j.closeAfterPlayerSave(id).join();j.closeAfterPlayerSave(id).join();assertEquals(1,j.progress(player).join().extractions());
        }
    }
    @Test void restartBeforeDeploymentCanRecoverReservedEquipmentWithoutReward() throws Exception{
        UUID player=UUID.randomUUID(),id=UUID.randomUUID();
        try(RaidJournal j=open()){j.reserve(id,player,new byte[]{42},HOME).join();}
        try(RaidJournal j=open()){
            RaidJournal.Entry e=j.live(player).join().orElseThrow();assertEquals(RaidState.RESERVED,e.state());
            j.prepareSettlement(id,e.reserved(),"RECOVERED").join();j.closeAfterPlayerSave(id).join();
            assertEquals(0,j.progress(player).join().extractions());assertTrue(j.live(player).join().isEmpty());
        }
    }
    @Test void replayCannotChangeItemOwnershipOrSettlement() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();
            j.reserve(id,player,new byte[]{1},HOME).join();
            assertThrows(CompletionException.class,()->j.reserve(id,UUID.randomUUID(),new byte[]{1},HOME).join());
            j.prepareSettlement(id,new byte[]{1},"EXTRACTED").join();
            assertThrows(CompletionException.class,()->j.prepareSettlement(id,new byte[]{2},"EXTRACTED").join());
            assertThrows(CompletionException.class,()->j.prepareSettlement(id,new byte[]{1},"RECOVERED").join());
        }
    }
    @Test void failedPlayerSaveKeepsSettlementPending() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();
            j.reserve(id,player,new byte[]{1},HOME).join();j.prepareSettlement(id,new byte[]{1},"EXTRACTED").join();
            assertThrows(CompletionException.class,()->j.closeVerified(id,()->{throw new java.io.IOException("disk full");}).join());
            assertEquals(RaidState.SETTLING,j.live(player).join().orElseThrow().state());assertEquals(0,j.progress(player).join().extractions());
            j.closeVerified(id,()->{}).join();assertEquals(1,j.progress(player).join().extractions());
        }
    }
    @Test void closingWithoutSettlementIsRejected() throws Exception{
        try(RaidJournal j=open()){
            UUID id=UUID.randomUUID();j.reserve(id,UUID.randomUUID(),new byte[]{1},HOME).join();
            assertThrows(CompletionException.class,()->j.closeAfterPlayerSave(id).join());assertEquals(1,j.pending().join());
        }
    }
    @Test void returnHubCoordinatesSurviveSettlementAndRestart() throws Exception {
        UUID player=UUID.randomUUID(),raid=UUID.randomUUID();
        var skyrim=new RaidJournal.ReturnPoint("svextract:skyrim",4834,97,4574,180,0);
        try(RaidJournal j=open()){j.reserve(raid,player,new byte[]{8},skyrim).join();j.activate(raid).join();j.prepareSettlement(raid,new byte[]{8},"EXTRACTED").join();}
        try(RaidJournal j=open()){assertEquals(skyrim,j.live(player).join().orElseThrow().home());}
    }
    @Test void snapshotInputMutationCannotChangeReservation() throws Exception{
        try(RaidJournal j=open()){
            UUID player=UUID.randomUUID(),id=UUID.randomUUID();byte[] snapshot={1,2,3};
            var future=j.reserve(id,player,snapshot,HOME);snapshot[0]=99;
            assertArrayEquals(new byte[]{1,2,3},future.join().reserved());
        }
    }
    @Test void oversizedInventoryFailsBeforeSchedulingIo() throws Exception{
        try(RaidJournal j=open()){
            assertThrows(IllegalArgumentException.class,()->j.reserve(UUID.randomUUID(),UUID.randomUUID(),new byte[65537],HOME));
            assertEquals(0,j.pending().join());
        }
    }
    @Test void queueHasBackpressureAndDoesNotDropAcceptedTransactions() throws Exception{
        try(RaidJournal j=new RaidJournal(dir.resolve("bounded.sqlite"),1,5,65536)){
            UUID id=UUID.randomUUID(),player=UUID.randomUUID();
            j.reserve(id,player,new byte[]{1},HOME).join();j.prepareSettlement(id,new byte[]{1},"EXTRACTED").join();
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            var blocked=j.closeVerified(id,()->{entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));});
            assertTrue(entered.await(3,TimeUnit.SECONDS));var queued=j.pending();var rejected=j.progress(player);
            assertThrows(CompletionException.class,rejected::join);release.countDown();blocked.join();
            assertEquals(0,queued.join());assertEquals(1,j.progress(player).join().extractions());
        }
    }
}
