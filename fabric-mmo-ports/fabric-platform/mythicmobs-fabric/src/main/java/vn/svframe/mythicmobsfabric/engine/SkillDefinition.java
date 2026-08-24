package vn.svframe.mythicmobsfabric.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record SkillDefinition(String id, double cooldownSeconds, List<String> conditions,
                              List<SkillLine> lines, boolean stopIfNoTargets) {
    public static SkillDefinition from(String id, Map<String, Object> section) {
        double cooldown = number(section, "Cooldown", number(section, "cooldown", 0.0));
        boolean stop = bool(section, "StopIfNoTargets", bool(section, "stopifnotargets", false));
        List<String> conditions = strings(section.getOrDefault("Conditions", section.get("conditions")));
        Object rawSkills = section.containsKey("Skills") ? section.get("Skills") : section.get("skills");
        List<SkillLine> lines = new ArrayList<>();
        for (String raw : strings(rawSkills)) {
            try { lines.add(SkillLine.parse(raw)); }
            catch (IllegalArgumentException ignored) {}
        }
        return new SkillDefinition(id, Math.max(0, cooldown), List.copyOf(conditions), List.copyOf(lines), stop);
    }
    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(value));
    }
    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key); if (value instanceof Number n) return n.doubleValue();
        if (value != null) try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ignored) {}
        return fallback;
    }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key); return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
