package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {
    private static final Id ACTION = Id.of("test:move");
    private ArenaRuntime arenas;
    private GenericSession session;
    private IntentGate gate;
    private UUID actor, token;
    @BeforeEach void setup() {
        arenas = new ArenaRuntime(); session = RuntimeTest.session(arenas, new ThreadGuard());
        session.start(new Registry<>(Map.of(RuntimeTest.SYSTEM, (s, c) -> new RuntimeTest.Stub())));
        gate = new IntentGate(session, arenas, new RateLimiter(16, 100, 1), new Registry<>(Map.of(ACTION, (s, f, i) -> Optional.empty())), 10);
        actor = session.participants().keySet().iterator().next(); token = gate.issue(actor, 1000);
    }
    IntentGate.Intent intent(long seq, long revision, UUID controller) { return new IntentGate.Intent(session.id(), controller, seq, revision, ACTION, Map.of()); }
    IntentGate.Facts facts(UUID id, double range, boolean permission) { return new IntentGate.Facts(id, 1, range, permission); }
    @Test void validActionAcceptedOnceAndReplayRejected() {
        var intent = intent(1, session.revision(), token);
        assertTrue(gate.validate(facts(actor, 4, true), intent).accepted());
        assertEquals("replay", gate.validate(facts(actor, 4, true), intent).reason());
    }
    @Test void forgedControllerStaleStateAndInvalidRangeRejected() {
        assertEquals("controller", gate.validate(facts(actor, 4, true), intent(1, session.revision(), UUID.randomUUID())).reason());
        assertEquals("stale", gate.validate(facts(actor, 4, true), intent(1, session.revision() - 1, token)).reason());
        assertEquals("range", gate.validate(facts(actor, Double.NaN, true), intent(1, session.revision(), token)).reason());
        assertEquals("range", gate.validate(facts(actor, 101, true), intent(1, session.revision(), token)).reason());
    }
    @Test void nonMemberPermissionAndLostArenaRejectIntent() {
        assertEquals("membership", gate.validate(facts(UUID.randomUUID(), 1, true), intent(1, session.revision(), token)).reason());
        assertEquals("permission", gate.validate(facts(actor, 1, false), intent(1, session.revision(), token)).reason());
        arenas.release(session.lease()); assertEquals("arena", gate.validate(facts(actor, 1, true), intent(1, session.revision(), token)).reason());
    }
    @Test void revocationAndExpiryDisableOldController() {
        gate.revoke(actor); assertEquals("controller", gate.validate(facts(actor, 1, true), intent(1, session.revision(), token)).reason());
        token = gate.issue(actor, 2); assertEquals("controller", gate.validate(new IntentGate.Facts(actor, 2, 1, true), intent(1, session.revision(), token)).reason());
    }
    @Test void gameRulesShareTheGateAndCannotBeBypassedByValidToken() {
        gate = new IntentGate(session, arenas, new RateLimiter(16, 100, 1), new Registry<>(Map.of(ACTION, (s, f, i) -> Optional.of("phase"))), 10);
        token = gate.issue(actor, 100);
        assertEquals("phase", gate.validate(facts(actor, 1, true), intent(1, session.revision(), token)).reason());
    }
    @Test void limiterBoundsMemoryRefillsAndNeverEvictsExhaustedKeyToBypassLimit() {
        RateLimiter limiter = new RateLimiter(1, 2, 0.5); UUID first = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(limiter.allow(first, 0)); assertTrue(limiter.allow(first, 0)); assertFalse(limiter.allow(first, 0));
        assertFalse(limiter.allow(other, 0)); limiter.prune(1); assertFalse(limiter.allow(other, 1));
        limiter.prune(4); assertTrue(limiter.allow(other, 4)); assertEquals(1, limiter.size());
        assertThrows(IllegalArgumentException.class, () -> limiter.allow(other, 3));
    }
}
