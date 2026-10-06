package io.github.aristheg201.cobblemonworld.boss;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

public final class TobaSkillRegistry {
    public static final TobaSkillRegistry INSTANCE = new TobaSkillRegistry();
    private static final Gson GSON = new Gson();
    private final Map<RpgSkill, Definition> skills = new EnumMap<>(RpgSkill.class);

    private TobaSkillRegistry() {}

    public void load() {
        String path = "data/cobblemonworld/boss/toba_skills.json";
        try (var stream = TobaSkillRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            Root root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Root.class);
            skills.clear();
            if (root != null && root.skills != null) {
                for (var entry : root.skills.entrySet()) {
                    RpgSkill id = RpgSkill.valueOf(entry.getKey().toUpperCase(java.util.Locale.ROOT));
                    Definition raw = entry.getValue();
                    skills.put(id, raw.normalized());
                }
            }
            for (RpgSkill skill : RpgSkill.values()) {
                if (!skills.containsKey(skill)) throw new IllegalStateException("Missing TOBA skill definition: " + skill);
            }
            CobblemonWorldMod.LOGGER.info("Loaded {} TOBA RPG skill definitions.", skills.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load TOBA RPG skill definitions", e);
        }
    }

    public Definition get(RpgSkill skill) {
        Definition definition = skills.get(skill);
        if (definition == null) throw new IllegalStateException("TOBA skills not loaded: " + skill);
        return definition;
    }

    public static final class Root {
        public Map<String, Definition> skills;
    }

    public static final class Definition {
        public String resource = "NONE";
        public double cost;
        public long cooldownTicks;
        public int durationTicks;
        public double magnitude;
        public double secondaryMagnitude;
        public double maxBonus;

        Definition normalized() {
            resource = resource == null ? "NONE" : resource.trim().toUpperCase(java.util.Locale.ROOT);
            SvFrameRpgBridge.Resource.valueOf(resource);
            cost = Math.max(0, cost);
            cooldownTicks = Math.max(1, cooldownTicks);
            durationTicks = Math.max(0, durationTicks);
            magnitude = Math.max(0, magnitude);
            secondaryMagnitude = Math.max(0, secondaryMagnitude);
            maxBonus = Math.max(0, maxBonus);
            return this;
        }

        public SvFrameRpgBridge.Resource resourceType() {
            return SvFrameRpgBridge.Resource.valueOf(resource);
        }
    }
}
