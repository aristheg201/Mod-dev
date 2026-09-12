package vn.svframe.svarcade.verification;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class SecurityLifecycleChecks {
    private SecurityLifecycleChecks() { }
    public static void main(String[] ignored) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        RateLimiter limiter = new RateLimiter(1, 2, 0.5);
        equal(true, limiter.allow(a, 0)); equal(true, limiter.allow(a, 0));
        equal(false, limiter.allow(b, 1)); equal(false, limiter.allow(b, 2));
        equal(true, limiter.allow(b, 4)); equal(1, limiter.size());
        equal(true, limiter.allow(b, 4)); equal(false, limiter.allow(b, 4));
        rejects(IllegalArgumentException.class, () -> limiter.allow(b, 3));

        Id system = Id.of("test:system"), action = Id.of("test:action");
        Definition d = new Definition(1, Id.of("test:game"), "test", true, 1, 1, Set.of(),
                List.of(new Definition.SystemSpec(system, new Node(Map.of(), "system"))), Map.of("one", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime();
        GenericSession session = new GenericSession(UUID.randomUUID(), d, "one", List.of(new Participant(a, Participant.Kind.PLAYER, "team")), arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(system, (s, n) -> new SessionSystem() {
            public int stateSchema() { return 1; }
            public void start() { }
            public void tick(long tick) { }
            public Map<String, Object> snapshot() { return Map.of(); }
            public void restore(int schema, Map<String, Object> data) { }
            public void close() { }
        })));
        RateLimiter shared = new RateLimiter(1, 4, 1);
        IntentGate gate = new IntentGate(session, arenas, shared, new Registry<>(Map.of(action, (s, f, i) -> Optional.empty())), 10);
        UUID token = gate.issue(a, 10);
        IntentGate.Intent intent = new IntentGate.Intent(session.id(), token, 0, session.revision(), action, Map.of());
        equal("membership", gate.validate(new IntentGate.Facts(b, 1, 0, true), intent).reason());
        equal(0, shared.size());
        equal(true, gate.validate(new IntentGate.Facts(a, 1, 0, true), intent).accepted());
        equal("replay", gate.validate(new IntentGate.Facts(a, 1, 0, true), intent).reason());
        equal("controller", gate.validate(new IntentGate.Facts(a, 10, 0, true), intent).reason());
        equal(0, gate.controllers());
        gate.issue(a, 20); equal(1, gate.controllers());
        session.close(); equal(0, gate.controllers()); equal(0, session.resources().size()); equal(0, arenas.snapshot().size());
        equal("session", gate.validate(new IntentGate.Facts(a, 11, 0, true), intent).reason());
        rejects(IllegalStateException.class, () -> gate.issue(a, 30));
        session.close(); gate.close(); equal(0, gate.controllers());
        System.out.println("SecurityLifecycleChecks: PASS (automatic capacity recovery, replay, expiry, membership isolation, session-owned revocation)");
    }
}
