package vn.svframe.lively.ai;

import java.util.Map;
import java.util.Objects;

/** Proposal only. AI never executes game mutations directly. */
public record AiAction(String type, Map<String, String> args, double utility, Risk risk) {
    public AiAction {
        Objects.requireNonNull(type);
        args = Map.copyOf(args);
        utility = Math.max(-1D, Math.min(1D, utility));
        Objects.requireNonNull(risk);
    }

    public enum Risk { LOW, MEDIUM, HIGH, PRIVILEGED }
}
