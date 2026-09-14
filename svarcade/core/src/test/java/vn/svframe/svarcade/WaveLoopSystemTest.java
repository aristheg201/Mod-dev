package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.wave.*;
import static org.junit.jupiter.api.Assertions.*;

class WaveLoopSystemTest {
    @Test void coordinatesDefinitionAuthoredWaveLifecycleWithoutGameDispatch() {
        Node states = new Node(Map.of("initial","PREPARE","data",Map.of(),"states",Map.of(
                "PREPARE",Map.of("transitions",List.of(Map.of("target","WAVE","event","start"))),
                "WAVE",Map.of("transitions",List.of(Map.of("target","WAVE_CLEAR","event","wave_cleared"))),
                "WAVE_CLEAR",Map.of("transitions",List.of(Map.of("target","SHOP","event","shop"))),
                "SHOP",Map.of("transitions",List.of(Map.of("target","COMPLETE","event","waves_complete"))),
                "COMPLETE",Map.of("terminal",true))),"states");
        Node waves = new Node(Map.of("repeat_from",-1,"max_spawns_per_tick",4,"max_pending_spawns",8,"waves",List.of(
                Map.of("id","test:w1","groups",List.of(Map.of("enemy","test:e","count",1,"interval_ticks",1,"lane","test:path","modifiers",List.of(),"tags",List.of()))))),"waves");
        Node loop = new Node(Map.of("wave_state","WAVE","clear_state","WAVE_CLEAR","acknowledge_state","SHOP","cleared_event","wave_cleared","complete_event","waves_complete"),"loop");
        StateMachineSystem.Plan fsmPlan = new StateMachineSystem.Plan(new Registry<>(Map.of()),new Registry<>(Map.of()));
        SystemCatalog catalog = SystemCatalog.builder().add(StateMachineSystem.ID,fsmPlan).add(WaveSystem.ID,new WaveSystem.Plan()).add(WaveLoopSystem.ID,new WaveLoopSystem.Plan()).build();
        Definition definition = new Definition(1,Id.of("test:wave_loop"),"fp",true,1,1,Set.of(),List.of(
                new Definition.SystemSpec(StateMachineSystem.ID,states),new Definition.SystemSpec(WaveSystem.ID,waves),new Definition.SystemSpec(WaveLoopSystem.ID,loop)),
                Map.of("arena",new Node(Map.of("id","arena"),"arena")));
        Participant player = new Participant(UUID.randomUUID(),Participant.Kind.PLAYER,"one");
        GenericSession session = new GenericSession(UUID.randomUUID(),definition,"arena",List.of(player),new ArenaRuntime(),new ThreadGuard()); session.start(catalog.factories());
        StateMachineAccess fsm=session.services().require(StateMachineAccess.ACCESS); WaveAccess wave=session.services().require(WaveAccess.ACCESS); assertTrue(fsm.event("start"));
        session.tick(1); assertTrue(wave.activeWaveIndex().isPresent()); session.tick(2); WaveAccess.Spawn spawn=wave.pendingSpawns(8).getFirst();
        wave.prepareSpawned(spawn.sequence()).apply(); wave.prepareResolved(spawn.sequence()).apply(); session.tick(3); assertEquals("WAVE_CLEAR",fsm.state());
        assertTrue(fsm.event("shop")); session.tick(4); assertEquals("COMPLETE",fsm.state()); assertTrue(fsm.terminal()); assertTrue(wave.complete()); session.close();
    }
}
