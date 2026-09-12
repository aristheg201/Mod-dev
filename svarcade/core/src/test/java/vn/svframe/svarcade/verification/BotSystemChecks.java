package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class BotSystemChecks {
    private BotSystemChecks() { }
    private static final Id ACTION = Id.of("test:act");
    private static Node profile(int amount) {
        return new Node(Map.of("strategy", "test:strategy", "parameters", Map.of("amount", amount), "think_nanos", 1_000_000_000L,
                "operations", 1000, "delay_ticks", 2, "retry_ticks", 3, "pending_timeout_ticks", 100, "controller_ttl_ticks", 100), "profile");
    }
    public static Node data() {
        return new Node(Map.of("profiles", Map.of("easy", profile(1).values(), "normal", profile(3).values(), "hard", profile(7).values()),
                "default_difficulty", "normal", "team_difficulties", Map.of("a", "hard"), "max_bots_per_tick", 1, "seed", 123), "bots");
    }
    private record Match(GenericSession session, BotSystem botSystem, ActionDispatcher dispatcher, AtomicInteger value, AtomicInteger snapshots, AtomicBoolean phase) { }
    private static Match match(BotRuntime workers, List<Participant> people, Map<Id, GenericSession.SystemState> saved) {
        Definition d = new Definition(1, Id.of("test:game"), "same", true, 1, 4, Set.of(),
                List.of(new Definition.SystemSpec(BotSystem.ID, data())), Map.of("one", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime();
        GenericSession session = new GenericSession(UUID.randomUUID(), d, "one", people, arenas, new ThreadGuard());
        AtomicInteger value = new AtomicInteger(), snapshots = new AtomicInteger(); AtomicBoolean phase = new AtomicBoolean(true);
        BotSystem[] scheduler = new BotSystem[1]; ActionDispatcher[] actions = new ActionDispatcher[1];
        Registry<SystemFactory> factories = new Registry<>(Map.of(BotSystem.ID, (owner, config) -> {
            actions[0] = new ActionDispatcher(owner, arenas, new RateLimiter(8, 100, 1), new Registry<>(Map.of(ACTION, new ActionDispatcher.Handler() {
                public Optional<String> reject(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) { return phase.get() ? Optional.empty() : Optional.of("phase"); }
                public ActionDispatcher.Prepared prepare(GenericSession s, IntentGate.Facts f, IntentGate.Intent i) {
                    int old = value.get(), next = old + ((Number) i.payload().get("amount")).intValue();
                    return new ActionDispatcher.Prepared(() -> value.set(next), () -> value.set(old), List.of());
                }
            })), 10, 32);
            BotDecisionSource source = new BotDecisionSource() {
                public CompiledProfile compile(BotProfile p) {
                    int amount = (int) p.parameters().integer("amount", 1, 10);
                    return (actor, seed) -> {
                        snapshots.incrementAndGet();
                        return (context, budget) -> { budget.visit(); return new BotRuntime.Decision(ACTION, Map.of("amount", amount)); };
                    };
                }
                public boolean eligible(UUID actor) { return phase.get(); }
                public IntentGate.Facts facts(UUID actor, long tick) { return new IntentGate.Facts(actor, tick, 0, true); }
            };
            scheduler[0] = new BotSystem(owner, workers, actions[0], source, BotSystem.Config.parse(config)); return scheduler[0];
        }));
        // Use the owning arena registry in the dispatcher; no fake validation path.
        if (saved == null) session.start(factories); else session.restore(factories, saved);
        return new Match(session, scheduler[0], actions[0], value, snapshots, phase);
    }
    private static void done(BotRuntime workers, UUID actor) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!workers.done(actor) && System.nanoTime() < until) Thread.sleep(1);
        equal(true, workers.done(actor));
    }
    public static void main(String[] ignored) throws Exception {
        rejects(ConfigException.class, () -> BotSystem.Config.parse(new Node(Map.of("profiles", Map.of("expert", profile(1).values()),
                "default_difficulty", "expert", "max_bots_per_tick", 1, "seed", 0), "bad")));
        UUID actor = UUID.randomUUID(); List<Participant> people = List.of(new Participant(actor, Participant.Kind.BOT, "a"), new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "b"));
        try (BotRuntime workers = new BotRuntime(new ThreadGuard(), new Registry<>(Map.of()), 1, 2)) {
            Match first = match(workers, people, null);
            equal(Map.of(actor, BotRuntime.Difficulty.HARD), first.botSystem.assignments());
            first.session.tick(10); first.session.tick(11); equal(0, first.snapshots.get());
            first.session.tick(12); done(workers, actor); equal(0, first.value.get());
            first.session.tick(13); equal(7, first.value.get()); equal(1, first.snapshots.get());
            Map<Id, GenericSession.SystemState> saved = first.session.snapshot(); first.session.close(); equal(0, workers.pending());
            Match restored = match(workers, people, saved);
            equal(Map.of(actor, BotRuntime.Difficulty.HARD), restored.botSystem.assignments());
            restored.session.tick(1); restored.session.tick(2); equal(0, restored.snapshots.get());
            restored.session.tick(3); done(workers, actor); restored.session.tick(4); equal(7, restored.value.get());
            restored.phase.set(false); restored.session.tick(6); equal(1, restored.snapshots.get());
            restored.phase.set(true); restored.session.tick(7); done(workers, actor);
            restored.session.changed(); restored.session.tick(8); equal(7, restored.value.get()); equal(0, workers.pending());
            restored.session.tick(11); done(workers, actor); restored.session.close(); equal(0, workers.pending());
            equal(0, restored.dispatcher.ownedCounts().get("grants"));
            Match inFlight = match(workers, people, null); inFlight.session.tick(0); inFlight.session.tick(2); done(workers, actor);
            Map<Id, GenericSession.SystemState> pendingSaved = inFlight.session.snapshot(); inFlight.session.close();
            Match resumed = match(workers, people, pendingSaved); resumed.session.tick(0); done(workers, actor); resumed.session.tick(1);
            equal(7, resumed.value.get()); resumed.session.close();
        }
        System.out.println("BotSystemChecks: PASS (data profiles, scheduling delay/backoff, immutable snapshots, stale cancellation, recovery of assignments/pending work, teardown)");
    }
}
