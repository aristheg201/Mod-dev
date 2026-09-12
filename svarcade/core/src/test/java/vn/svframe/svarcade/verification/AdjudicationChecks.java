package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.AdjudicationAccess.Command;
import vn.svframe.svarcade.systems.board.MovementRules.Move;
import vn.svframe.svarcade.systems.objective.*;
import vn.svframe.svarcade.systems.turn.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class AdjudicationChecks {
    private AdjudicationChecks() { }
    private static final class Fixture implements AutoCloseable {
        final AtomicLong time=new AtomicLong();
        final ArenaRuntime arenas=new ArenaRuntime();
        final UUID white=UUID.randomUUID(),black=UUID.randomUUID();
        final List<Participant> people=List.of(new Participant(white,Participant.Kind.PLAYER,"white"),new Participant(black,Participant.Kind.BOT,"black"));
        final Registry<SystemFactory> factories;
        final Definition definition;
        GenericSession session;
        BoardAccess board;
        TurnAccess clock;
        ObjectiveAccess objective;
        AdjudicationAccess adjudication;
        ActionDispatcher dispatcher;
        BoardMoveHandler moveHandler;
        UUID token;
        long sequence;
        Fixture(GridPosition position) {
            Node clocks=new Node(Map.of("banks",Map.of("white",Map.of("initial_ms",1000,"increment_ms",10,"maximum_ms",10000),"black",Map.of("initial_ms",1000,"increment_ms",10,"maximum_ms",10000)),
                    "initial_team",position.turn(),"starts_paused",false,"enabled",true,"persistence_interval_ms",100),"clocks");
            Node outcomes=new Node(Map.of("counters",Map.of(),"reasons",AdjudicationFixtures.config().reasons().values().stream().map(Id::toString).distinct().toList()),"objectives");
            factories=new Registry<>(Map.of(MovementSystem.ID,new MovementSystem.Plan(),BoardSystem.ID,new BoardSystem.Plan(),TurnSystem.ID,new TurnSystem.Plan(time::get),ObjectiveSystem.ID,new ObjectiveSystem.Plan(),BoardAdjudicationSystem.ID,new BoardAdjudicationSystem.Plan()));
            definition=new Definition(1,id("adjudicated"),"v1",true,2,2,Set.of(),List.of(new Definition.SystemSpec(MovementSystem.ID,BoardFixtures.data(standard())),
                    new Definition.SystemSpec(BoardSystem.ID,new Node(Map.of("initial",position.encode(),"history_limit",1000),"board")),new Definition.SystemSpec(TurnSystem.ID,clocks),
                    new Definition.SystemSpec(ObjectiveSystem.ID,outcomes),new Definition.SystemSpec(BoardAdjudicationSystem.ID,AdjudicationFixtures.data())),Map.of("one",new Node(Map.of(),"arena")));
            session=new GenericSession(UUID.randomUUID(),definition,"one",people,arenas,new ThreadGuard()); session.start(factories); bind();
        }
        void bind() {
            board=session.services().require(BoardSystem.ACCESS); clock=session.services().require(TurnSystem.ACCESS); objective=session.services().require(ObjectiveSystem.ACCESS); adjudication=session.services().require(BoardAdjudicationSystem.ACCESS);
            moveHandler=new BoardMoveHandler(board,clock,adjudication,() -> true,id("moved"));
            Map<Id,ActionDispatcher.Handler> handlers=new LinkedHashMap<>(); handlers.put(id("move"),moveHandler);
            for (Command command : Command.values()) handlers.put(id(command.name().toLowerCase(Locale.ROOT)),new BoardCommandHandler(adjudication,command,board,() -> true,id("command")));
            dispatcher=new ActionDispatcher(session,arenas,new RateLimiter(4,1000,1),new Registry<>(handlers),10,100);
            token=dispatcher.issueController(white,10000); dispatcher.issueController(black,10000); sequence=0;
        }
        IntentGate.Result send(String team,Id action,Map<String,Object> payload) {
            UUID actor=team.equals("white") ? white : black; IntentGate.Facts facts=new IntentGate.Facts(actor,1,0,true);
            IntentGate.Result result=actor.equals(white) ? dispatcher.dispatchHuman(facts,new IntentGate.Intent(session.id(),token,sequence++,session.revision(),action,payload))
                    : dispatcher.dispatchBot(facts,session.id(),session.revision(),new BotRuntime.Decision(action,payload));
            dispatcher.drainEvents(100); return result;
        }
        void play(String from,String to) { equal(true,send(board.position().turn(),id("move"),Map.of("from",square(from),"to",square(to))).accepted()); }
        IntentGate.Result command(String team,Command command,Optional<Move> intended) {
            return send(team,id(command.name().toLowerCase(Locale.ROOT)),intended.isEmpty() ? Map.of() : Map.of("move",BoardMoveHandler.encode(intended.get())));
        }
        void cycle() { play("g1","f3");play("g8","f6");play("f3","g1");play("f6","g8"); }
        void restart() {
            Map<Id,GenericSession.SystemState> saved=session.snapshot(); UUID identity=session.id(); session.close();
            session=new GenericSession(identity,definition,"one",people,arenas,new ThreadGuard()); session.restore(factories,saved); bind();
        }
        @Override public void close() { session.close(); equal(0,arenas.snapshot().size()); }
    }
    public static void main(String[] ignored) {
        try (Fixture f=new Fixture(start())) {
            equal(false,f.command("white",Command.CLAIM_REPETITION,Optional.empty()).accepted());
            f.cycle(); f.cycle(); equal(3,f.board.repetitions()); equal(Optional.empty(),f.objective.result());
            equal(true,f.command("white",Command.CLAIM_REPETITION,Optional.empty()).accepted()); equal(id("repetition_claim"),f.objective.result().orElseThrow().reason()); equal(false,f.clock.running());
            equal(false,f.send("white",id("move"),Map.of("from",square("e2"),"to",square("e4"))).accepted());
            f.restart(); equal(id("repetition_claim"),f.objective.result().orElseThrow().reason()); equal(false,f.clock.running()); equal(8,f.board.archive().moves().size());
        }
        try (Fixture f=new Fixture(start())) {
            f.cycle();f.play("g1","f3");f.play("g8","f6");f.play("f3","g1");
            Map<String,Object> before=f.board.position().encode();
            equal(true,f.command("black",Command.CLAIM_REPETITION,Optional.of(new Move(square("f6"),square("g8"),null,null))).accepted());
            equal(before,f.board.position().encode());equal(7,f.board.archive().moves().size());equal(id("repetition_claim"),f.objective.result().orElseThrow().reason());
        }
        try (Fixture f=new Fixture(start())) { for(int i=0;i<4;i++) f.cycle(); equal(id("repetition_auto"),f.objective.result().orElseThrow().reason());equal(false,f.clock.running()); }
        try (Fixture f=new Fixture(fen("7k/8/8/8/8/8/8/R6K w - - 100 51"))) {
            equal(Optional.empty(),f.objective.result()); equal(true,f.command("white",Command.CLAIM_QUIET,Optional.empty()).accepted()); equal(id("quiet_claim"),f.objective.result().orElseThrow().reason());
        }
        try (Fixture f=new Fixture(fen("7k/8/8/8/8/8/8/R6K w - - 99 51"))) {
            equal(false,f.command("black",Command.CLAIM_QUIET,Optional.empty()).accepted());
            equal(false,f.command("white",Command.CLAIM_QUIET,Optional.empty()).accepted());
            equal(true,f.command("white",Command.CLAIM_QUIET,Optional.of(new Move(square("a1"),square("a2"),null,null))).accepted());
            equal(99L,f.board.position().quietPlies()); equal(0,f.board.archive().moves().size());
        }
        try (Fixture f=new Fixture(fen("7k/5K2/6Q1/8/8/8/8/8 w - - 149 76"))) {
            f.play("g6","g7"); equal(id("immobile_threatened"),f.objective.result().orElseThrow().reason()); equal(Set.of("white"),f.objective.result().orElseThrow().winners()); equal(false,f.clock.running());
        }
        try (Fixture f=new Fixture(start())) {
            equal(true,f.command("white",Command.OFFER_DRAW,Optional.empty()).accepted());
            equal(false,f.command("white",Command.ACCEPT_DRAW,Optional.empty()).accepted());
            f.play("e2","e4");equal(Optional.of("white"),f.adjudication.offeredBy());f.restart();equal(Optional.of("white"),f.adjudication.offeredBy());
            equal(true,f.command("black",Command.ACCEPT_DRAW,Optional.empty()).accepted()); equal(id("agreement"),f.objective.result().orElseThrow().reason());equal(Optional.empty(),f.adjudication.offeredBy());
        }
        try (Fixture f=new Fixture(start())) {
            equal(true,f.command("white",Command.OFFER_DRAW,Optional.empty()).accepted()); equal(true,f.command("black",Command.DECLINE_DRAW,Optional.empty()).accepted()); equal(Optional.empty(),f.adjudication.offeredBy());
            equal(true,f.command("white",Command.OFFER_DRAW,Optional.empty()).accepted()); f.play("e2","e4");f.play("e7","e5");f.play("g1","f3");
            equal(Optional.empty(),f.adjudication.offeredBy());equal(false,f.command("black",Command.ACCEPT_DRAW,Optional.empty()).accepted());
            equal(true,f.command("white",Command.RESIGN,Optional.empty()).accepted());equal(Set.of("black"),f.objective.result().orElseThrow().winners());
        }
        try (Fixture f=new Fixture(fen("7k/p7/8/8/8/8/8/7K b - - 0 1"))) {
            f.time.set(2_000_000_000L); equal(false,f.command("white",Command.OFFER_DRAW,Optional.empty()).accepted());f.session.tick(1);
            equal(id("timeout_draw"),f.objective.result().orElseThrow().reason());equal(false,f.clock.running());
        }
        try (Fixture f=new Fixture(fen("7k/p7/8/8/8/8/8/7K b - - 0 1"))) { equal(true,f.command("black",Command.RESIGN,Optional.empty()).accepted());equal(id("resign_draw"),f.objective.result().orElseThrow().reason()); }
        try (Fixture f=new Fixture(start())) { f.time.set(2_000_000_000L);f.session.tick(1);equal(id("timeout"),f.objective.result().orElseThrow().reason());equal(Set.of("black"),f.objective.result().orElseThrow().winners()); }
        try (Fixture f=new Fixture(fen("7k/8/8/8/8/8/8/7K w - - 0 1"))) { equal(id("material"),f.objective.result().orElseThrow().reason());equal(false,f.clock.running()); }
        try (Fixture f=new Fixture(start())) {
            Map<String,Object> before=f.board.position().encode();
            ActionDispatcher.Prepared prepared=f.moveHandler.prepare(f.session,new IntentGate.Facts(f.white,1,0,true),new IntentGate.Intent(f.session.id(),f.token,0,f.session.revision(),id("move"),Map.of("from",square("e2"),"to",square("e4"))));
            f.time.set(2_000_000_000L);rejects(IllegalStateException.class,() -> prepared.apply().run());prepared.rollback().run();equal(before,f.board.position().encode());equal(0L,f.board.revision());equal(Optional.empty(),f.objective.result());
            f.session.tick(1);equal(id("timeout"),f.objective.result().orElseThrow().reason());
        }
        System.out.println("AdjudicationChecks: PASS (real shared ingress, current/intended claims, automatic repetition, mate priority, offers across moves/restart, resignation, timeout material, atomic result/clock rollback)");
    }
}
