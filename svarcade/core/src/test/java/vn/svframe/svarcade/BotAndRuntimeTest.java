package vn.svframe.svarcade;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.persistence.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.security.*;
import static org.junit.jupiter.api.Assertions.*;

class BotAndRuntimeTest {
    GenericGameRuntime runtime(RuntimeTest.Stub stub) {
        DefinitionRegistry registry = new DefinitionRegistry(); registry.reload(() -> List.of(RuntimeTest.definition()), Set.of(), Runnable::run).join();
        return new GenericGameRuntime(new ThreadGuard(), registry, new Registry<>(Map.of(RuntimeTest.SYSTEM, (s, c) -> stub)), 8);
    }
    List<Participant> people(UUID id) { return List.of(new Participant(id, Participant.Kind.BOT, "team")); }
    @Test void failedStartupWithFailedCleanupRemainsDiscoverableAndIsRetried() {
        RuntimeTest.Stub stub = new RuntimeTest.Stub(); stub.failStart = true; stub.failClose = true;
        var runtime = runtime(stub);
        assertThrows(IllegalStateException.class, () -> runtime.open(RuntimeTest.definition().id(), "one", people(UUID.randomUUID())));
        assertEquals(1, runtime.sessions().size()); assertEquals(1, runtime.arenas().snapshot().size()); assertFalse(runtime.failures().isEmpty());
        stub.failClose = false; runtime.tick(1); assertTrue(runtime.sessions().isEmpty()); assertTrue(runtime.arenas().snapshot().isEmpty());
    }
    @Test void duplicateParticipantRejectedAndRecoveryRetainsIdentityRevisionAndState() {
        RuntimeTest.Stub stub = new RuntimeTest.Stub(); var runtime = runtime(stub); UUID person = UUID.randomUUID();
        var session = runtime.open(RuntimeTest.definition().id(), "one", people(person)); session.changed(); session.changed();
        assertThrows(IllegalStateException.class, () -> runtime.open(RuntimeTest.definition().id(), "one", people(person)));
        SessionSnapshot saved = SessionSnapshot.capture(session); runtime.close(session.id());
        var restored = runtime.recover(saved); assertEquals(saved.id(), restored.id()); assertTrue(restored.revision() > saved.revision()); assertEquals(10, stub.value);
        runtime.close(restored.id()); assertTrue(runtime.sessions().isEmpty());
    }
    @Test void operationBudgetIsExactAndRejectsInvalidTuning() {
        ThinkBudget budget = new ThinkBudget(10_000_000_000L, 2); budget.visit(); budget.visit();
        assertThrows(CancellationException.class, budget::visit); assertEquals(2, budget.operations());
        assertThrows(IllegalArgumentException.class, () -> new ThinkBudget(0, 2));
    }
    @Test void staleBotDecisionCannotApplyToChangedState() throws Exception {
        ThreadGuard thread = new ThreadGuard(); var runtime = runtime(new RuntimeTest.Stub()); UUID person = UUID.randomUUID();
        GenericSession session = runtime.open(RuntimeTest.definition().id(), "one", people(person));
        CountDownLatch computed = new CountDownLatch(1); Id strategy = Id.of("test:search");
        try (BotRuntime bots = new BotRuntime(thread, new Registry<>(Map.of(strategy, (context, budget) -> {
            budget.visit(); computed.countDown(); return new BotRuntime.Decision(Id.of("test:move"), Map.of());
        })), 1, 1)) {
            var context = new BotRuntime.Context(session.id(), person, session.revision(), Map.of("visible", 1), new Node(Map.of("depth", 1), "bot"));
            assertTrue(bots.submit(context, strategy, 1_000_000_000, 10)); assertTrue(computed.await(5, TimeUnit.SECONDS));
            var dispatcher = new ActionDispatcher(session, runtime.arenas(), new RateLimiter(8, 20, 1), new Registry<>(Map.of()), 10, 10);
            dispatcher.issueController(person, 100);
            session.changed();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (bots.pending() > 0 && System.nanoTime() < deadline) { bots.poll(session, person, dispatcher, new IntentGate.Facts(person, 1, 0, true)); Thread.onSpinWait(); }
            assertEquals(0, bots.pending()); assertEquals(1L, bots.metrics().get("stale"));
        }
    }
    @Test void boundedQueueRejectsOverloadAndCancellationStopsCooperativeWork() throws Exception {
        ThreadGuard thread = new ThreadGuard(); CountDownLatch started = new CountDownLatch(1), unblock = new CountDownLatch(1);
        Id strategy = Id.of("test:search");
        try (BotRuntime bots = new BotRuntime(thread, new Registry<>(Map.of(strategy, (context, budget) -> {
            started.countDown(); try { unblock.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            budget.check(); return new BotRuntime.Decision(Id.of("test:move"), Map.of());
        })), 1, 1)) {
            UUID session = UUID.randomUUID(); UUID first = UUID.randomUUID();
            assertTrue(bots.submit(new BotRuntime.Context(session, first, 1, Map.of(), new Node(Map.of(), "tuning")), strategy, 5_000_000_000L, 10));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(bots.submit(new BotRuntime.Context(session, UUID.randomUUID(), 1, Map.of(), new Node(Map.of(), "tuning")), strategy, 5_000_000_000L, 10));
            assertFalse(bots.submit(new BotRuntime.Context(session, UUID.randomUUID(), 1, Map.of(), new Node(Map.of(), "tuning")), strategy, 5_000_000_000L, 10));
            assertEquals(2, bots.pending()); bots.cancel(first); assertEquals(1, bots.pending()); unblock.countDown();
        } finally { unblock.countDown(); }
    }
}
