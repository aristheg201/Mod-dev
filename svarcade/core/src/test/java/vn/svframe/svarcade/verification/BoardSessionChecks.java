package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.MovementRules.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class BoardSessionChecks {
    private BoardSessionChecks() { }
    private static IntentGate.Intent intent(GenericSession s, UUID token, long sequence, Id action, String from, String to) {
        return new IntentGate.Intent(s.id(), token, sequence, s.revision(), action, Map.of("from", square(from), "to", square(to)));
    }
    public static void main(String[] ignored) {
        UUID white = UUID.randomUUID(), black = UUID.randomUUID();
        List<Participant> participants = List.of(new Participant(white, Participant.Kind.PLAYER,"white"),new Participant(black,Participant.Kind.BOT,"black"));
        Node movement = data(standard()), board = new Node(Map.of("initial", start().encode(), "history_limit", 30_000),"board");
        Registry<SystemFactory> factories = new Registry<>(Map.of(MovementSystem.ID,new MovementSystem.Plan(),BoardSystem.ID,new BoardSystem.Plan()));
        Definition d = new Definition(1,id("board_session"),"definition-v1",true,2,2,Set.of(),
                List.of(new Definition.SystemSpec(MovementSystem.ID,movement),new Definition.SystemSpec(BoardSystem.ID,board)),Map.of("one",new Node(Map.of(),"arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(),d,"one",participants,arenas,new ThreadGuard()); session.start(factories);
        BoardAccess access = session.services().require(BoardSystem.ACCESS); Id action = id("move"); AtomicBoolean phase = new AtomicBoolean();
        ActionDispatcher dispatcher = new ActionDispatcher(session,arenas,new RateLimiter(8,100,1),new Registry<>(Map.of(action,new BoardMoveHandler(access,phase::get,id("moved")))),10,2);
        UUID token = dispatcher.issueController(white,1000); dispatcher.issueController(black,1000);
        IntentGate.Facts human = new IntentGate.Facts(white,1,0,true), bot = new IntentGate.Facts(black,1,0,true);
        equal("phase",dispatcher.dispatchHuman(human,intent(session,token,0,action,"e2","e4")).reason()); equal(0L,access.revision());
        phase.set(true); long firstRevision = session.revision();
        equal(false,dispatcher.dispatchHuman(human,intent(session,token,0,action,"a1","a4")).accepted()); equal(firstRevision,session.revision()); equal(0L,access.revision());
        IntentGate.Intent opening = intent(session,token,1,action,"e2","e4"); equal(true,dispatcher.dispatchHuman(human,opening).accepted());
        equal("replay",dispatcher.dispatchHuman(human,opening).reason()); equal(1L,access.revision());
        equal(true,dispatcher.dispatchBot(bot,session.id(),session.revision(),new BotRuntime.Decision(action,Map.of("from",square("e7"),"to",square("e5")))).accepted());
        equal(2,access.history(0,256).size()); equal(firstRevision+2,session.revision());
        equal(false,dispatcher.dispatchHuman(human,intent(session,token,2,action,"e5","e4")).accepted());
        equal("event_backpressure",dispatcher.dispatchHuman(human,intent(session,token,3,action,"g1","f3")).reason()); equal(2L,access.revision());
        equal(1,dispatcher.drainEvents(1).size()); equal(true,dispatcher.dispatchHuman(human,intent(session,token,4,action,"g1","f3")).accepted()); equal(3L,access.revision());
        StateChange rollback = access.prepareMove(black,new Move(square("b8"),square("c6"),null,null));
        Map<String,Object> before = access.position().encode(); rollback.apply(); equal(4L,access.revision()); rollback.rollback(); equal(before,access.position().encode()); equal(3L,access.revision());
        rejects(IllegalStateException.class,rollback::apply); equal(3,access.archive().moves().size());
        BoardAccess.Archive archive = access.archive(); GridPosition replay = CompletableFuture.supplyAsync(() -> BoardSystem.replay(archive,new MovementRules(standard()),archive.moves().size(),() -> { })).join();
        equal(before,replay.encode());
        Map<Id,GenericSession.SystemState> saved = session.snapshot(); session.close();
        equal(Map.of("events",0,"bot_controllers",0,"grants",0),dispatcher.ownedCounts()); equal(0,arenas.snapshot().size());
        equal("session",dispatcher.dispatchBot(bot,session.id(),session.revision(),new BotRuntime.Decision(action,Map.of())) .reason());
        GenericSession restored = new GenericSession(UUID.randomUUID(),d,"one",participants,arenas,new ThreadGuard()); restored.restore(factories,saved);
        BoardAccess restoredBoard = restored.services().require(BoardSystem.ACCESS); equal(before,restoredBoard.position().encode()); equal(3,restoredBoard.history(0,256).size());
        equal(saved.get(BoardSystem.ID),restored.snapshot().get(BoardSystem.ID)); restored.close();
        Map<String,Object> damaged = new LinkedHashMap<>(saved.get(BoardSystem.ID).data()); damaged.put("history",List.of());
        Map<Id,GenericSession.SystemState> bad = new LinkedHashMap<>(saved); bad.put(BoardSystem.ID,new GenericSession.SystemState(1,damaged));
        GenericSession rejected = new GenericSession(UUID.randomUUID(),d,"one",participants,arenas,new ThreadGuard());
        rejects(ConfigException.class,() -> rejected.restore(factories,bad)); equal(GenericSession.Status.CLOSED,rejected.status()); equal(0,arenas.snapshot().size());
        System.out.println("BoardSessionChecks: PASS (typed composition, real human/bot dispatcher, phase/turn/legal guards, event backpressure, history/replay, atomic rollback/restore, owned cleanup)");
    }
}
