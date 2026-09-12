package vn.svframe.svarcade.bot;

import java.util.*;

/** Iterative minimax over immutable states; no named games or difficulty presets. */
public final class BoundedSearch<S, M, K> {
    public record Options(int depth, boolean alphaBeta, boolean moveOrdering, int tableCapacity,
                          int quiescenceDepth, int maxSuccessors, double randomness, long seed) {
        public Options {
            if (depth < 1 || depth > 32 || tableCapacity < 0 || tableCapacity > 200_000
                    || quiescenceDepth < 0 || quiescenceDepth > 16 || maxSuccessors < 1 || maxSuccessors > 8192
                    || !Double.isFinite(randomness) || randomness < 0 || randomness > 1) throw new IllegalArgumentException("Search limits");
        }
    }
    public record Edge<S, M>(M move, S state, boolean tactical, double priority) {
        public Edge { Objects.requireNonNull(move); Objects.requireNonNull(state); finite(priority); }
    }
    public interface Model<S, M, K> {
        boolean maximizing(S state);
        OptionalDouble terminal(S state, ThinkBudget budget);
        double evaluate(S state, ThinkBudget budget);
        List<Edge<S, M>> successors(S state, ThinkBudget budget);
        boolean forced(S state, ThinkBudget budget);
        /** Must include every rule-relevant component, including draw/history rights. */
        K key(S state, ThinkBudget budget);
    }
    public record Result<M>(Optional<M> move, OptionalDouble score, int completedDepth, long operations,
                            long tableHits, int tableEntries, boolean exhausted) { }
    private enum Bound { EXACT, LOWER, UPPER }
    private record Entry<M>(int depth, double value, Bound bound, M best) { }
    private record Iteration<M>(M move, double value) { }
    private final Model<S, M, K> model;
    private final Options options;
    public BoundedSearch(Model<S, M, K> model, Options options) {
        this.model = Objects.requireNonNull(model); this.options = Objects.requireNonNull(options);
    }
    public Result<M> search(S root, ThinkBudget budget) { return new Run(budget).execute(Objects.requireNonNull(root)); }
    private static double finite(double score) {
        if (!Double.isFinite(score) || Math.abs(score) > 1_000_000_000) throw new IllegalArgumentException("Search score outside finite bounds");
        return score;
    }
    private final class Run {
        private final ThinkBudget budget;
        private final LinkedHashMap<K, Entry<M>> table = new LinkedHashMap<>();
        private long hits;
        Run(ThinkBudget budget) { this.budget = Objects.requireNonNull(budget); }
        Result<M> execute(S root) {
            M best = null; OptionalDouble score = OptionalDouble.empty(); int completed = 0; boolean exhausted = false;
            List<Edge<S, M>> roots = List.of();
            try {
                budget.check(); OptionalDouble terminal = model.terminal(root, budget);
                if (terminal.isPresent()) return result(null, OptionalDouble.of(finite(terminal.getAsDouble())), 0, false);
                roots = edges(root); if (roots.isEmpty()) throw new IllegalStateException("Nonterminal state without successors");
                best = roots.getFirst().move();
                for (int depth = 1; depth <= options.depth(); depth++) {
                    Iteration<M> iteration = rootIteration(root, roots, depth, best);
                    best = iteration.move(); score = OptionalDouble.of(iteration.value()); completed = depth;
                }
            } catch (ThinkBudget.Exhausted end) { exhausted = true; }
            // Do not accidentally convert interruption/shutdown into a playable fallback.
            budget.checkCancellation();
            if (best != null && options.randomness() > 0) {
                SplittableRandom random = new SplittableRandom(options.seed());
                if (random.nextDouble() < options.randomness()) {
                    best = roots.get(random.nextInt(roots.size())).move(); score = OptionalDouble.empty();
                }
            }
            return result(best, score, completed, exhausted);
        }
        private Result<M> result(M move, OptionalDouble score, int depth, boolean exhausted) {
            return new Result<>(Optional.ofNullable(move), score, depth, budget.operations(), hits, table.size(), exhausted);
        }
        private Iteration<M> rootIteration(S root, List<Edge<S, M>> roots, int depth, M previous) {
            budget.visit(); boolean max = model.maximizing(root);
            double best = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            double alpha = Double.NEGATIVE_INFINITY, beta = Double.POSITIVE_INFINITY; M move = null;
            for (Edge<S, M> edge : ordered(roots, previous)) {
                budget.check(); double value = node(edge.state(), depth - 1, alpha, beta);
                if (move == null || (max ? value > best : value < best)) { best = value; move = edge.move(); }
                if (options.alphaBeta()) { if (max) alpha = Math.max(alpha, best); else beta = Math.min(beta, best); }
            }
            return new Iteration<>(Objects.requireNonNull(move), best);
        }
        private double node(S state, int depth, double alpha, double beta) {
            budget.visit(); OptionalDouble terminal = model.terminal(state, budget);
            if (terminal.isPresent()) return finite(terminal.getAsDouble());
            if (depth == 0) return quiet(state, options.quiescenceDepth(), alpha, beta);
            K key = null; Entry<M> cached = null;
            if (options.tableCapacity() > 0) {
                key = Objects.requireNonNull(model.key(state, budget)); cached = table.get(key);
                // Equal-depth values preserve fixed-horizon minimax semantics.
                if (cached != null && cached.depth() == depth) {
                    hits++;
                    if (cached.bound() == Bound.EXACT) return cached.value();
                    if (options.alphaBeta()) {
                        if (cached.bound() == Bound.LOWER) alpha = Math.max(alpha, cached.value());
                        else beta = Math.min(beta, cached.value());
                        if (alpha >= beta) return cached.value();
                    }
                }
            }
            double alphaStart = alpha, betaStart = beta;
            boolean max = model.maximizing(state); double best = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY; M bestMove = null;
            List<Edge<S, M>> successors = edges(state);
            if (successors.isEmpty()) throw new IllegalStateException("Nonterminal state without successors");
            for (Edge<S, M> edge : ordered(successors, cached == null ? null : cached.best())) {
                budget.check(); double value = node(edge.state(), depth - 1, alpha, beta);
                if (bestMove == null || (max ? value > best : value < best)) { best = value; bestMove = edge.move(); }
                if (options.alphaBeta()) {
                    if (max) alpha = Math.max(alpha, best); else beta = Math.min(beta, best);
                    if (alpha >= beta) break;
                }
            }
            if (key != null) {
                Bound bound = !options.alphaBeta() ? Bound.EXACT : best <= alphaStart ? Bound.UPPER : best >= betaStart ? Bound.LOWER : Bound.EXACT;
                if (!table.containsKey(key) && table.size() == options.tableCapacity()) table.remove(table.keySet().iterator().next());
                table.put(key, new Entry<>(depth, best, bound, bestMove));
            }
            return best;
        }
        private double quiet(S state, int remaining, double alpha, double beta) {
            budget.check(); double evaluation = finite(model.evaluate(state, budget));
            if (remaining == 0) return evaluation;
            boolean max = model.maximizing(state), forced = model.forced(state, budget);
            double best = forced ? (max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY) : evaluation;
            if (!forced && options.alphaBeta()) {
                if (max) alpha = Math.max(alpha, best); else beta = Math.min(beta, best);
                if (alpha >= beta) return best;
            }
            boolean expanded = false;
            for (Edge<S, M> edge : ordered(edges(state), null)) {
                budget.check(); if (!forced && !edge.tactical()) continue;
                expanded = true; budget.visit(); OptionalDouble terminal = model.terminal(edge.state(), budget);
                double value = terminal.isPresent() ? finite(terminal.getAsDouble()) : quiet(edge.state(), remaining - 1, alpha, beta);
                best = max ? Math.max(best, value) : Math.min(best, value);
                if (options.alphaBeta()) {
                    if (max) alpha = Math.max(alpha, best); else beta = Math.min(beta, best);
                    if (alpha >= beta) break;
                }
            }
            if (forced && !expanded) throw new IllegalStateException("Forced nonterminal state without evasions");
            return best;
        }
        private List<Edge<S, M>> edges(S state) {
            budget.check(); List<Edge<S, M>> edges = Objects.requireNonNull(model.successors(state, budget));
            if (edges.size() > options.maxSuccessors()) throw new IllegalArgumentException("Search successor bound");
            budget.check(); return List.copyOf(edges);
        }
        private List<Edge<S, M>> ordered(List<Edge<S, M>> edges, M principal) {
            if (!options.moveOrdering()) return edges;
            List<Edge<S, M>> copy = new ArrayList<>(edges);
            copy.sort((a, b) -> {
                budget.check(); int first = Boolean.compare(Objects.equals(b.move(), principal), Objects.equals(a.move(), principal));
                return first != 0 ? first : Double.compare(b.priority(), a.priority());
            });
            return copy;
        }
    }
}
