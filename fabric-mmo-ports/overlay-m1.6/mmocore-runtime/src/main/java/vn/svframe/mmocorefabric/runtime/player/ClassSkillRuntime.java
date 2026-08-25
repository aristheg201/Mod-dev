package vn.svframe.mmocorefabric.runtime.player;

import java.util.Objects;

/** API-facing class-skill metadata. Identity follows MythicLib SkillHandler id. */
public record ClassSkillRuntime(
        String handlerId,
        int unlockLevel,
        int maxSkillLevel,
        boolean unlockedByDefault,
        boolean permanent,
        boolean upgradable,
        String trigger) {
    public ClassSkillRuntime {
        Objects.requireNonNull(handlerId, "handlerId");
        Objects.requireNonNull(trigger, "trigger");
        if (unlockLevel < 0) throw new IllegalArgumentException("unlockLevel < 0");
    }

    public boolean hasMaxLevel() { return maxSkillLevel > 0; }
}
