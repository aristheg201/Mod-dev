package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.objective.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class ObjectiveChecks {
    private ObjectiveChecks() { }
    public static void main(String[] ignored) {
        Id health = Id.of("test:health"), score = Id.of("test:score"), win = Id.of("test:win"), loss = Id.of("test:loss"), draw = Id.of("test:draw");
        ObjectiveSystem.Config config = new ObjectiveSystem.Config(Map.of(health,new ObjectiveSystem.Counter(100,0,100,true),score,new ObjectiveSystem.Counter(0,0,1000,false)),Set.of(win,loss,draw));
        ObjectiveSystem objectives = new ObjectiveSystem(config,Set.of("a","b"),new ThreadGuard()); objectives.start();
        StateChange damage = objectives.prepare(Map.of(health,-25L,score,10L),Optional.empty());
        equal(100L,objectives.value(health)); damage.apply(); equal(75L,objectives.value(health)); equal(10L,objectives.value(score));
        damage.rollback(); equal(100L,objectives.value(health)); equal(0L,objectives.revision()); rejects(IllegalStateException.class,damage::apply);
        rejects(IllegalArgumentException.class,() -> objectives.prepare(Map.of(health,-1L,score,-1L),Optional.empty())); equal(100L,objectives.value(health));
        StateChange early = objectives.prepare(Map.of(score,1L),Optional.empty()); objectives.prepare(Map.of(health,Long.MIN_VALUE),Optional.empty()).apply();
        equal(0L,objectives.value(health)); rejects(IllegalStateException.class,early::apply);
        objectives.prepare(Map.of(health,Long.MAX_VALUE),Optional.empty()).apply(); equal(100L,objectives.value(health));
        objectives.prepare(Map.of(health,Long.MAX_VALUE),Optional.empty()).apply(); equal(100L,objectives.value(health));
        rejects(IllegalArgumentException.class,() -> objectives.prepare(Map.of(),Optional.of(new ObjectiveAccess.Result(win,Set.of("outsider"),false))));
        rejects(IllegalArgumentException.class,() -> new ObjectiveAccess.Result(draw,Set.of("a"),true));
        ObjectiveAccess.Result result = new ObjectiveAccess.Result(win,Set.of("a"),false);
        StateChange finish = objectives.prepare(Map.of(score,100L),Optional.of(result)); finish.apply(); equal(Optional.of(result),objectives.result());
        rejects(IllegalStateException.class,() -> objectives.prepare(Map.of(score,1L),Optional.empty()));
        Map<String,Object> saved = objectives.snapshot(); ObjectiveSystem recovered = new ObjectiveSystem(config,Set.of("a","b"),new ThreadGuard()); recovered.restore(1,saved); equal(saved,recovered.snapshot());
        finish.rollback(); equal(Optional.empty(),objectives.result()); equal(0L,objectives.value(score));
        objectives.prepare(Map.of(),Optional.of(new ObjectiveAccess.Result(loss,Set.of(),false))).apply(); equal(false,objectives.result().orElseThrow().draw());
        Node n = new Node(Map.of("counters",Map.of(health.toString(),Map.of("initial",100,"minimum",0,"maximum",100,"clamp",true)),"reasons",List.of(win.toString(),loss.toString(),draw.toString())),"objectives");
        ObjectiveSystem.Plan plan = new ObjectiveSystem.Plan(); plan.validate(n);
        Definition definition = new Definition(1,Id.of("test:game"),"v1",true,1,1,Set.of(),List.of(new Definition.SystemSpec(ObjectiveSystem.ID,n)),Map.of("arena",new Node(Map.of(),"arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(),definition,"arena",List.of(new Participant(UUID.randomUUID(),Participant.Kind.PLAYER,"a")),arenas,new ThreadGuard()); session.start(new Registry<>(Map.of(ObjectiveSystem.ID,plan)));
        ObjectiveAccess access = session.services().require(ObjectiveSystem.ACCESS); access.prepare(Map.of(health,-100L),Optional.of(new ObjectiveAccess.Result(loss,Set.of(),false))).apply(); equal(0L,access.value(health));
        equal(1,session.snapshot().size()); session.close(); equal(0,arenas.snapshot().size()); objectives.close(); recovered.close();
        rejects(IllegalStateException.class,objectives::start);
        System.out.println("ObjectiveChecks: PASS (bounded/clamped counters, atomic outcome, stale/rollback checks, co-op loss, valid winners, schema restore, actual composition)");
    }
}
