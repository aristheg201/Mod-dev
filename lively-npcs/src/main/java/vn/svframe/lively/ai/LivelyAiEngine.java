package vn.svframe.lively.ai;

import vn.svframe.lively.memory.MemoryPolicy;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.WorldSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Offline domain-specific cognition. The core intentionally has no LLM provider.
 * Utility selects goals/actions from immutable perception, decayed memory, personality and learned outcomes.
 */
public final class LivelyAiEngine {
    private final MemoryPolicy memoryPolicy = new MemoryPolicy();

    public Optional<Decision> decide(NpcSnapshot npc, WorldSnapshot world) {
        Instant now = Instant.now();
        List<Goal> goals = new ArrayList<>();
        addNeed(goals, "satisfy_hunger", npc.need("hunger"));
        addNeed(goals, "earn_money", npc.need("money"));
        addNeed(goals, "socialize", npc.need("social"));
        addNeed(goals, "rest", npc.need("fatigue"));

        double entityThreat = world.entities().stream()
                .mapToDouble(WorldSnapshot.ObservedEntity::threat)
                .max().orElse(0D);
        double environmentThreat = clamp01(world.signals().getOrDefault("environment_threat", 0D));
        double memoryThreat = rememberedDanger(npc, now);
        double currentThreat = Math.max(entityThreat, environmentThreat);
        double threat = currentThreat > .18D ? clamp01(currentThreat + memoryThreat * .18D) : currentThreat;
        if (threat > .45D) goals.add(new Goal("respond_to_threat", threat, Map.of(
                "entity_threat", Double.toString(entityThreat),
                "environment_threat", Double.toString(environmentThreat),
                "memory_threat", Double.toString(memoryThreat))));
        if (goals.isEmpty()) goals.add(new Goal("maintain_routine", .25D, Map.of()));

        return goals.stream()
                .flatMap(goal -> actions(npc, goal).stream().map(action ->
                        new Decision(npc.id(), npc.revision(), world.revision(), goal, action,
                                score(npc, goal, action, now))))
                .max(Comparator.comparingDouble(Decision::score));
    }

    private double rememberedDanger(NpcSnapshot npc, Instant now) {
        return npc.recentMemories().stream()
                .filter(LivelyAiEngine::dangerousMemory)
                .mapToDouble(memory -> memoryPolicy.recallScore(memory, now))
                .max().orElse(0D);
    }

    private static boolean dangerousMemory(NpcSnapshot.MemoryView memory) {
        String type = memory.type().toLowerCase(Locale.ROOT);
        return type.contains("threat") || type.contains("crime") || type.equals("battle_lost")
                || type.equals("fled_from_threat") || type.equals("defensive_stance")
                || "THREAT".equalsIgnoreCase(memory.facts().getOrDefault("intent", ""));
    }

    private static void addNeed(List<Goal> goals, String type, double priority) {
        if (priority >= .35D) goals.add(new Goal(type, priority, Map.of()));
    }

    private List<AiAction> actions(NpcSnapshot npc, Goal goal) {
        return switch (goal.type()) {
            case "satisfy_hunger" -> List.of(
                    new AiAction("consume_food", Map.of(), .80D, AiAction.Risk.LOW),
                    new AiAction("seek_food", Map.of(), .60D, AiAction.Risk.LOW));
            case "earn_money" -> List.of(
                    new AiAction("perform_occupation", Map.of("role", npc.role()), .70D, AiAction.Risk.LOW),
                    new AiAction("observe_surroundings", Map.of(), .36D, AiAction.Risk.LOW));
            case "socialize" -> List.of(
                    new AiAction("observe_surroundings", Map.of(), .62D, AiAction.Risk.LOW),
                    new AiAction("wander", Map.of(), .48D, AiAction.Risk.LOW));
            case "rest" -> List.of(new AiAction("travel_home", Map.of(), .75D, AiAction.Risk.LOW));
            case "respond_to_threat" -> List.of(
                    new AiAction("flee", goal.context(), .90D, AiAction.Risk.LOW),
                    new AiAction("defend", goal.context(), .60D, AiAction.Risk.MEDIUM));
            default -> List.of(
                    new AiAction("wander", Map.of(), .55D, AiAction.Risk.LOW),
                    new AiAction("observe_surroundings", Map.of(), .42D, AiAction.Risk.LOW));
        };
    }

    private double score(NpcSnapshot npc, Goal goal, AiAction action, Instant now) {
        double score = .55D * goal.priority() + .45D * action.utility();
        if (action.type().equals("defend")) score += .24D * npc.trait("brave") + .08D * npc.trait("loyal");
        if (action.type().equals("flee")) score += .24D * (1D - npc.trait("brave"));
        if (action.type().equals("observe_surroundings")) score += .10D * npc.trait("friendly");
        if (action.type().equals("perform_occupation")) score += .08D * npc.trait("diligent");
        score += learnedActionBias(npc, action.type(), now);
        return score;
    }

    /** Recent successful actions become more attractive; repeated failures become less attractive and decay over time. */
    double learnedActionBias(NpcSnapshot npc, String actionType, Instant now) {
        double signed = 0D;
        double total = 0D;
        int used = 0;
        for (NpcSnapshot.MemoryView memory : npc.recentMemories()) {
            if (used >= 24 || !memory.type().equals("action_outcome")
                    || !actionType.equals(memory.facts().get("action"))) continue;
            double weight = memoryPolicy.recallScore(memory, now);
            if (weight <= 0D) continue;
            boolean success = Boolean.parseBoolean(memory.facts().getOrDefault("success", "false"));
            signed += (success ? 1D : -1D) * weight;
            total += weight;
            used++;
        }
        if (total <= 0D) return 0D;
        double direction = clampSigned(signed / total);
        double evidenceStrength = 1D - Math.exp(-total);
        return direction * evidenceStrength * .18D;
    }

    private static double clampSigned(double value) { return Math.max(-1D, Math.min(1D, value)); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
