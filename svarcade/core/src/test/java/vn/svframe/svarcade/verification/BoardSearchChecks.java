package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import vn.svframe.svarcade.bot.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.systems.board.*;
import vn.svframe.svarcade.systems.board.BoardOutcomeRules.Claim;
import static vn.svframe.svarcade.verification.BoardFixtures.*;

public final class BoardSearchChecks {
    private BoardSearchChecks() { }
    public static Node tuning(int depth, int table, int quiet, double randomness) {
        return new Node(Map.of("search", Map.of("depth", depth, "alpha_beta", true, "move_ordering", true,
                "transposition_table", table > 0, "table_capacity", table, "quiescence", quiet > 0,
                "quiescence_depth", quiet, "max_successors", 4096, "randomness", randomness),
                "evaluation", Map.of("win_score", 1_000_000, "mobility", 0,
                        "piece_values", Map.of("test:king", 0, "test:queen", 900, "test:rook", 500, "test:bishop", 330, "test:knight", 320, "test:pawn", 100))), "bot");
    }
    private static final MovementRules RULES = new MovementRules(standard());
    private static BoardSearch.Snapshot snapshot(GridPosition p, int count) { return new BoardSearch.Snapshot(p, Map.of(RULES.repetitionKey(p), count)); }
    private static BoundedSearch.Result<BoardSearch.Choice> search(GridPosition p, int depth, int table, int quiet, long nodes) {
        return new BoardSearch(standard(), AdjudicationFixtures.config(), BoardSearchTuning.parse(tuning(depth, table, quiet, 0)))
                .search(snapshot(p, 1), new ThinkBudget(10_000_000_000L, nodes), 1);
    }
    private static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    private static void legal(GridPosition p, BoardSearch.Choice choice) {
        if (choice.claim() == null) check(RULES.successors(p).stream().anyMatch(s -> s.move().equals(choice.move())), "Non-legal search move");
        else {
            var candidate = choice.move() == null ? p : RULES.apply(p, choice.move());
            check(new BoardOutcomeRules(AdjudicationFixtures.config(), standard()).claim(candidate, 3, choice.claim()).isPresent(), "Invalid search claim");
        }
    }
    public static void main(String[] ignored) {
        GridPosition start = start(); Map<String, Object> before = start.encode();
        for (int depth : List.of(1, 3, 5)) {
            var result = search(start, depth, 16, 1, 120); check(result.move().isPresent(), "No bounded legal fallback");
            check(result.operations() <= 120, "Node budget exceeded"); legal(start, result.move().orElseThrow());
        }
        check(before.equals(start.encode()), "Search mutated root");
        GridPosition mate = fen("7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        var mating = search(mate, 1, 32, 0, 5000).move().orElseThrow(); legal(mate, mating);
        var finish = new BoardOutcomeRules(AdjudicationFixtures.config(), standard()).automatic(RULES.apply(mate, mating.move()), 1).orElseThrow();
        check(!finish.draw() && finish.winners().contains("white"), "Missed mate in one");
        GridPosition ep = fen("7k/8/8/3pP3/8/8/8/K7 w - d6 0 2");
        var capture = search(ep, 1, 0, 0, 5000).move().orElseThrow();
        check(capture.move().from() == square("e5") && capture.move().to() == square("d6"), "Missed en passant material");
        GridPosition promotion = fen("7k/P7/2K5/8/8/8/8/8 w - - 0 1");
        var promote = search(promotion, 1, 0, 0, 5000).move().orElseThrow();
        check(id("queen").equals(promote.move().promotion()), "Promotion values not used");
        GridPosition pinned = fen("4r2k/8/8/8/8/8/4R3/4K3 w - - 0 1");
        legal(pinned, search(pinned, 3, 32, 1, 300).move().orElseThrow());
        GridPosition lost = fen("5q1k/8/8/8/8/8/8/K7 w - - 0 8");
        BoardSearch engine = new BoardSearch(standard(), AdjudicationFixtures.config(), BoardSearchTuning.parse(tuning(2, 32, 0, 0)));
        var claim = engine.search(snapshot(lost, 3), new ThinkBudget(10_000_000_000L, 5000), 2).move().orElseThrow();
        check(claim.claim() == Claim.REPETITION && claim.move() == null, "Losing bot did not claim available draw");
        var auto = engine.search(snapshot(lost, 5), new ThinkBudget(10_000_000_000L, 5000), 2);
        check(auto.move().isEmpty() && auto.score().orElseThrow() == 0, "Automatic repetition not terminal");
        GridPosition intended = fen("5q1k/8/8/8/8/8/8/K7 w - - 99 60");
        var intendedClaim = search(intended, 1, 0, 0, 5000).move().orElseThrow();
        check(intendedClaim.claim() == Claim.QUIET && intendedClaim.move() != null, "Intended-move draw missing"); legal(intended, intendedClaim);
        GridPosition bare = fen("7k/8/8/8/8/8/8/K7 w - - 0 1");
        check(search(bare, 3, 32, 1, 100).move().isEmpty(), "Insufficient material not terminal");
        for (int seed = 0; seed < 20; seed++) {
            var random = new BoardSearch(standard(), AdjudicationFixtures.config(), BoardSearchTuning.parse(tuning(1, 0, 0, 1)))
                    .search(snapshot(start, 1), new ThinkBudget(10_000_000_000L, 1000), seed);
            legal(start, random.move().orElseThrow());
        }
        GridPosition castling = fen("2k5/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        List<Double> cells = new ArrayList<>(Collections.nCopies(64, 0.0)); cells.set(square("g1"), 100.0);
        BoardSearchTuning original = BoardSearchTuning.parse(tuning(1, 0, 0, 0));
        BoardSearchTuning preferred = new BoardSearchTuning(original.search(), original.winScore(), 0, original.values(), Map.of("white", Map.of(id("king"), cells)));
        var castle = new BoardSearch(standard(), AdjudicationFixtures.config(), preferred)
                .search(snapshot(castling, 1), new ThinkBudget(10_000_000_000L, 1000), 2).move().orElseThrow();
        check(id("white_short").equals(castle.move().compound()), "Data-authored positional score/castling not used");
        AtomicLong checks = new AtomicLong();
        try { RULES.successors(start, () -> { if (checks.incrementAndGet() == 3) throw new CancellationException(); }); throw new AssertionError("No in-generator checkpoints"); }
        catch (CancellationException expected) { check(checks.get() == 3, "Cancellation delayed"); }
        AtomicLong clock = new AtomicLong();
        var expired = engine.search(snapshot(start, 1), new ThinkBudget(20, 1000, clock::getAndIncrement), 1);
        check(expired.exhausted() && expired.operations() <= 1000, "Move generation ignored clock");
        try { BoardSearchTuning.parse(new Node(Map.of("unknown", 1), "bad")); throw new AssertionError("Unknown tuning accepted"); }
        catch (ConfigException expected) { }
        try { engine.search(new BoardSearch.Snapshot(start, Map.of()), new ThinkBudget(10_000_000_000L, 100), 1); throw new AssertionError("Missing history accepted"); }
        catch (ConfigException expected) { }
        System.out.println("BoardSearchChecks: PASS (configured depth/values/position tables, legal fallback, mate, pinned move, EP, promotion, castling, claims/history/material, inner cancellation)");
    }
}
