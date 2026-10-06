package io.github.aristheg201.cobblemonworld.boss;

import java.util.EnumMap;
import java.util.Map;

public final class RpgCombatState {
    private final Map<RpgSkill, Long> cooldownUntilTick = new EnumMap<>(RpgSkill.class);

    public boolean ready(RpgSkill skill, long gameTick) {
        return cooldownUntilTick.getOrDefault(skill, 0L) <= gameTick;
    }

    public void consume(RpgSkill skill, long gameTick, long cooldownTicks) {
        cooldownUntilTick.put(skill, gameTick + Math.max(0L, cooldownTicks));
    }

    public long remaining(RpgSkill skill, long gameTick) {
        return Math.max(0L, cooldownUntilTick.getOrDefault(skill, 0L) - gameTick);
    }

    public void reset() {
        cooldownUntilTick.clear();
    }
}
