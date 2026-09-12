package vn.svframe.svarcade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTest {
    static final Id ACTION = Id.of("test:spend");
    final UUID actor = UUID.randomUUID();
    final ArenaRuntime arenas = new ArenaRuntime();
    GenericSession session;
    ActionDispatcher dispatcher;
    UUID token;
    long sequence;
    void setup(Participant.Kind kind, ActionDispatcher.Handler handler, int eventCapacity) {
        session = new GenericSession(UUID.randomUUID(), RuntimeTest.definition(), "one", List.of(new Participant(actor, kind, "team")), arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(RuntimeTest.SYSTEM, (s, c) -> new RuntimeTest.Stub())));
        dispatcher = new ActionDispatcher(session, arenas, new RateLimiter(8, 100, 1), new Registry<>(Map.of(ACTION, handler)), 10, eventCapacity);
        token = dispatcher.issueController(actor, 100);
    }
    IntentGate.Result send(Participant.Kind kind, long revision) {
        var facts = new IntentGate.Facts(actor, 1, 1, true);
        return kind == Participant.Kind.BOT ? dispatcher.dispatchBot(facts, session.id(), revision, new BotRuntime.Decision(ACTION, Map.of()))
                : dispatcher.dispatchHuman(facts, new IntentGate.Intent(session.id(), token, sequence++, revision, ACTION, Map.of()));
    }
    ActionDispatcher.Handler spending(AtomicInteger coins) {
        return new ActionDispatcher.Handler() {
            public Optional<String> reject(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) { return coins.get() < 5 ? Optional.of("currency") : Optional.empty(); }
            public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) {
                int previous = coins.get(); return new ActionDispatcher.Prepared(() -> coins.set(previous - 5), () -> coins.set(previous), List.of(new ActionDispatcher.Effect(Id.of("test:spent"), Map.of("amount", 5))));
            }
        };
    }
    @ParameterizedTest @EnumSource(value = Participant.Kind.class, names = {"PLAYER", "BOT"})
    void humanAndBotUseSameCurrencyRulesRevisionAndEvents(Participant.Kind kind) {
        AtomicInteger coins = new AtomicInteger(5); setup(kind, spending(coins), 10); long old = session.revision();
        assertTrue(send(kind, old).accepted()); assertEquals(0, coins.get()); assertEquals(old + 1, session.revision());
        var events = dispatcher.drainEvents(10); assertEquals(1, events.size()); assertEquals(old + 1, events.getFirst().revision());
        assertEquals("currency", send(kind, session.revision()).reason()); assertEquals(0, coins.get()); assertTrue(dispatcher.drainEvents(10).isEmpty());
    }
    @ParameterizedTest @EnumSource(value = Participant.Kind.class, names = {"PLAYER", "BOT"})
    void staleStateAndRevokedControllerDenyBothOrigins(Participant.Kind kind) {
        AtomicInteger coins = new AtomicInteger(10); setup(kind, spending(coins), 10);
        assertEquals("stale", send(kind, session.revision() - 1).reason());
        dispatcher.revokeController(actor); assertEquals("controller", send(kind, session.revision()).reason()); assertEquals(10, coins.get());
    }
    @Test void botEndpointCannotAcceptPlayerOrSpectatorIdentity() {
        setup(Participant.Kind.PLAYER, spending(new AtomicInteger(5)), 10);
        assertEquals("participant_kind", dispatcher.dispatchBot(new IntentGate.Facts(actor, 1, 1, true), session.id(), session.revision(), new BotRuntime.Decision(ACTION, Map.of())).reason());
    }
    @Test void failedApplyRollsBackWithoutRevisionOrEventPublication() {
        AtomicInteger value = new AtomicInteger(5);
        setup(Participant.Kind.PLAYER, new ActionDispatcher.Handler() {
            public Optional<String> reject(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) { return Optional.empty(); }
            public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) {
                return new ActionDispatcher.Prepared(() -> { value.set(0); throw new IllegalStateException(); }, () -> value.set(5), List.of());
            }
        }, 10);
        long old = session.revision(); assertEquals("action_failed", send(Participant.Kind.PLAYER, old).reason());
        assertEquals(5, value.get()); assertEquals(old, session.revision()); assertTrue(dispatcher.drainEvents(10).isEmpty());
    }
    @Test void eventBackpressureRejectsBeforeMutation() {
        AtomicInteger coins = new AtomicInteger(15); setup(Participant.Kind.PLAYER, spending(coins), 1);
        assertTrue(send(Participant.Kind.PLAYER, session.revision()).accepted());
        assertEquals("event_backpressure", send(Participant.Kind.PLAYER, session.revision()).reason()); assertEquals(10, coins.get());
        dispatcher.drainEvents(1); assertTrue(send(Participant.Kind.PLAYER, session.revision()).accepted()); assertEquals(5, coins.get());
    }
    @Test void failedRollbackAbortsSessionAndReleasesResources() {
        setup(Participant.Kind.PLAYER, new ActionDispatcher.Handler() {
            public Optional<String> reject(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) { return Optional.empty(); }
            public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) {
                return new ActionDispatcher.Prepared(() -> { throw new IllegalStateException(); }, () -> { throw new IllegalStateException(); }, List.of());
            }
        }, 1);
        assertEquals("rollback_failed_session_closed", send(Participant.Kind.PLAYER, session.revision()).reason());
        assertEquals(GenericSession.Status.CLOSED, session.status()); assertTrue(arenas.snapshot().isEmpty());
    }
    @Test void botDecisionCannotBeRedirectedIntoAnotherSession() {
        AtomicInteger coins = new AtomicInteger(10); setup(Participant.Kind.BOT, spending(coins), 10);
        assertEquals("session", dispatcher.dispatchBot(new IntentGate.Facts(actor, 1, 1, true), UUID.randomUUID(), session.revision(), new BotRuntime.Decision(ACTION, Map.of())).reason());
        assertEquals(10, coins.get());
    }

}
