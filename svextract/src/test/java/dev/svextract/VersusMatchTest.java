package dev.svextract;

import dev.svextract.match.VersusMatch;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VersusMatchTest {
    static List<VersusMatch.Seat> seats(){
        List<VersusMatch.Seat> roster=new ArrayList<>();
        for(int team=0;team<2;team++)for(int index=0;index<5;index++)roster.add(new VersusMatch.Seat(UUID.randomUUID(),team,index>0));
        return roster;
    }
    @Test void tdmScoresOnlyValidEnemyEliminations(){
        var s=seats();var rules=new VersusMatch.Rules(VersusMatch.Mode.TEAM_DEATHMATCH,5,2,600,1,5,20);
        var game=new VersusMatch(rules,s,0);game.tick(20);
        assertFalse(game.elimination(s.get(0).player(),s.get(1).player(),21));
        assertTrue(game.elimination(s.get(5).player(),s.get(0).player(),21));
        assertFalse(game.elimination(s.get(5).player(),s.get(0).player(),22));
        assertEquals(1,game.score(0));game.tick(121);
        assertTrue(game.elimination(s.get(5).player(),s.get(0).player(),122));
        assertEquals(0,game.winner());
        assertEquals(VersusMatch.Phase.FINISHED,game.phase());
    }
    @Test void dominationScoresHeldPointsOncePerSecond(){
        var game=new VersusMatch(VersusMatch.Rules.domination(),seats(),0);game.tick(200);
        assertTrue(game.capture(0,0));assertTrue(game.capture(1,0));assertTrue(game.capture(2,1));
        game.tick(220);assertEquals(2,game.score(0));assertEquals(1,game.score(1));
        game.tick(220);assertEquals(2,game.score(0));
        game.tick(240);assertEquals(4,game.score(0));
    }
}
