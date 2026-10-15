package dev.svextract;

import dev.svextract.match.BattleRoyaleMatch;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleRoyaleMatchTest {
    @Test void zoneInterpolationDamageAndWinner(){
        var rules=BattleRoyaleMatch.Rules.defaults();
        var players=List.of(new BattleRoyaleMatch.Participant(UUID.randomUUID(),0,false),
                            new BattleRoyaleMatch.Participant(UUID.randomUUID(),1,true));
        var match=new BattleRoyaleMatch(rules,players,100);
        assertEquals(BattleRoyaleMatch.Phase.DEPLOYING,match.phase());
        assertFalse(match.eliminate(players.get(1).id(),null,101));
        match.tick(100+rules.deploymentSeconds()*20L);
        assertEquals(BattleRoyaleMatch.Phase.ACTIVE,match.phase());
        var zone=match.zone(100+rules.deploymentSeconds()*20L+rules.zones().getFirst().holdSeconds()*20L+100);
        assertTrue(zone.shrinking());
        assertTrue(zone.radius()<rules.zones().getFirst().radius());
        assertEquals(rules.outOfZoneDamage(),match.zoneDamage(1000,1000,600));
        assertTrue(match.eliminate(players.get(1).id(),players.get(0).id(),601));
        assertEquals(0,match.winner());
        assertEquals(BattleRoyaleMatch.Phase.FINISHED,match.phase());
        assertFalse(match.eliminate(players.get(1).id(),players.get(0).id(),602));
    }
    @Test void rejectsOverlappingPlayersAndUnsafeZoneRelocations(){
        UUID id=UUID.randomUUID();var p=new BattleRoyaleMatch.Participant(id,0,false);
        assertThrows(IllegalArgumentException.class,()->new BattleRoyaleMatch(BattleRoyaleMatch.Rules.defaults(),List.of(p,p),0));
        assertThrows(IllegalArgumentException.class,()->new BattleRoyaleMatch.Rules(16,4,10,900,2,20,
            List.of(new BattleRoyaleMatch.Zone(0,0,50,10,30),new BattleRoyaleMatch.Zone(50,0,40,10,30))));
    }
}
