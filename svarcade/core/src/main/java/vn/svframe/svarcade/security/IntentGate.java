package vn.svframe.svarcade.security;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Shared ingress for human and bot intents; callers supply server-derived facts. */
public final class IntentGate {
    public record Intent(UUID session, UUID controller, long sequence, long revision, Id action, Map<String, Object> payload) {
        public Intent { Objects.requireNonNull(session); Objects.requireNonNull(controller); Objects.requireNonNull(action); payload = Values.map(payload); }
    }
    public record Facts(UUID actor, long tick, double distanceSquared, boolean permission) {
        public Facts { Objects.requireNonNull(actor); }
    }
    public record Result(boolean accepted, String reason) { }
    @FunctionalInterface public interface Rule {
        /** Must validate phase/turn/ownership/cost/prerequisites/caps/placement without mutations. */
        Optional<String> reject(GenericSession session, Facts facts, Intent intent);
    }
    private record Grant(UUID token, long expires, long sequence) { }
    private final Map<UUID, Grant> controllers = new HashMap<>();
    private final GenericSession session;
    private final ArenaRuntime arenas;
    private final RateLimiter limiter;
    private final Registry<Rule> rules;
    private final double maxDistanceSquared;
    public IntentGate(GenericSession session, ArenaRuntime arenas, RateLimiter limiter, Registry<Rule> rules, double maxDistance) {
        if (!Double.isFinite(maxDistance) || maxDistance < 0 || !Double.isFinite(maxDistance * maxDistance)) throw new IllegalArgumentException("Interaction distance");
        this.session = session; this.arenas = arenas; this.limiter = limiter; this.rules = rules; maxDistanceSquared = maxDistance * maxDistance;
    }
    public UUID issue(UUID actor, long expires) {
        session.thread().check();
        Participant p = session.participants().get(actor);
        if (p == null || p.kind() == Participant.Kind.SPECTATOR || expires < 0) throw new IllegalArgumentException("Invalid controller owner");
        UUID token = UUID.randomUUID(); controllers.put(actor, new Grant(token, expires, -1)); return token;
    }
    public void revoke(UUID actor) { session.thread().check(); controllers.remove(actor); }
    public Result validate(Facts facts, Intent intent) {
        session.thread().check();
        if (facts.tick() < 0) return deny("time");
        if (!limiter.allow(facts.actor(), facts.tick())) return deny("rate");
        if (session.status() != GenericSession.Status.RUNNING || !intent.session().equals(session.id())) return deny("session");
        Participant p = session.participants().get(facts.actor());
        if (p == null || p.kind() == Participant.Kind.SPECTATOR) return deny("membership");
        if (!arenas.owns(session.lease())) return deny("arena");
        if (!facts.permission()) return deny("permission");
        if (!Double.isFinite(facts.distanceSquared()) || facts.distanceSquared() < 0 || facts.distanceSquared() > maxDistanceSquared) return deny("range");
        Grant grant = controllers.get(facts.actor());
        if (grant == null || !grant.token().equals(intent.controller()) || facts.tick() >= grant.expires()) return deny("controller");
        if (intent.sequence() < 0 || intent.sequence() <= grant.sequence()) return deny("replay");
        if (intent.revision() != session.revision()) return deny("stale");
        if (!rules.contains(intent.action())) return deny("action");
        Optional<String> rejected = rules.require(intent.action()).reject(session, facts, intent);
        if (rejected.isPresent()) return deny(rejected.get());
        controllers.put(facts.actor(), new Grant(grant.token(), grant.expires(), intent.sequence()));
        return new Result(true, "accepted");
    }
    private static Result deny(String reason) { return new Result(false, reason); }
}
