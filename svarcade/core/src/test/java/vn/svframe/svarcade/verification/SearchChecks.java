package vn.svframe.svarcade.verification;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import vn.svframe.svarcade.bot.*;

public final class SearchChecks {
    private SearchChecks() { }
    private record Tree(String id, boolean max, double value, boolean terminal, boolean forced, List<Tree> children) { }
    private static Tree leaf(String id, double value) { return new Tree(id, true, value, true, false, List.of()); }
    private static final BoundedSearch.Model<Tree, String, String> MODEL = new BoundedSearch.Model<>() {
        public boolean maximizing(Tree t) { return t.max(); }
        public OptionalDouble terminal(Tree t, ThinkBudget b) { b.check(); return t.terminal() ? OptionalDouble.of(t.value()) : OptionalDouble.empty(); }
        public double evaluate(Tree t, ThinkBudget b) { b.check(); return t.value(); }
        public boolean forced(Tree t, ThinkBudget b) { b.check(); return t.forced(); }
        public String key(Tree t, ThinkBudget b) { b.check(); return t.id(); }
        public List<BoundedSearch.Edge<Tree, String>> successors(Tree t, ThinkBudget b) {
            List<BoundedSearch.Edge<Tree, String>> out = new ArrayList<>();
            for (Tree child : t.children()) { b.check(); out.add(new BoundedSearch.Edge<>(child.id(), child, true, child.value())); }
            return out;
        }
    };
    private static BoundedSearch.Options options(int depth, boolean ab, boolean order, int table, int quiet, double random, long seed) {
        return new BoundedSearch.Options(depth, ab, order, table, quiet, 100, random, seed);
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] ignored) {
        Tree a = new Tree("a", false, 9, false, false, List.of(leaf("a1", -9), leaf("a2", 4)));
        Tree b = new Tree("b", false, 3, false, false, List.of(leaf("b1", 2), leaf("b2", 3)));
        Tree root = new Tree("root", true, 0, false, false, List.of(a, b));
        for (boolean ab : List.of(false, true)) for (boolean order : List.of(false, true)) for (int table : List.of(0, 2)) {
            var result = new BoundedSearch<>(MODEL, options(2, ab, order, table, 0, 0, 1)).search(root, new ThinkBudget(10_000_000_000L, 1000));
            check(result.move().orElseThrow().equals("b")); check(result.score().orElseThrow() == 2); check(result.completedDepth() == 2); check(result.tableEntries() <= table);
        }
        var quiet = new BoundedSearch<>(MODEL, options(1, true, true, 0, 1, 0, 1)).search(root, new ThinkBudget(10_000_000_000L, 1000));
        check(quiet.move().orElseThrow().equals("b"));
        Tree forced = new Tree("forced", false, -100, false, true, List.of(leaf("evasion", 5)));
        Tree forcedRoot = new Tree("forced-root", true, 0, false, false, List.of(forced, leaf("alternative", 3)));
        var evasion = new BoundedSearch<>(MODEL, options(1, true, true, 0, 1, 0, 1)).search(forcedRoot, new ThinkBudget(10_000_000_000L, 100));
        check(evasion.move().orElseThrow().equals("forced"));
        var limited = new BoundedSearch<>(MODEL, options(32, true, true, 1, 0, 0, 1)).search(root, new ThinkBudget(10_000_000_000L, 1));
        check(limited.exhausted()); check(limited.operations() == 1); check(limited.completedDepth() == 0); check(limited.move().isPresent()); check(limited.score().isEmpty());
        var partial = new BoundedSearch<>(MODEL, options(8, false, false, 1, 0, 0, 1)).search(root, new ThinkBudget(10_000_000_000L, 4));
        check(partial.completedDepth() == 1); check(partial.move().orElseThrow().equals("a")); check(partial.score().orElseThrow() == 9);
        var terminal = new BoundedSearch<>(MODEL, options(2, true, true, 1, 0, 0, 1)).search(leaf("end", 0), new ThinkBudget(10_000_000_000L, 10));
        check(terminal.move().isEmpty());
        Set<String> observed = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            var r = new BoundedSearch<>(MODEL, options(2, true, true, 1, 0, 1, i)).search(root, new ThinkBudget(10_000_000_000L, 100));
            observed.add(r.move().orElseThrow()); check(r.score().isEmpty());
        }
        check(observed.equals(Set.of("a", "b")));
        Tree shared = new Tree("shared", false, 2, false, false, List.of(leaf("s1", 2), leaf("s2", 3)));
        Tree dag = new Tree("dag", true, 0, false, false, List.of(
                new Tree("p", false, 0, false, false, List.of(shared)), new Tree("q", false, 0, false, false, List.of(shared))));
        var cached = new BoundedSearch<>(MODEL, options(3, false, true, 10, 0, 0, 1)).search(dag, new ThinkBudget(10_000_000_000L, 1000));
        check(cached.tableHits() > 0);
        SplittableRandom generated = new SplittableRandom(9122026);
        for (int sample = 0; sample < 200; sample++) {
            List<Tree> layer = new ArrayList<>();
            for (int i = 0; i < 4; i++) layer.add(leaf("leaf-" + i, generated.nextInt(-20, 21)));
            for (int level = 3; level >= 0; level--) {
                List<Tree> upper = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    List<Tree> children = new ArrayList<>();
                    for (Tree child : layer) if (generated.nextBoolean()) children.add(child);
                    if (children.isEmpty()) children.add(layer.getFirst());
                    upper.add(new Tree("level-" + level + "-" + i, level % 2 == 0, generated.nextInt(-20, 21), false, false, List.copyOf(children)));
                }
                layer = upper;
            }
            Tree position = layer.getFirst();
            double oracle = new BoundedSearch<>(MODEL, options(4, false, false, 0, 0, 0, 1))
                    .search(position, new ThinkBudget(10_000_000_000L, 100_000)).score().orElseThrow();
            for (int cap : List.of(0, 2, 32)) for (boolean order : List.of(false, true)) {
                var actual = new BoundedSearch<>(MODEL, options(4, true, order, cap, 0, 0, 1))
                        .search(position, new ThinkBudget(10_000_000_000L, 100_000));
                check(actual.completedDepth() == 4); check(actual.score().orElseThrow() == oracle); check(actual.tableEntries() <= cap);
            }
        }
        AtomicLong now = new AtomicLong(); ThinkBudget time = new ThinkBudget(10, 100, now::get); now.set(10);
        var timed = new BoundedSearch<>(MODEL, options(2, true, true, 0, 0, 0, 1)).search(root, time);
        check(timed.exhausted()); check(timed.move().isEmpty());
        Thread.currentThread().interrupt();
        try { new BoundedSearch<>(MODEL, options(2, true, true, 0, 0, 0, 1)).search(root, new ThinkBudget(1000, 1)); throw new AssertionError("Cancellation ignored"); }
        catch (CancellationException expected) { check(!(expected instanceof ThinkBudget.Exhausted)); }
        finally { Thread.interrupted(); }
        System.out.println("SearchChecks: PASS (minimax, alpha-beta, ordering, bounded TT, quiescence/evasions, iterative fallback, random legality, time/nodes/cancellation)");
    }
}
