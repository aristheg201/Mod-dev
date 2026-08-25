package vn.svframe.mmocorefabric.runtime.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus.ClassChangeReason;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus.LevelReason;

/** Core mutable player-data semantics reconstructed from MMOCore 1.13.1. */
public final class MMOCorePlayerDataRuntime {
    private final String playerId;
    private final MMOCoreEventBus events;
    private PlayerClassRuntime playerClass;
    private int level = 1;
    private int classPoints;
    private int skillPoints;
    private int attributePoints;
    private int attributeReallocationPoints;
    private int skillReallocationPoints;
    private int skillTreeReallocationPoints;
    private double experience;
    private final Map<String, Integer> skillLevels = new LinkedHashMap<>();

    public MMOCorePlayerDataRuntime(String playerId, MMOCoreEventBus events, PlayerClassRuntime initialClass) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.events = Objects.requireNonNull(events, "events");
        this.playerClass = Objects.requireNonNull(initialClass, "initialClass");
    }

    public String playerId() { return playerId; }
    public PlayerClassRuntime playerClass() { return playerClass; }
    public int level() { return level; }
    public double experience() { return experience; }
    public int classPoints() { return classPoints; }
    public int skillPoints() { return skillPoints; }
    public int attributePoints() { return attributePoints; }
    public int attributeReallocationPoints() { return attributeReallocationPoints; }
    public int skillReallocationPoints() { return skillReallocationPoints; }
    public int skillTreeReallocationPoints() { return skillTreeReallocationPoints; }

    public void loaded() { events.post(new MMOCoreEventBus.PlayerDataLoadEvent(playerId)); }

    public void setLevel(int requested, LevelReason reason) {
        int old = level;
        int next = Math.max(1, requested);
        if (playerClass.hasMaxLevel()) next = Math.min(playerClass.maxLevel(), next);
        if (old == next) return;
        level = next;
        if (reason != LevelReason.CHOOSE_PROFILE)
            events.post(new MMOCoreEventBus.PlayerLevelChangeEvent(playerId, old, next, reason));
    }

    public void setExperience(double value) { experience = Math.max(0D, value); }
    public void setClassPoints(int value) { classPoints = Math.max(0, value); }
    public void setSkillPoints(int value) { skillPoints = Math.max(0, value); }
    public void setAttributePoints(int value) { attributePoints = Math.max(0, value); }
    public void setAttributeReallocationPoints(int value) { attributeReallocationPoints = Math.max(0, value); }
    public void setSkillReallocationPoints(int value) { skillReallocationPoints = Math.max(0, value); }
    public void setSkillTreeReallocationPoints(int value) { skillTreeReallocationPoints = Math.max(0, value); }

    public int skillLevel(String handlerId) { return skillLevels.getOrDefault(handlerId, 1); }

    public void setSkillLevel(String handlerId, int value) {
        Objects.requireNonNull(handlerId, "handlerId");
        if (value <= 1) skillLevels.remove(handlerId);
        else skillLevels.put(handlerId, value);
    }

    public Map<String, Integer> mapSkillLevels() { return Map.copyOf(skillLevels); }

    public boolean changeClass(PlayerClassRuntime next, ClassChangeReason reason) {
        Objects.requireNonNull(next, "next");
        var event = events.post(new MMOCoreEventBus.PlayerChangeClassEvent(playerId, playerClass.id(), next.id(), reason));
        if (event.cancelled()) return false;
        playerClass = next;
        if (playerClass.hasMaxLevel() && level > playerClass.maxLevel())
            setLevel(level, LevelReason.CHOOSE_CLASS);
        return true;
    }

    public boolean giveExperience(double amount, String source) {
        var event = events.post(new MMOCoreEventBus.PlayerExperienceGainEvent(playerId, amount, source));
        if (event.cancelled()) return false;
        setExperience(experience + event.experience());
        return true;
    }
}
