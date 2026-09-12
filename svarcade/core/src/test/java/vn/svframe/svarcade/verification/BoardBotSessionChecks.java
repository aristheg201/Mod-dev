package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.turn.*;
import vn.svframe.svarcade.systems.objective.*;
import static vn.svframe.svarcade.verification.BoardFixtures.*;
import static vn.svframe.svarcade.verification.Checks.*;

public final class BoardBotSessionChecks {
    private BoardBotSessionChecks() { }
    private static final Id MOVE = id("move"), REP = id("repeat"), QUIET = id("quiet"), ACCEPT = id("accept"), OFFER = id("offer");
    private static Node profiles(String selected, boolean acceptDraw) {
        Map<String, Object> profiles = new LinkedHashMap<>();
        for (var entry : Map.of("easy", 1, "normal", 3, "hard", 5).entrySet()) {
            Map<String, Object> parameters = new LinkedHashMap<>(BoardSearchChecks.tuning(entry.getValue(), 32, 1, 0).values());
            parameters.put("accept_draw_when_not_ahead", acceptDraw);
            profiles.put(entry.getKey(), Map.of("strategy", "svarcade:minimax", "parameters", parameters,
                    "think_nanos", 2_000_000_000L, "operations", 500, "delay_ticks", 0, "retry_ticks", 2,
                    "pending_timeout_ticks", 100, "controller_ttl_ticks", 100));
        }
        return new Node(Map.of("profiles", profiles, "default_difficulty", selected, "max_bots_per_tick", 2, "seed", 1), "bots");
    }
    private record Match(GenericSession session, BoardAccess board, TurnAccess clock, ObjectiveAccess objective, BotSystem botSystem,
                         ActionDispatcher dispatcher, UUID bot, UUID player, AtomicLong nanos, AtomicBoolean phase, AtomicBoolean permission) { }
    private static Match match(BotRuntime workers, GridPosition initial, String difficulty, List<Participant> identities,
                               Map<Id, GenericSession.SystemState> saved, boolean acceptDraw) {
        AtomicLong clock = new AtomicLong(); AtomicBoolean phase = new AtomicBoolean(true), permission = new AtomicBoolean(true);
        List<Participant> people = identities == null ? List.of(new Participant(UUID.randomUUID(), Participant.Kind.BOT, "white"),
                new Participant(UUID.randomUUID(), Participant.Kind.PLAYER, "black")) : identities;
        Node movement = data(standard()), board = new Node(Map.of("initial", initial.encode(), "history_limit", 512), "board");
        Node turns = new Node(Map.of("banks", Map.of("white", Map.of("initial_ms", 100000, "increment_ms", 1000, "maximum_ms", 200000),
                "black", Map.of("initial_ms", 100000, "increment_ms", 1000, "maximum_ms", 200000)), "initial_team", initial.turn(), "enabled", true,
                "starts_paused", false, "persistence_interval_ms", 100), "turn");
        Node objective = new Node(Map.of("counters", Map.of(), "reasons", AdjudicationFixtures.config().reasons().values().stream().map(Id::toString).toList()), "outcome");
        Node sourceConfig = new Node(Map.of("actions", Map.of("move", MOVE.toString(), "claim_repetition", REP.toString(), "claim_quiet", QUIET.toString(), "accept_draw", ACCEPT.toString())), "source");
        List<Definition.SystemSpec> specs = List.of(new Definition.SystemSpec(MovementSystem.ID, movement), new Definition.SystemSpec(BoardSystem.ID, board),
                new Definition.SystemSpec(TurnSystem.ID, turns), new Definition.SystemSpec(ObjectiveSystem.ID, objective),
                new Definition.SystemSpec(BoardAdjudicationSystem.ID, AdjudicationFixtures.data()), new Definition.SystemSpec(BoardDecisionSource.ID, sourceConfig),
                new Definition.SystemSpec(BotSystem.ID, profiles(difficulty, acceptDraw)));
        Definition d = new Definition(1, id("bot_match"), "same-content", true, 2, 2, Set.of(), specs, Map.of("one", new Node(Map.of(), "arena")));
        ArenaRuntime arenas = new ArenaRuntime(); GenericSession session = new GenericSession(UUID.randomUUID(), d, "one", people, arenas, new ThreadGuard());
        ActionDispatcher[] dispatcher = new ActionDispatcher[1]; BotSystem[] bots = new BotSystem[1];
        SystemFactory botFactory = new SystemFactory() {
            public Set<SessionServices.Key<?>> requires() { return Set.of(BotDecisionSource.ACCESS, BoardSystem.ACCESS, TurnSystem.ACCESS, BoardAdjudicationSystem.ACCESS); }
            public SessionSystem create(GenericSession s, Node config) {
                BoardAccess access = s.services().require(BoardSystem.ACCESS); TurnAccess timer = s.services().require(TurnSystem.ACCESS);
                AdjudicationAccess adjudication = s.services().require(BoardAdjudicationSystem.ACCESS);
                Registry<ActionDispatcher.Handler> actions = new Registry<>(Map.of(
                        MOVE, new BoardMoveHandler(access, timer, adjudication, phase::get, id("move_effect")),
                        REP, new BoardCommandHandler(adjudication, AdjudicationAccess.Command.CLAIM_REPETITION, access, phase::get, id("claim_effect")),
                        QUIET, new BoardCommandHandler(adjudication, AdjudicationAccess.Command.CLAIM_QUIET, access, phase::get, id("claim_effect")),
                        ACCEPT, new BoardCommandHandler(adjudication, AdjudicationAccess.Command.ACCEPT_DRAW, access, phase::get, id("draw_effect")),
                        OFFER, new BoardCommandHandler(adjudication, AdjudicationAccess.Command.OFFER_DRAW, access, phase::get, id("offer_effect"))));
                dispatcher[0] = new ActionDispatcher(s, arenas, new RateLimiter(8, 100, 1), actions, 64, 100);
                bots[0] = new BotSystem(s, workers, dispatcher[0], s.services().require(BotDecisionSource.ACCESS), BotSystem.Config.parse(config)); return bots[0];
            }
        };
        Registry<SystemFactory> factories = new Registry<>(Map.of(MovementSystem.ID, new MovementSystem.Plan(), BoardSystem.ID, new BoardSystem.Plan(),
                TurnSystem.ID, new TurnSystem.Plan(clock::get), ObjectiveSystem.ID, new ObjectiveSystem.Plan(), BoardAdjudicationSystem.ID, new BoardAdjudicationSystem.Plan(),
                BoardDecisionSource.ID, new BoardDecisionSource.Plan(BoardBotStrategy.builtins(), s -> phase::get, s -> (actor, tick) -> new IntentGate.Facts(actor, tick, 0, permission.get())),
                BotSystem.ID, botFactory));
        if (saved == null) session.start(factories); else session.restore(factories, saved);
        UUID bot = people.stream().filter(p -> p.kind() == Participant.Kind.BOT).findFirst().orElseThrow().id();
        UUID player = people.stream().filter(p -> p.kind() == Participant.Kind.PLAYER).findFirst().orElseThrow().id();
        return new Match(session, session.services().require(BoardSystem.ACCESS), session.services().require(TurnSystem.ACCESS), session.services().require(ObjectiveSystem.ACCESS),
                bots[0], dispatcher[0], bot, player, clock, phase, permission);
    }
    private static void done(BotRuntime workers, UUID actor) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!workers.done(actor) && System.nanoTime() < until) Thread.sleep(1);
        equal(true, workers.done(actor));
    }
    private static void submit(Match match, BotRuntime workers, long tick) throws Exception { match.session.tick(tick); done(workers, match.bot); }
    public static void main(String[] ignored) throws Exception {
        try (BotRuntime workers = new BotRuntime(new ThreadGuard(), new Registry<>(Map.of()), 2, 4)) {
            for (String difficulty : List.of("easy", "normal", "hard")) {
                Match game = match(workers, start(), difficulty, null, null, false);
                var original = game.board.position(); Set<MovementRules.Move> legal = new HashSet<>(); new MovementRules(standard()).successors(original).forEach(s -> legal.add(s.move()));
                Map<String, Integer> counts = game.board.repetitionCounts(); submit(game, workers, 0);
                equal(original, game.board.position()); game.nanos.set(1_000_000_000L); game.session.tick(1);
                equal(1L, game.board.revision()); equal("black", game.clock.team()); equal("black", game.board.position().turn());
                equal(100_000_000_000L, game.clock.remainingNanos("white")); equal(true, legal.contains(game.board.history(0, 1).getFirst().move()));
                equal(1, counts.size()); equal(1, counts.values().iterator().next());
                equal(BotRuntime.Difficulty.valueOf(difficulty.toUpperCase(Locale.ROOT)), game.botSystem.assignments().get(game.bot));
                game.session.close(); equal(0, workers.pending()); equal(0, game.dispatcher.ownedCounts().get("grants"));
            }
            Match claim = match(workers, fen("5q1k/8/8/8/8/8/8/K7 w - - 99 60"), "normal", null, null, false);
            GridPosition original = claim.board.position(); submit(claim, workers, 0); claim.session.tick(1);
            equal(true, claim.objective.result().orElseThrow().draw()); equal(original, claim.board.position()); equal(false, claim.clock.running()); claim.session.close();
            Match mate = match(workers, fen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1"), "hard", null, null, false);
            submit(mate, workers, 0); mate.session.tick(1); equal(Set.of("white"), mate.objective.result().orElseThrow().winners()); equal(false, mate.clock.running()); mate.session.close();
            Match offer = match(workers, fen("5q1k/8/8/8/8/8/8/K7 w - - 0 1"), "easy", null, null, true);
            UUID token = offer.dispatcher.issueController(offer.player, 100);
            equal(true, offer.dispatcher.dispatchHuman(new IntentGate.Facts(offer.player, 0, 0, true),
                    new IntentGate.Intent(offer.session.id(), token, 0, offer.session.revision(), OFFER, Map.of())).accepted());
            submit(offer, workers, 0); offer.session.tick(1); equal(true, offer.objective.result().orElseThrow().draw()); equal(0L, offer.board.revision()); offer.session.close();
            Match denied = match(workers, start(), "normal", null, null, false); submit(denied, workers, 0); denied.permission.set(false); denied.session.tick(1);
            equal(0L, denied.board.revision()); denied.permission.set(true); submit(denied, workers, 3); denied.phase.set(false); denied.session.tick(4);
            equal(0L, denied.board.revision()); equal(0, workers.pending()); denied.session.close();
            Match expired = match(workers, start(), "normal", null, null, false); submit(expired, workers, 0); expired.nanos.set(100_000_000_000L); expired.session.tick(1);
            equal(0L, expired.board.revision()); equal(Set.of("black"), expired.objective.result().orElseThrow().winners()); equal(0, workers.pending()); expired.session.close();
            Match pending = match(workers, start(), "hard", null, null, false); submit(pending, workers, 0);
            List<Participant> people = new ArrayList<>(pending.session.participants().values()); Map<Id, GenericSession.SystemState> saved = pending.session.snapshot(); pending.session.close();
            Match restored = match(workers, start(), "hard", people, saved, false); submit(restored, workers, 0); restored.session.tick(1); equal(1L, restored.board.revision());
            equal("black", restored.clock.team()); restored.session.close();
            equal(0, workers.pending()); equal(0L, workers.metrics().get("failed"));
        }
        System.out.println("BoardBotSessionChecks: PASS (EASY/NORMAL/HARD real sessions, legal moves/clock increments, draw claims/offers, mate, phase/permission/expiry, immutable worker snapshots, pending restart)");
    }
}
