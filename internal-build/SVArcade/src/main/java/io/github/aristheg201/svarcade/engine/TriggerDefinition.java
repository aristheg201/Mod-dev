package io.github.aristheg201.svarcade.engine;

import java.util.List;

public record TriggerDefinition(String id, BattleEvent event, int priority, double chance,
                                long cooldownMs, int every, boolean oncePerCombat, boolean oncePerTarget,
                                List<Condition> conditions, List<EffectDefinition> effects) {
    public TriggerDefinition {
        if (id == null || id.isBlank() || event == null) throw new IllegalArgumentException("trigger.id/event required");
        if (!Double.isFinite(chance) || chance < 0 || chance > 1 || cooldownMs < 0 || every < 1)
            throw new IllegalArgumentException("trigger " + id + ": invalid chance/cooldown/every");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
    public record Condition(String kind, String key, double value) {
        public Condition {
            if (kind == null || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid condition");
            key = key == null ? "" : key;
        }
    }
}
