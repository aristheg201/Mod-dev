package dev.svextract;

import dev.svextract.match.DefusalMatch;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefusalMatchTest {
    private final List<UUID> ids=java.util.stream.IntStream.range(0,10).mapToObj(i->new UUID(0,i+1)).toList();
    private DefusalMatch match(){var roster=new LinkedHashMap<UUID,Integer>();for(int i=0;i<10;i++)roster.put(ids.get(i),i<5?0:1);return new DefusalMatch(DefusalMatch.Rules.defaults(),roster,0);}
    @Test void buyingIsServerBudgetedAndOnlyInBuyPhase(){var m=match();assertTrue(m.purchase(ids.get(0),650));assertEquals(150,m.cash(ids.get(0)));assertFalse(m.purchase(ids.get(0),200));assertFalse(m.purchase(ids.get(0),-1));m.tick(300,null,-1,null);assertFalse(m.purchase(ids.get(0),0));}
    @Test void eliminateOnlyOnceAndRespawnAtNextRound(){var m=match();m.tick(300,null,-1,null);for(int i=0;i<5;i++){assertTrue(m.eliminate(ids.get(i),ids.get(5)));assertFalse(m.eliminate(ids.get(i),ids.get(5)));}m.tick(301,null,-1,null);assertEquals(1,m.score(1));assertEquals(DefusalMatch.Phase.ROUND_OVER,m.phase());assertFalse(m.alive(ids.get(0)));m.tick(401,null,-1,null);assertEquals(2,m.round());assertTrue(m.alive(ids.get(0)));}
    @Test void plantRequiresUninterruptedCarrierAtOneSite(){var m=match();m.tick(300,null,-1,null);m.tick(301,ids.get(1),0,null);m.tick(400,ids.get(1),0,null);assertFalse(m.planted());m.tick(401,ids.get(0),0,null);m.tick(450,null,-1,null);m.tick(451,ids.get(0),0,null);m.tick(510,ids.get(0),0,null);assertFalse(m.planted());m.tick(511,ids.get(0),0,null);assertTrue(m.planted());assertEquals(1100,m.cash(ids.get(0)));}
    @Test void plantedBombStillRequiresDefuseAfterAllAttackersDie(){var m=match();m.tick(300,null,-1,null);m.tick(301,ids.get(0),0,null);m.tick(361,ids.get(0),0,null);for(int i=0;i<5;i++)m.eliminate(ids.get(i),ids.get(5));m.tick(362,null,-1,null);assertEquals(DefusalMatch.Phase.LIVE,m.phase());m.tick(1161,null,-1,null);assertEquals(1,m.score(0));}
    @Test void defuseKitIsFasterAndInterruptedDefuseResets(){var m=match();m.giveKit(ids.get(5));m.tick(300,null,-1,null);m.tick(301,ids.get(0),0,null);m.tick(361,ids.get(0),0,null);m.tick(362,null,-1,ids.get(5));m.tick(450,null,-1,null);m.tick(451,null,-1,ids.get(5));m.tick(550,null,-1,ids.get(5));assertEquals(DefusalMatch.Phase.LIVE,m.phase());m.tick(551,null,-1,ids.get(5));assertEquals(1,m.score(1));}
    @Test void killingCarrierDropsBombAndOnlyAttackersCanPickUp(){var m=match();m.tick(300,null,-1,null);m.eliminate(ids.get(0),ids.get(5));assertNull(m.carrier());assertFalse(m.pickUpBomb(ids.get(5)));assertTrue(m.pickUpBomb(ids.get(1)));assertFalse(m.pickUpBomb(ids.get(2)));}
    @Test void defuseCompletingAtDetonationCannotBeatFuse(){var m=match();m.tick(300,null,-1,null);m.tick(301,ids.get(0),0,null);m.tick(361,ids.get(0),0,null);m.tick(961,null,-1,ids.get(5));m.tick(1161,null,-1,ids.get(5));assertEquals(1,m.score(0));}
    @Test void fixedTeamsSwapSidesAfterTwelveRoundsAndFinishAtThirteen(){var m=match();long now=0;for(int r=1;r<=13;r++){assertEquals(r,m.round());assertEquals(r<=12?0:1,m.attackingTeam());if(r==13)assertEquals(800,m.cash(ids.get(0)));now+=300;m.tick(now,null,-1,null);for(int i=5;i<10;i++)m.eliminate(ids.get(i),ids.get(0));m.tick(++now,null,-1,null);if(r<13){now+=100;m.tick(now,null,-1,null);}}assertEquals(DefusalMatch.Phase.FINISHED,m.phase());assertEquals(0,m.winner());assertEquals(13,m.score(0));}
}
