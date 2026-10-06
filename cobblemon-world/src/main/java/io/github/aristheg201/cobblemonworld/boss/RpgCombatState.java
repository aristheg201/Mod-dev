package io.github.aristheg201.cobblemonworld.boss;

import java.util.EnumMap;
import java.util.Map;

public final class RpgCombatState {
    private final Map<RpgSkill, Long> cooldownUntilTick = new EnumMap<>(RpgSkill.class);
    private long invulnerableUntilTick;
    private long guardUntilTick;
    private long anchorUntilTick;
    private int corruption;

    public boolean ready(RpgSkill skill, long gameTick) {
        return cooldownUntilTick.getOrDefault(skill, 0L) <= gameTick;
    }

    public void consume(RpgSkill skill, long gameTick, long cooldownTicks) {
        cooldownUntilTick.put(skill, gameTick + Math.max(0L, cooldownTicks));
    }

    public long remaining(RpgSkill skill, long gameTick) {
        return Math.max(0L, cooldownUntilTick.getOrDefault(skill, 0L) - gameTick);
    }

    public void setInvulnerableUntil(long tick) { invulnerableUntilTick = Math.max(invulnerableUntilTick, tick); }
    public void setGuardUntil(long tick) { guardUntilTick = Math.max(guardUntilTick, tick); }
    public void setAnchorUntil(long tick) { anchorUntilTick = Math.max(anchorUntilTick, tick); }

    public boolean protectedAt(long tick) {
        return tick <= invulnerableUntilTick || tick <= guardUntilTick;
    }

    public boolean anchoredAt(long tick) {
        return tick <= anchorUntilTick;
    }

    public int corruption() { return corruption; }
    public void addCorruption(int amount) { corruption = Math.max(0, Math.min(10, corruption + amount)); }
    public void purge() { corruption = 0; }

    public void reset() {
        cooldownUntilTick.clear();
        invulnerableUntilTick = 0L;
        guardUntilTick = 0L;
        anchorUntilTick = 0L;
        corruption = 0;
    }
}
