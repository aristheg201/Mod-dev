package vn.svframe.lively.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Game-agnostic tactical search. Integration supplies legal actions and simulated outcomes.
 * Difficulty changes search budget/knowledge, never hidden-state cheating.
 */
public final class CombatCortex {
    public record CombatAction(String id, double immediateValue, double risk, Map<String, String> metadata) {
        public CombatAction { Objects.requireNonNull(id); metadata = Map.copyOf(metadata); }
    }

    public record Outcome(double value, double probability, CombatState nextState) {}

    @FunctionalInterface
    public interface Simulator { List<Outcome> simulate(CombatState state, CombatAction action); }

    public record CombatState(
            long revision,
            int turn,
            double aggression,
            double caution,
            List<CombatAction> legalActions,
            Map<String, Double> features
    ) {
        public CombatState { legalActions = List.copyOf(legalActions); features = Map.copyOf(features); }
    }

    public record Decision(CombatAction action, double expectedValue, long sourceRevision) {}

    public Optional<Decision> choose(CombatState state, Simulator simulator, int depth, int beamWidth) {
        if (state.legalActions().isEmpty()) return Optional.empty();
        int safeDepth = Math.max(1, Math.min(4, depth));
        int width = Math.max(1, Math.min(16, beamWidth));
        return state.legalActions().stream()
                .map(action -> new Decision(action, evaluate(state, action, simulator, safeDepth, width), state.revision()))
                .max(Comparator.comparingDouble(Decision::expectedValue));
    }

    private double evaluate(CombatState state, CombatAction action, Simulator simulator, int depth, int width) {
        double personality = state.aggression() * action.immediateValue() * 0.15D - state.caution() * action.risk() * 0.20D;
        if (depth <= 1) return action.immediateValue() + personality;

        List<Outcome> outcomes = simulator.simulate(state, action).stream()
                .sorted(Comparator.comparingDouble((Outcome o) -> o.probability() * o.value()).reversed())
                .limit(width)
                .toList();
        if (outcomes.isEmpty()) return action.immediateValue() + personality;

        double future = 0D;
        for (Outcome outcome : outcomes) {
            double continuation = outcome.nextState() == null ? 0D : bestContinuation(outcome.nextState(), simulator, depth - 1, width);
            future += outcome.probability() * (outcome.value() + 0.72D * continuation);
        }
        return action.immediateValue() + personality + future;
    }

    private double bestContinuation(CombatState state, Simulator simulator, int depth, int width) {
        return state.legalActions().stream()
                .mapToDouble(action -> evaluate(state, action, simulator, depth, width))
                .max().orElse(0D);
    }
}
