package vn.svframe.lively.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CombatCortex {
    public record CombatAction(String id, double immediateValue, double risk, Map<String, String> metadata) {
        public CombatAction { Objects.requireNonNull(id); metadata = Map.copyOf(metadata); }
    }
    public record Outcome(double value, double probability, CombatState nextState) {}
    @FunctionalInterface public interface Simulator { List<Outcome> simulate(CombatState state, CombatAction action); }
    public record CombatState(long revision, int turn, double aggression, double caution,
                              List<CombatAction> legalActions, Map<String, Double> features) {
        public CombatState { legalActions = List.copyOf(legalActions); features = Map.copyOf(features); }
    }
    public record Decision(CombatAction action, double expectedValue, long sourceRevision, int visitedNodes, boolean budgetExhausted) {}
    public record SearchBudget(int depth, int beamWidth, int maxNodes, long timeoutNanos) {
        public SearchBudget {
            depth = Math.max(1, Math.min(5, depth)); beamWidth = Math.max(1, Math.min(24, beamWidth));
            maxNodes = Math.max(8, Math.min(4096, maxNodes));
            timeoutNanos = Math.max(100_000L, Math.min(10_000_000L, timeoutNanos));
        }
        public static SearchBudget trainer(int skill) {
            int s = Math.max(1, Math.min(5, skill));
            return new SearchBudget(s <= 2 ? 1 : s - 1, 2 + s, 48 * s, 250_000L + s * 250_000L);
        }
    }

    public Optional<Decision> choose(CombatState state, Simulator simulator, int depth, int beamWidth) {
        return choose(state, simulator, new SearchBudget(depth, beamWidth, 512, 2_000_000L));
    }

    public Optional<Decision> choose(CombatState state, Simulator simulator, SearchBudget budget) {
        if (state.legalActions().isEmpty()) return Optional.empty();
        SearchContext context = new SearchContext(budget.maxNodes(), System.nanoTime() + budget.timeoutNanos());
        List<CombatAction> ranked = state.legalActions().stream()
                .sorted(Comparator.comparingDouble((CombatAction action) -> cheapScore(state, action)).reversed())
                .toList();
        CombatAction bestAction = ranked.getFirst();
        double bestValue = cheapScore(state, bestAction);
        for (CombatAction action : ranked) {
            if (!context.canVisit()) break;
            double value = evaluate(state, action, simulator, budget.depth(), budget.beamWidth(), context);
            if (value > bestValue) { bestValue = value; bestAction = action; }
        }
        return Optional.of(new Decision(bestAction, bestValue, state.revision(), context.visited, context.exhausted()));
    }

    private double cheapScore(CombatState state, CombatAction action) {
        return action.immediateValue()
                + state.aggression() * action.immediateValue() * 0.15D
                - state.caution() * action.risk() * 0.20D;
    }

    private double evaluate(CombatState state, CombatAction action, Simulator simulator, int depth, int width, SearchContext context) {
        if (!context.visit()) return action.immediateValue() - state.caution() * action.risk();
        double personality = state.aggression() * action.immediateValue() * 0.15D - state.caution() * action.risk() * 0.20D;
        if (depth <= 1) return action.immediateValue() + personality;
        List<Outcome> outcomes = simulator.simulate(state, action).stream()
                .filter(outcome -> outcome.probability() > 0D)
                .sorted(Comparator.comparingDouble((Outcome o) -> o.probability() * o.value()).reversed())
                .limit(width).toList();
        if (outcomes.isEmpty()) return action.immediateValue() + personality;
        double future = 0D;
        for (Outcome outcome : outcomes) {
            if (!context.canVisit()) break;
            double continuation = outcome.nextState() == null ? 0D : bestContinuation(outcome.nextState(), simulator, depth - 1, width, context);
            future += outcome.probability() * (outcome.value() + 0.72D * continuation);
        }
        return action.immediateValue() + personality + future;
    }

    private double bestContinuation(CombatState state, Simulator simulator, int depth, int width, SearchContext context) {
        return state.legalActions().stream().limit(width)
                .takeWhile(action -> context.canVisit())
                .mapToDouble(action -> evaluate(state, action, simulator, depth, width, context))
                .max().orElse(0D);
    }

    private static final class SearchContext {
        private final int maxNodes; private final long deadline; private int visited;
        private SearchContext(int maxNodes, long deadline) { this.maxNodes = maxNodes; this.deadline = deadline; }
        private boolean canVisit() { return visited < maxNodes && System.nanoTime() <= deadline; }
        private boolean visit() { if (!canVisit()) return false; visited++; return true; }
        private boolean exhausted() { return visited >= maxNodes || System.nanoTime() > deadline; }
    }
}
