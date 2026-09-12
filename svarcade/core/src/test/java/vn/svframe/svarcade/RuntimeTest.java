package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTest {
    static final Id SYSTEM = Id.of("test:system");
    static Definition definition() {
        return new Definition(1, Id.of("test:game"), "fingerprint", true, 1, 2, Set.of(),
                List.of(new Definition.SystemSpec(SYSTEM, new Node(Map.of(), "test"))), Map.of("one", new Node(Map.of("id", "one"), "arena")));
    }
    static GenericSession session(ArenaRuntime arenas, ThreadGuard thread) {
        return new GenericSession(UUID.randomUUID(), definition(), "one", List.of(new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "team")), arenas, thread);
    }
    static class Stub implements SessionSystem {
        int value; int closes; boolean failClose; boolean failStart;
        public int stateSchema() { return 1; }
        public void start() { if (failStart) throw new IllegalStateException("start failed"); value = 10; }
        public void tick(long tick) { value++; }
        public Map<String, Object> snapshot() { return Map.of("value", value); }
        public void restore(int schema, Map<String, Object> state) { if (schema != 1) throw new IllegalArgumentException("schema"); value = (Integer) state.get("value"); }
        public void close() { closes++; if (failClose) throw new IllegalStateException("cleanup failed"); }
    }
    @Test void arenaIsExclusiveAndStaleLeaseCannotUnlockNewOwner() {
        ArenaRuntime arenas = new ArenaRuntime(); ArenaRuntime.Key key = new ArenaRuntime.Key(Id.of("test:game"), "one");
        var first = arenas.acquire(key, UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> arenas.acquire(key, UUID.randomUUID()));
        assertTrue(arenas.release(first)); var second = arenas.acquire(key, UUID.randomUUID());
        assertFalse(arenas.release(first)); assertTrue(arenas.owns(second));
    }
    @Test void cleanupIsReverseOrderedAndRetriesFailuresOnly() {
        ResourceTracker tracker = new ResourceTracker(new ThreadGuard()); List<String> order = new ArrayList<>(); AtomicBoolean fail = new AtomicBoolean(true);
        tracker.own("first", () -> order.add("first"));
        tracker.own("second", () -> { order.add("second"); if (fail.getAndSet(false)) throw new Exception("once"); });
        assertEquals(1, tracker.cleanup().size()); assertEquals(List.of("second", "first"), order); assertEquals(1, tracker.size());
        assertTrue(tracker.cleanup().isEmpty()); assertEquals(List.of("second", "first", "second"), order); assertEquals(0, tracker.size());
        assertTrue(tracker.cleanup().isEmpty()); assertThrows(IllegalStateException.class, () -> tracker.own("new", () -> { }));
    }
    @Test void failedCleanupRetainsArenaUntilRetrySucceeds() {
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession s = session(arenas, new ThreadGuard()); Stub stub = new Stub(); stub.failClose = true;
        s.start(new Registry<>(Map.of(SYSTEM, (session, config) -> stub)));
        assertEquals(1, s.close().size()); assertEquals(GenericSession.Status.CLOSING, s.status()); assertTrue(arenas.owns(s.lease()));
        stub.failClose = false; assertTrue(s.close().isEmpty()); assertEquals(GenericSession.Status.CLOSED, s.status()); assertFalse(arenas.owns(s.lease()));
        s.close(); assertEquals(2, stub.closes);
    }
    @Test void partialStartCleansAlreadyCreatedSystems() {
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession s = session(arenas, new ThreadGuard()); Stub stub = new Stub(); stub.failStart = true;
        assertThrows(IllegalStateException.class, () -> s.start(new Registry<>(Map.of(SYSTEM, (session, config) -> stub))));
        assertEquals(1, stub.closes); assertTrue(arenas.snapshot().isEmpty()); assertEquals(GenericSession.Status.CLOSED, s.status());
    }
    @Test void sessionKeepsImmutableSnapshotAndRestoresThroughExplicitSchema() {
        ArenaRuntime arenas = new ArenaRuntime(); ThreadGuard thread = new ThreadGuard(); GenericSession s = session(arenas, thread); Stub original = new Stub();
        s.start(new Registry<>(Map.of(SYSTEM, (session, config) -> original))); s.tick(1); var state = s.snapshot(); s.tick(2);
        assertEquals(11, state.get(SYSTEM).data().get("value")); s.close();
        GenericSession restored = session(arenas, thread); Stub target = new Stub(); restored.restore(new Registry<>(Map.of(SYSTEM, (session, config) -> target)), state);
        assertEquals(11, target.value); restored.close();
    }
    @Test void failedRestoreCleansAndDoesNotReplayStart() {
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession s = session(arenas, new ThreadGuard()); Stub stub = new Stub(); stub.failStart = true;
        assertThrows(IllegalArgumentException.class, () -> s.restore(new Registry<>(Map.of(SYSTEM, (session, config) -> stub)), Map.of(SYSTEM, new GenericSession.SystemState(2, Map.of()))));
        assertEquals(1, stub.closes); assertTrue(arenas.snapshot().isEmpty());
    }
    @Test void threadGuardRejectsWorkerMutation() throws Exception {
        ThreadGuard thread = new ThreadGuard();
        assertThrows(CompletionException.class, () -> CompletableFuture.runAsync(thread::check).join());
    }
    @Test void replacedTimerCannotFireAndCapacityDoesNotAccumulateTombstones() {
        TimerRuntime timers = new TimerRuntime(new ThreadGuard(), 2); AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 10_000; i++) timers.schedule("same", 100, calls::incrementAndGet);
        assertEquals(1, timers.size()); assertEquals(0, timers.advance(99, 10)); assertEquals(1, timers.advance(100, 10)); assertEquals(1, calls.get());
    }
    @Test void timerCallbacksCannotCreateAnUnboundedSameTickLoop() {
        TimerRuntime timers = new TimerRuntime(new ThreadGuard(), 2); AtomicInteger calls = new AtomicInteger();
        Runnable[] callback = new Runnable[1]; callback[0] = () -> { calls.incrementAndGet(); timers.schedule("loop", 10, callback[0]); };
        timers.schedule("loop", 10, callback[0]); assertEquals(1, timers.advance(10, 10)); assertEquals(1, calls.get());
        assertEquals(1, timers.advance(10, 10)); assertEquals(2, calls.get());
    }
    @Test void timerExceptionPreservesOtherDueWorkAndCancellationWins() {
        TimerRuntime timers = new TimerRuntime(new ThreadGuard(), 4); AtomicInteger calls = new AtomicInteger();
        timers.schedule("bad", 10, () -> { throw new IllegalStateException(); }); timers.schedule("good", 10, calls::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> timers.advance(10, 10)); assertEquals(1, timers.size());
        assertEquals(1, timers.advance(10, 10)); assertEquals(1, calls.get());
        timers.schedule("cancel", 11, () -> timers.cancel("later")); timers.schedule("later", 11, () -> fail("Cancelled due timer fired"));
        assertEquals(1, timers.advance(11, 10)); assertEquals(0, timers.size());
    }
    @Test void timerOverflowCapacityAndTimeRegressionAreRejected() {
        TimerRuntime timers = new TimerRuntime(new ThreadGuard(), 1); timers.schedule("one", 3, () -> { });
        assertThrows(IllegalStateException.class, () -> timers.schedule("two", 4, () -> { })); timers.advance(3, 1);
        assertThrows(IllegalArgumentException.class, () -> timers.advance(2, 1));
    }
}
