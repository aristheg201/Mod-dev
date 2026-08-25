package vn.svframe.mmocorefabric.runtime.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PlayerClassRuntime {
    private final String id;
    private final String name;
    private final int maxLevel;
    private final Map<String, ClassSkillRuntime> skills = new LinkedHashMap<>();

    public PlayerClassRuntime(String id, String name, int maxLevel) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.maxLevel = maxLevel;
    }

    public String id() { return id; }
    public String name() { return name; }
    public int maxLevel() { return maxLevel; }
    public boolean hasMaxLevel() { return maxLevel > 0; }

    public void registerSkill(ClassSkillRuntime skill) {
        Objects.requireNonNull(skill, "skill");
        if (skills.putIfAbsent(skill.handlerId(), skill) != null)
            throw new IllegalArgumentException("Duplicate class skill " + skill.handlerId());
    }

    public ClassSkillRuntime skill(String handlerId) { return skills.get(handlerId); }
    public Map<String, ClassSkillRuntime> skills() { return Map.copyOf(skills); }
}
