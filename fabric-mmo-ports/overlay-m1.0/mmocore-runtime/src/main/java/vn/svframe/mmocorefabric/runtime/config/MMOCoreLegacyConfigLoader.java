package vn.svframe.mmocorefabric.runtime.config;

import vn.svframe.compat.YamlLite;
import vn.svframe.mmocorefabric.runtime.gameplay.ClassRuntime;
import vn.svframe.mmocorefabric.runtime.gameplay.ProfessionRuntime;
import vn.svframe.mmocorefabric.runtime.gameplay.QuestRuntime;
import vn.svframe.mmocorefabric.runtime.progression.LegacyClassDefinition;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MMOCoreLegacyConfigLoader {
    private static final Pattern AMOUNT = Pattern.compile("(?:^|[;{])\\s*amount\\s*=\\s*['\"]?(\\d+)", Pattern.CASE_INSENSITIVE);
    public record Result(int classes, int professions, int quests) {}
    private MMOCoreLegacyConfigLoader() {}

    public static Result load(Path root, ClassRuntime classes, ProfessionRuntime professions, QuestRuntime quests) throws IOException {
        classes.clearDefinitions(); professions.clearDefinitions(); quests.clearDefinitions();
        return new Result(loadClasses(root.resolve("classes"), classes), loadProfessions(root.resolve("professions"), professions), loadQuests(root.resolve("quests"), quests));
    }

    private static int loadClasses(Path dir, ClassRuntime runtime) throws IOException {
        int count = 0;
        for (Path file : yamlFiles(dir)) { runtime.register(id(file), LegacyClassDefinition.from(rootMap(file))); count++; }
        return count;
    }

    private static int loadProfessions(Path dir, ProfessionRuntime runtime) throws IOException {
        int count = 0;
        for (Path file : yamlFiles(dir)) {
            Map<String,Object> root = rootMap(file);
            Set<String> sources = new LinkedHashSet<>();
            collectExpSources(root.get("exp-sources"), sources);
            runtime.register(new ProfessionRuntime.Definition(id(file), integer(root.get("max-level"), 100), string(root.get("exp-curve"), "levels"), sources));
            count++;
        }
        return count;
    }

    private static int loadQuests(Path dir, QuestRuntime runtime) throws IOException {
        int count = 0;
        for (Path file : yamlFiles(dir)) {
            Map<String,Object> root = rootMap(file), levelReq = map(root.get("level-req"));
            int main = integer(levelReq.get("main"), 0), professionLevel = 0;
            String profession = "";
            for (var entry : levelReq.entrySet()) if (!entry.getKey().equalsIgnoreCase("main")) { profession = entry.getKey(); professionLevel = integer(entry.getValue(), 0); break; }
            long cooldown = Math.multiplyExact(Math.max(0L, longValue(root.get("delay"), 0L)), 3_600_000L);
            List<QuestRuntime.Objective> objectives = new ArrayList<>();
            for (var entry : map(root.get("objectives")).entrySet()) {
                Map<String,Object> objective = map(entry.getValue());
                String type = string(objective.get("type"), "");
                objectives.add(new QuestRuntime.Objective(entry.getKey(), amount(type), type, strings(objective.get("triggers"))));
            }
            runtime.register(new QuestRuntime.Definition(id(file), strings(root.get("parent")), main, profession, professionLevel, cooldown, objectives));
            count++;
        }
        return count;
    }

    private static void collectExpSources(Object value, Set<String> out) {
        if (value instanceof Map<?,?> map) {
            for (var entry : map.entrySet()) { String key = String.valueOf(entry.getKey()).trim(); if (!key.isEmpty()) out.add(key.toLowerCase(Locale.ROOT)); collectExpSources(entry.getValue(), out); }
        } else if (value instanceof List<?> list) for (Object item : list) collectExpSources(item, out);
        else if (value instanceof String string && !string.isBlank()) { String candidate = string.trim(); int brace = candidate.indexOf('{'); if (brace > 0) candidate = candidate.substring(0, brace); if (candidate.matches("[A-Za-z0-9_.:-]+")) out.add(candidate.toLowerCase(Locale.ROOT)); }
    }

    private static int amount(String type) { Matcher matcher = AMOUNT.matcher(type); if (!matcher.find()) return 1; try { return Math.max(1, Integer.parseInt(matcher.group(1))); } catch (NumberFormatException ignored) { return 1; } }
    private static List<Path> yamlFiles(Path dir) throws IOException { if (!Files.isDirectory(dir)) return List.of(); try (var stream = Files.list(dir)) { return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList(); } }
    private static Map<String,Object> rootMap(Path file) throws IOException { Object parsed = YamlLite.parse(file); if (!(parsed instanceof Map<?,?>)) throw new IOException("expected YAML map: " + file); return YamlLite.map(parsed); }
    private static String id(Path file) { String name = file.getFileName().toString(); int dot = name.lastIndexOf('.'); return (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.ROOT); }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return value instanceof Map<?,?> ? (Map<String,Object>) value : Map.of(); }
    private static List<String> strings(Object value) { if (value instanceof List<?> list) return list.stream().filter(Objects::nonNull).map(String::valueOf).toList(); if (value == null || value instanceof Map<?,?>) return List.of(); String string = String.valueOf(value).trim(); return string.isEmpty() ? List.of() : List.of(string); }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : parseInt(value, fallback); }
    private static int parseInt(Object value, int fallback) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }
    private static long longValue(Object value, long fallback) { return value instanceof Number n ? n.longValue() : parseLong(value, fallback); }
    private static long parseLong(Object value, long fallback) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }
}
