package dev.svextract;

import dev.svextract.match.Matchmaker;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchmakerTest {
    private List<Matchmaker.Member> party(int count,int level){List<Matchmaker.Member> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(new Matchmaker.Member(UUID.randomUUID(),level));return out;}
    @Test void versusNeverFillsWithBotsBeforeTwoMinutes(){
        AtomicLong now=new AtomicLong();var q=new Matchmaker(Matchmaker.Settings.defaults(),now::get);var player=party(1,10);q.join(Matchmaker.Mode.VERSUS,player);
        now.set(119_999_999_999L);assertTrue(q.poll(Matchmaker.Mode.VERSUS).isEmpty());assertEquals(1,q.queuedPlayers());
        now.incrementAndGet();var match=q.poll(Matchmaker.Mode.VERSUS).orElseThrow();assertEquals(2,match.teams().size());
        assertEquals(9,match.teams().stream().mapToInt(t->t.bots().size()).sum());assertEquals(5,match.teams().getFirst().players().size()+match.teams().getFirst().bots().size());
        assertEquals(player.getFirst().player(),match.teams().getFirst().players().getFirst());assertEquals(0,q.queuedPlayers());assertTrue(q.poll(Matchmaker.Mode.VERSUS).isEmpty());
    }
    @Test void fullRealVersusStartsEarlyAndKeepsPartiesTogether(){
        var q=new Matchmaker(Matchmaker.Settings.defaults(),()->0);var a=party(5,20);var b=party(5,22);q.join(Matchmaker.Mode.VERSUS,a);q.join(Matchmaker.Mode.VERSUS,b);
        var match=q.poll(Matchmaker.Mode.VERSUS).orElseThrow();assertTrue(match.teams().stream().allMatch(t->t.bots().isEmpty()));assertEquals(5,match.teams().getFirst().players().size());
        assertEquals(new HashSet<>(a.stream().map(Matchmaker.Member::player).toList()),new HashSet<>(match.teams().getFirst().players()));
    }
    @Test void battlegroundSquadBuildsSixtyExplicitCombatants(){
        AtomicLong now=new AtomicLong();var q=new Matchmaker(Matchmaker.Settings.defaults(),now::get);var a=party(4,10);q.join(Matchmaker.Mode.BATTLEGROUND_SQUAD,a);now.set(120_000_000_000L);
        var match=q.poll(Matchmaker.Mode.BATTLEGROUND_SQUAD).orElseThrow();assertEquals(15,match.teams().size());assertEquals(56,match.teams().stream().mapToInt(t->t.bots().size()).sum());
        assertEquals(a.stream().map(Matchmaker.Member::player).toList(),match.teams().getFirst().players());
        assertEquals(56,match.teams().stream().flatMap(t->t.bots().stream()).map(Matchmaker.Bot::id).distinct().count());
    }
    @Test void unsuitablePlayersRemainQueuedAndCannotAppearTwice(){
        AtomicLong now=new AtomicLong();var q=new Matchmaker(Matchmaker.Settings.defaults(),now::get);var low=party(1,1);var high=party(1,100);q.join(Matchmaker.Mode.VERSUS,low);q.join(Matchmaker.Mode.VERSUS,high);
        assertThrows(IllegalStateException.class,()->q.join(Matchmaker.Mode.BATTLEGROUND_SOLO,low));now.set(120_000_000_000L);
        var match=q.poll(Matchmaker.Mode.VERSUS).orElseThrow();assertEquals(1,match.teams().stream().mapToInt(t->t.players().size()).sum());assertEquals(1,q.queuedPlayers());assertTrue(q.leave(high.getFirst().player()));assertEquals(0,q.queuedPlayers());
    }
    @Test void disconnectDropsEntirePartyAndQueueIsBounded(){
        var q=new Matchmaker(Matchmaker.Settings.defaults(),()->0);var team=party(4,10);q.join(Matchmaker.Mode.BATTLEGROUND_SQUAD,team);assertTrue(q.leave(team.get(1).player()));assertEquals(0,q.queuedPlayers());
        for(int i=0;i<120;i++)q.join(Matchmaker.Mode.BATTLEGROUND_SOLO,party(1,10));assertThrows(IllegalStateException.class,()->q.join(Matchmaker.Mode.VERSUS,party(1,10)));q.clear();assertEquals(0,q.queuedPlayers());
    }
}
