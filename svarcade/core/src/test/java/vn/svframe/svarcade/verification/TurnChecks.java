package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.turn.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class TurnChecks {
    private TurnChecks() { }
    public static void main(String[] ignored) {
        AtomicLong time = new AtomicLong(); AtomicInteger dirty = new AtomicInteger(); ThreadGuard thread = new ThreadGuard();
        TurnSystem.Config config = new TurnSystem.Config(Map.of("white",new TurnSystem.Bank(100,10,1000),"black",new TurnSystem.Bank(100,10,1000)),"white",true,true,10);
        TurnSystem clocks = new TurnSystem(config,time::get,thread,dirty::incrementAndGet); clocks.start();
        time.set(50); equal(100L,clocks.remainingNanos("white")); clocks.prepareRunning(true).apply();
        time.set(70); equal(80L,clocks.remainingNanos("white")); StateChange pass = clocks.preparePass("white","black");
        time.set(75); pass.apply(); equal(85L,clocks.remainingNanos("white")); equal(100L,clocks.remainingNanos("black"));
        time.set(90); equal(85L,clocks.remainingNanos("black")); pass.rollback(); equal(60L,clocks.remainingNanos("white")); equal("white",clocks.team());
        rejects(IllegalStateException.class,pass::apply); clocks.preparePass("white","black").apply(); time.set(95);
        Map<String,Object> saved = clocks.snapshot(); equal(70L,clocks.remainingNanos("white")); equal(95L,clocks.remainingNanos("black"));
        AtomicLong restoredTime = new AtomicLong(1000); TurnSystem restored = new TurnSystem(config,restoredTime::get,thread,dirty::incrementAndGet); restored.restore(1,saved);
        equal(saved,restored.snapshot()); restoredTime.set(1007); equal(88L,restored.remainingNanos("black")); StateChange pending = restored.preparePass("black","white");
        restoredTime.set(1100); rejects(IllegalStateException.class,pending::apply); equal(true,restored.expired()); equal("black",restored.team());
        restored.tick(1); equal(1,dirty.get()); restored.tick(2); equal(1,dirty.get());
        restoredTime.set(1099); rejects(IllegalArgumentException.class,() -> restored.remainingNanos("black"));
        AtomicLong wrapping = new AtomicLong(Long.MAX_VALUE-2); TurnSystem wrapped = new TurnSystem(new TurnSystem.Config(config.banks(),"white",true,false,10),wrapping::get,thread,() -> { }); wrapped.start();
        wrapping.set(Long.MIN_VALUE+7); equal(90L,wrapped.remainingNanos("white"));
        AtomicLong disabledTime = new AtomicLong(); TurnSystem disabled = new TurnSystem(new TurnSystem.Config(config.banks(),"white",false,false,10),disabledTime::get,thread,() -> { }); disabled.start();
        disabledTime.set(1_000_000); equal(false,disabled.expired()); equal(100L,disabled.remainingNanos("white")); disabled.preparePass("white","black").apply(); equal(100L,disabled.remainingNanos("white"));
        AtomicInteger counter = new AtomicInteger(); CompositeChange composite = new CompositeChange(thread,List.of(new StateChange() {
            public void apply() { counter.incrementAndGet(); }
            public void rollback() { counter.decrementAndGet(); }
        },new StateChange() {
            public void apply() { throw new IllegalStateException("injected"); }
            public void rollback() { throw new AssertionError("Unapplied part must not be rolled back"); }
        }));
        rejects(IllegalStateException.class,composite::apply); equal(1,counter.get()); composite.rollback(); equal(0,counter.get()); rejects(IllegalStateException.class,composite::rollback);
        integration(); clocks.close(); restored.close(); wrapped.close(); disabled.close(); rejects(IllegalStateException.class,clocks::start);
        System.out.println("TurnChecks: PASS (monotonic banks/increment, commit-time expiry, pause/resume, rollback elapsed time, signed clock wrap, recovery, real board+clock atomic ingress, persistence dirty version)");
    }
    private static void integration() {
        AtomicLong time = new AtomicLong(); UUID white = UUID.randomUUID(), black = UUID.randomUUID();
        List<Participant> people = List.of(new Participant(white,Participant.Kind.PLAYER,"white"),new Participant(black,Participant.Kind.BOT,"black"));
        Node clock = new Node(Map.of("banks",Map.of("white",Map.of("initial_ms",1000,"increment_ms",100,"maximum_ms",5000),"black",Map.of("initial_ms",1000,"increment_ms",100,"maximum_ms",5000)),
                "initial_team","white","enabled",true,"starts_paused",false,"persistence_interval_ms",100),"clock");
        Registry<SystemFactory> factories = new Registry<>(Map.of(MovementSystem.ID,new MovementSystem.Plan(),BoardSystem.ID,new BoardSystem.Plan(),TurnSystem.ID,new TurnSystem.Plan(time::get)));
        Definition definition = new Definition(1,id("timed"),"v1",true,2,2,Set.of(),List.of(new Definition.SystemSpec(MovementSystem.ID,data(standard())),
                new Definition.SystemSpec(TurnSystem.ID,clock),new Definition.SystemSpec(BoardSystem.ID,new Node(Map.of("initial",start().encode(),"history_limit",100),"board"))),Map.of("one",new Node(Map.of(),"arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(),definition,"one",people,arenas,new ThreadGuard()); session.start(factories);
        BoardAccess board = session.services().require(BoardSystem.ACCESS); TurnAccess turns = session.services().require(TurnSystem.ACCESS); Id action = id("move");
        BoardMoveHandler handler = new BoardMoveHandler(board,turns,() -> true,id("moved"));
        ActionDispatcher dispatcher = new ActionDispatcher(session,arenas,new RateLimiter(4,100,1),new Registry<>(Map.of(action,handler)),10,10);
        UUID token = dispatcher.issueController(white,1000); dispatcher.issueController(black,1000); IntentGate.Facts human = new IntentGate.Facts(white,1,0,true), bot = new IntentGate.Facts(black,1,0,true);
        time.set(400_000_000L);
        equal(true,dispatcher.dispatchHuman(human,new IntentGate.Intent(session.id(),token,0,session.revision(),action,Map.of("from",square("e2"),"to",square("e4")))).accepted());
        equal("black",turns.team()); equal(board.position().turn(),turns.team()); equal(700_000_000L,turns.remainingNanos("white"));
        long revision = session.revision(), dirty = session.dirtyVersion(); time.set(600_000_000L); session.tick(1);
        equal(revision,session.revision()); equal(dirty+1,session.dirtyVersion());
        equal(true,dispatcher.dispatchBot(bot,session.id(),session.revision(),new BotRuntime.Decision(action,Map.of("from",square("e7"),"to",square("e5")))).accepted());
        equal(900_000_000L,turns.remainingNanos("black"));
        ActionDispatcher.Prepared pending = handler.prepare(session,human,new IntentGate.Intent(session.id(),token,1,session.revision(),action,Map.of("from",square("g1"),"to",square("f3"))));
        Map<String,Object> before = board.position().encode(); time.set(2_000_000_000L); rejects(IllegalStateException.class,() -> pending.apply().run()); pending.rollback().run();
        equal(before,board.position().encode()); equal(2L,board.revision()); equal("white",turns.team());
        equal("clock",dispatcher.dispatchHuman(human,new IntentGate.Intent(session.id(),token,1,session.revision(),action,Map.of("from",square("g1"),"to",square("f3")))).reason());
        session.tick(2); Map<Id,GenericSession.SystemState> saved = session.snapshot(); session.close(); equal(0,arenas.snapshot().size());
        time.set(5_000_000_000L); GenericSession recovered = new GenericSession(UUID.randomUUID(),definition,"one",people,arenas,new ThreadGuard()); recovered.restore(factories,saved);
        equal(true,recovered.services().require(TurnSystem.ACCESS).expired()); equal(900_000_000L,recovered.services().require(TurnSystem.ACCESS).remainingNanos("black")); recovered.close();
    }
}
