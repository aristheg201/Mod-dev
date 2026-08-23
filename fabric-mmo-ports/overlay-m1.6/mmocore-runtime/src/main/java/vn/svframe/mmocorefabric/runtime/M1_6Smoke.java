package vn.svframe.mmocorefabric.runtime;

import java.util.ArrayList;
import java.util.List;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus.ClassChangeReason;
import vn.svframe.mmocorefabric.runtime.event.MMOCoreEventBus.LevelReason;
import vn.svframe.mmocorefabric.runtime.player.ClassSkillRuntime;
import vn.svframe.mmocorefabric.runtime.player.MMOCorePlayerDataRuntime;
import vn.svframe.mmocorefabric.runtime.player.PlayerClassRuntime;

public final class M1_6Smoke {
    public static void main(String[] args) {
        MMOCoreEventBus bus = new MMOCoreEventBus();
        List<MMOCoreEventBus.Event> seen = new ArrayList<>();
        bus.register(seen::add);

        PlayerClassRuntime mage = new PlayerClassRuntime("MAGE", "Mage", 100);
        mage.registerSkill(new ClassSkillRuntime("FIREBALL", 1, 10, true, false, true, "CAST"));
        MMOCorePlayerDataRuntime data = new MMOCorePlayerDataRuntime("player", bus, mage);

        data.setLevel(0, LevelReason.COMMAND);
        require(data.level() == 1 && seen.isEmpty(), "level min clamp/no-op");
        data.setLevel(150, LevelReason.COMMAND);
        require(data.level() == 100, "class max clamp");
        require(seen.size() == 1 && seen.get(0) instanceof MMOCoreEventBus.PlayerLevelChangeEvent, "level event");
        data.setLevel(50, LevelReason.CHOOSE_PROFILE);
        require(seen.size() == 1, "CHOOSE_PROFILE suppresses level event");

        data.setExperience(-4); data.setSkillPoints(-2); data.setClassPoints(-3); data.setAttributePoints(-9);
        require(data.experience() == 0 && data.skillPoints() == 0 && data.classPoints() == 0 && data.attributePoints() == 0, "non-negative setters");

        require(data.skillLevel("FIREBALL") == 1, "default skill level is one");
        data.setSkillLevel("FIREBALL", 4);
        require(data.skillLevel("FIREBALL") == 4, "skill level set");
        data.setSkillLevel("FIREBALL", 1);
        require(!data.mapSkillLevels().containsKey("FIREBALL") && data.skillLevel("FIREBALL") == 1, "level one stored implicitly");

        bus.register(event -> {
            if (event instanceof MMOCoreEventBus.PlayerExperienceGainEvent exp) exp.setExperience(exp.experience() * 2);
        });
        data.giveExperience(10, "SMOKE");
        require(data.experience() == 20, "mutable experience event");

        PlayerClassRuntime warrior = new PlayerClassRuntime("WARRIOR", "Warrior", 20);
        require(data.changeClass(warrior, ClassChangeReason.GUI), "class change");
        require(data.playerClass() == warrior && data.level() == 20, "new class clamps current level");

        System.out.println("MMOCORE_CLASS_SKILL_PLAYERDATA_EVENT_RUNTIME=PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
