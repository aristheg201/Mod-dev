package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class BotWorkerChecks {
    private BotWorkerChecks() { }
    private static final Id ACTION = Id.of("test:advance");
    private record Fixture(GenericSession session, ActionDispatcher dispatcher, UUID bot, AtomicInteger value) { }
    private static Fixture fixture() {
        ThreadGuard thread = new ThreadGuard(); ArenaRuntime arenas = new ArenaRuntime(); UUID bot = UUID.randomUUID();
        Definition definition = new Definition(1, Id.of("test:runtime"), "test", true, 1, 1, Set.of(), List.of(), Map.of("one", new Node(Map.of(), "arena")));
        GenericSession session = new GenericSession(UUID.randomUUID(), definition, "one", List.of(new Participant(bot, Participant.Kind.BOT, "a")), arenas, thread);
        session.start(new Registry<>(Map.of())); AtomicInteger value = new AtomicInteger();
        ActionDispatcher.Handler handler = new ActionDispatcher.Handler() {
            public Optional<String> reject(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) { return Optional.empty(); }
            public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) {
                int before = value.get(); return new ActionDispatcher.Prepared(() -> value.set(before + 1), () -> value.set(before), List.of());
            }
        };
        ActionDispatcher dispatcher = new ActionDispatcher(session, arenas, new RateLimiter(8, 100, 1), new Registry<>(Map.of(ACTION, handler)), 10, 32);
        dispatcher.issueController(bot, 100); return new Fixture(session, dispatcher, bot, value);
    }
    private static BotRuntime.Context context(Fixture f) { return new BotRuntime.Context(f.session.id(), f.bot, f.session.revision(), Map.of(), new Node(Map.of(), "tuning")); }
    private static BotRuntime.Decision decision() { return new BotRuntime.Decision(ACTION, Map.of()); }
    private static void await(BotRuntime bots, UUID bot) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!bots.done(bot) && System.nanoTime() < deadline) Thread.sleep(1);
        equal(true, bots.done(bot));
    }
    private static BotRuntime.Poll poll(BotRuntime bots, Fixture f, long tick, boolean permission, double distance) {
        return bots.pollResult(f.session, f.bot, f.dispatcher, new IntentGate.Facts(f.bot, tick, distance, permission));
    }
    public static void main(String[] ignored) throws Exception {
        Fixture f = fixture(); AtomicLong clock = new AtomicLong();
        try (BotRuntime bots = new BotRuntime(new ThreadGuard(), new Registry<>(Map.of()), 1, 1, clock::get)) {
            BotRuntime.BoundedStrategy partial = (ctx, budget) -> {
                budget.visit(); BotRuntime.Decision complete = decision(); clock.set(100);
                try { budget.check(); throw new AssertionError("Expected expiration"); }
                catch (ThinkBudget.Exhausted expected) { return Optional.of(complete); }
            };
            equal(true, bots.submit(context(f), partial, 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.APPLIED, poll(bots, f, 1, true, 0)); equal(1, f.value.get());
            equal(1L, bots.metrics().get("exhausted")); equal(1L, bots.metrics().get("nodes"));
            BotRuntime.Strategy tardy = (ctx, budget) -> { clock.addAndGet(100); return decision(); };
            equal(true, bots.submit(context(f), tardy, 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.FAILED, poll(bots, f, 2, true, 0)); equal(1, f.value.get());
            equal(false, bots.failures().isEmpty());
            BotRuntime.BoundedStrategy noMove = (ctx, budget) -> Optional.empty();
            equal(true, bots.submit(context(f), noMove, 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.EMPTY, poll(bots, f, 3, true, 0)); equal(1L, bots.metrics().get("no_decision"));
            equal(true, bots.submit(context(f), (ctx, budget) -> decision(), 10, 20)); await(bots, f.bot);
            f.session.changed(); equal(BotRuntime.Poll.STALE, poll(bots, f, 4, true, 0)); equal(1, f.value.get());
            equal(true, bots.submit(context(f), (ctx, budget) -> decision(), 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.REJECTED, poll(bots, f, 5, false, 0)); equal(1, f.value.get());
            equal(true, bots.submit(context(f), (ctx, budget) -> decision(), 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.REJECTED, poll(bots, f, 6, true, 101)); equal(1, f.value.get());
            equal(true, bots.submit(context(f), (ctx, budget) -> decision(), 10, 20)); await(bots, f.bot);
            equal(BotRuntime.Poll.REJECTED, poll(bots, f, 100, true, 0)); equal(1, f.value.get());
            for (int i = 0; i < 2; i++) {
                UUID actor = UUID.randomUUID();
                equal(true, bots.submit(new BotRuntime.Context(f.session.id(), actor, 1, Map.of(), new Node(Map.of(), "tuning")), (ctx, budget) -> decision(), 10, 20));
                await(bots, actor);
            }
            equal(false, bots.submit(context(f), (ctx, budget) -> decision(), 10, 20)); equal(2, bots.pending());
            bots.cancelSession(UUID.randomUUID()); equal(2, bots.pending());
            bots.cancelSession(f.session.id()); equal(0, bots.pending());
            equal(2L, bots.metrics().get("cancelled"));
        } finally { f.session.close(); }
        Fixture second = fixture(); CountDownLatch started = new CountDownLatch(1), exited = new CountDownLatch(1);
        BotRuntime bots = new BotRuntime(new ThreadGuard(), new Registry<>(Map.of()), 1, 1);
        equal(true, bots.submit(context(second), (ctx, budget) -> {
            started.countDown();
            try { while (true) { budget.visit(); Thread.onSpinWait(); } }
            finally { exited.countDown(); }
        }, TimeUnit.SECONDS.toNanos(10), Long.MAX_VALUE));
        equal(true, started.await(5, TimeUnit.SECONDS)); bots.cancelSession(second.session.id());
        equal(true, exited.await(5, TimeUnit.SECONDS)); equal(0, bots.pending()); equal(0, second.value.get());
        equal(true, bots.submit(context(second), (ctx, budget) -> { throw new IllegalArgumentException("bad strategy"); }, TimeUnit.SECONDS.toNanos(1), 100));
        await(bots, second.bot); equal(BotRuntime.Poll.FAILED, poll(bots, second, 1, true, 0));
        equal(true, bots.submit(context(second), (ctx, budget) -> decision(), TimeUnit.SECONDS.toNanos(1), 100));
        await(bots, second.bot); equal(BotRuntime.Poll.APPLIED, poll(bots, second, 2, true, 0));
        bots.close(); equal(false, bots.submit(context(second), (ctx, budget) -> decision(), 1000, 1));
        equal(true, bots.awaitTermination(5, TimeUnit.SECONDS)); second.session.close();
        equal(0, second.dispatcher.ownedCounts().get("grants"));
        System.out.println("BotWorkerChecks: PASS (partial budget results, strict legacy deadlines, empty/stale/rejected decisions, completed-task cap, cancellation, failure isolation, controller teardown)");
    }
}
