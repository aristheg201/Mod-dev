package vn.svframe.lively.ai;

import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.WorldSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Offline domain-specific cognition. The core intentionally has no LLM provider.
 * Utility selects goals/actions while richer planners can be registered later.
 */
public final class LivelyAiEngine {
    public Optional<Decision> decide(NpcSnapshot npc, WorldSnapshot world) {
        List<Goal> goals = new ArrayList<>();
        addNeed(goals, "satisfy_hunger", npc.need("hunger"));
        addNeed(goals, "earn_money", npc.need("money"));
        addNeed(goals, "socialize", npc.need("social"));
        addNeed(goals, "rest", npc.need("fatigue"));

        double threat = world.entities().stream()
                .mapToDouble(WorldSnapshot.ObservedEntity::threat)
                .max().orElse(0D);
        if (threat > 0.60D) goals.add(new Goal("respond_to_threat", threat, Map.of()));
        if (goals.isEmpty()) goals.add(new Goal("maintain_routine", 0.25D, Map.of()));

        return goals.stream()
                .flatMap(goal -> actions(npc, goal).stream().map(action ->
                        new Decision(npc.id(), npc.revision(), world.revision(), goal, action, score(npc, goal, action))))
                .max(Comparator.comparingDouble(Decision::score));
    }

    private static void addNeed(List<Goal> goals, String type, double priority) {
        if (priority >= 0.35D) goals.add(new Goal(type, priority, Map.of()));
    }

    private List<AiAction> actions(NpcSnapshot npc, Goal goal) {
        return switch (goal.type()) {
            case "satisfy_hunger" -> List.of(
                    new AiAction("consume_food", Map.of(), 0.80D, AiAction.Risk.LOW),
                    new AiAction("seek_food", Map.of(), 0.60D, AiAction.Risk.LOW));
            case "earn_money" -> List.of(
                    new AiAction("perform_occupation", Map.of("role", npc.role()), 0.70D, AiAction.Risk.LOW),
                    new AiAction("offer_trade", Map.of(), 0.50D, AiAction.Risk.MEDIUM));
            case "socialize" -> List.of(new AiAction("start_dialogue", Map.of(), 0.70D, AiAction.Risk.LOW));
            case "rest" -> List.of(new AiAction("travel_home", Map.of(), 0.75D, AiAction.Risk.LOW));
            case "respond_to_threat" -> List.of(
                    new AiAction("flee", Map.of(), 0.90D, AiAction.Risk.LOW),
                    new AiAction("defend", Map.of(), 0.60D, AiAction.Risk.MEDIUM));
            default -> List.of(new AiAction("perform_occupation", Map.of("role", npc.role()), 0.35D, AiAction.Risk.LOW));
        };
    }

    private double score(NpcSnapshot npc, Goal goal, AiAction action) {
        double score = 0.55D * goal.priority() + 0.45D * action.utility();
        if (action.type().equals("defend")) score += 0.20D * npc.trait("brave");
        if (action.type().equals("flee")) score += 0.20D * (1D - npc.trait("brave"));
        if (action.type().equals("start_dialogue")) score += 0.15D * npc.trait("friendly");
        if (action.type().equals("offer_trade")) score += 0.12D * npc.trait("greedy");
        return score;
    }
}
