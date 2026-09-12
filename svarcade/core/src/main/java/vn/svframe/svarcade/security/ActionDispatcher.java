package vn.svframe.svarcade.security;

import java.util.*;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** One authoritative, transactional ingress for both human intents and bot decisions. */
public final class ActionDispatcher {
    public record Effect(Id type, Map<String, Object> data) {
        public Effect { Objects.requireNonNull(type); data = Values.map(data); }
    }
    public record Event(UUID session, UUID actor, long revision, Effect effect) { }
    public record Prepared(Runnable apply, Runnable rollback, List<Effect> effects) {
        public Prepared { Objects.requireNonNull(apply); Objects.requireNonNull(rollback); effects = List.copyOf(effects); }
    }
    public interface Handler extends IntentGate.Rule {
        /** Pure preparation. apply/rollback must execute synchronously on the owner thread. */
        Prepared prepare(GenericSession session, IntentGate.Facts facts, IntentGate.Intent intent);
    }
    private record BotController(UUID token, long sequence) { }
    private final GenericSession session;
    private final Registry<Handler> handlers;
    private final IntentGate gate;
    private final Map<UUID, BotController> botControllers = new HashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private final int eventCapacity;
    private boolean applying;
    public ActionDispatcher(GenericSession session, ArenaRuntime arenas, RateLimiter limiter,
                            Registry<Handler> handlers, double range, int eventCapacity) {
        if (eventCapacity < 1) throw new IllegalArgumentException("Event capacity");
        this.session = session; this.handlers = handlers; this.eventCapacity = eventCapacity;
        Map<Id, IntentGate.Rule> rules = new HashMap<>();
        handlers.ids().forEach(id -> rules.put(id, handlers.require(id)));
        gate = new IntentGate(session, arenas, limiter, new Registry<>(rules), range);
    }
    public UUID issueController(UUID actor, long expires) {
        session.thread().check(); UUID token = gate.issue(actor, expires);
        if (session.participants().get(actor).kind() == Participant.Kind.BOT) botControllers.put(actor, new BotController(token, 0));
        return token;
    }
    public void revokeController(UUID actor) { session.thread().check(); gate.revoke(actor); botControllers.remove(actor); }
    public IntentGate.Result dispatchHuman(IntentGate.Facts facts, IntentGate.Intent intent) {
        session.thread().check();
        if (!isKind(facts.actor(), Participant.Kind.PLAYER)) return denied("participant_kind");
        return dispatch(facts, intent);
    }
    public IntentGate.Result dispatchBot(IntentGate.Facts facts, UUID intendedSession, long revision, BotRuntime.Decision decision) {
        session.thread().check();
        if (!isKind(facts.actor(), Participant.Kind.BOT)) return denied("participant_kind");
        BotController controller = botControllers.get(facts.actor());
        if (controller == null || controller.sequence() == Long.MAX_VALUE) return denied("controller");
        botControllers.put(facts.actor(), new BotController(controller.token(), controller.sequence() + 1));
        return dispatch(facts, new IntentGate.Intent(intendedSession, controller.token(), controller.sequence(), revision, decision.action(), decision.payload()));
    }
    private boolean isKind(UUID actor, Participant.Kind kind) {
        Participant participant = session.participants().get(actor); return participant != null && participant.kind() == kind;
    }
    private IntentGate.Result dispatch(IntentGate.Facts facts, IntentGate.Intent intent) {
        if (applying) return denied("reentrant");
        applying = true;
        Prepared prepared = null; boolean started = false;
        try {
            IntentGate.Result validation = gate.validate(facts, intent);
            if (!validation.accepted()) return validation;
            if (session.revision() == Long.MAX_VALUE) return denied("revision_exhausted");
            prepared = Objects.requireNonNull(handlers.require(intent.action()).prepare(session, facts, intent));
            if (prepared.effects().size() > eventCapacity - events.size()) return denied("event_backpressure");
            started = true; prepared.apply().run();
            session.changed();
            for (Effect effect : prepared.effects()) events.addLast(new Event(session.id(), facts.actor(), session.revision(), effect));
            return new IntentGate.Result(true, "accepted");
        } catch (RuntimeException failure) {
            if (started) {
                try { prepared.rollback().run(); }
                catch (RuntimeException rollbackFailure) { session.close(); return denied("rollback_failed_session_closed"); }
            }
            return denied("action_failed");
        } finally { applying = false; }
    }
    public List<Event> drainEvents(int maximum) {
        session.thread().check(); if (maximum < 1) throw new IllegalArgumentException("Drain limit");
        List<Event> result = new ArrayList<>();
        while (!events.isEmpty() && result.size() < maximum) result.add(events.removeFirst());
        return List.copyOf(result);
    }
    private static IntentGate.Result denied(String reason) { return new IntentGate.Result(false, reason); }
}
