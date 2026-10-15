package dev.svextract;

import dev.svextract.match.LobbyParties;
import dev.svextract.persistence.RaidJournal;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LobbyPartiesTest {
    @TempDir Path directory;
    @Test void invitesExpireAndMembershipPublishesOnlyAfterCommit(){
        LobbyParties lobby=new LobbyParties();UUID a=UUID.randomUUID(),b=UUID.randomUUID();lobby.invite(a,b,0);
        var proposal=lobby.accept(b,119_999_999_999L);assertTrue(lobby.squad(b).isEmpty());assertEquals(2,proposal.squads().getFirst().members().size());
        assertThrows(IllegalStateException.class,()->lobby.accept(b,120_000_000_000L));assertTrue(lobby.squad(b).isEmpty());
        lobby.committed(new RaidJournal.Lobby(1,proposal.squads()),b);assertTrue(lobby.squad(b).isPresent());
        assertThrows(IllegalArgumentException.class,()->lobby.invite(a,b,130_000_000_000L));
    }
    @Test void membershipSurvivesReopenAndStaleWriteRollsBack() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();LobbyParties lobby=new LobbyParties();lobby.invite(a,b,0);var proposal=lobby.accept(b,1);
        Path db=directory.resolve("journal.sqlite");
        try(RaidJournal journal=new RaidJournal(db,8,5,65536)){
            var committed=journal.saveLobby(proposal).get();lobby.committed(committed,b);assertEquals(1,committed.revision());
            assertThrows(Exception.class,()->journal.saveLobby(proposal).get());assertEquals(committed,journal.lobby().get());
        }
        try(RaidJournal journal=new RaidJournal(db,8,5,65536)){
            LobbyParties restored=new LobbyParties();restored.restore(journal.lobby().get());assertEquals(lobby.squad(a),restored.squad(a));
            var changed=journal.saveLobby(restored.leave(a)).get();restored.committed(changed,a);assertEquals(b,restored.squad(b).orElseThrow().leader());assertTrue(restored.squad(a).isEmpty());
        }
    }
    @Test void duplicateCrossSquadOwnershipIsRejectedBeforePersistence(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        try(RaidJournal journal=new RaidJournal(directory.resolve("journal.sqlite"),8,5,65536)){
            var invalid=new RaidJournal.Lobby(0,List.of(new RaidJournal.Squad(UUID.randomUUID(),a,List.of(a,b)),new RaidJournal.Squad(UUID.randomUUID(),b,List.of(b))));
            assertThrows(IllegalArgumentException.class,()->journal.saveLobby(invalid));assertEquals(0,journal.lobby().get().revision());
        }catch(Exception e){throw new AssertionError(e);}
    }
}
