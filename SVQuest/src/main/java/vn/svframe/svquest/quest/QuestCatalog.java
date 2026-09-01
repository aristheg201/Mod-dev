package vn.svframe.svquest.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Runtime quest catalog. Quest content lives in JSON, never in Java.
 *
 * Server files: config/svquest/quests/*.json
 * Client catalog: replaced by the server-synchronised JSON catalog after login.
 */
public final class QuestCatalog {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_RESOURCE = "/svquest/defaults/quests.json";

    private QuestCatalog() {}

    public record Objective(String type, String target, String metaKey, long amount, String mode,
                            String label, String featureId) {
        public boolean maxMode() { return "max".equalsIgnoreCase(mode); }
    }

    public record Reward(String type, String item, int count, long amount, String command, String label) {}

    public record Quest(String id, String category, String phase, String title, String description,
                        List<Objective> objectives, List<Reward> rewards, List<String> prerequisites) {}

    public static volatile List<Quest> QUESTS = List.of();
    public static volatile Map<String, Quest> BY_ID = Map.of();
    private static volatile String compressedCatalog = "";

    static {
        try {
            install(parseDocument(readResourceStrict(DEFAULT_RESOURCE), DEFAULT_RESOURCE), DEFAULT_RESOURCE);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(new IllegalStateException("Could not load SVQuest default config catalog", t));
        }
    }

    /** Reloads every *.json file from config/svquest/quests in lexical filename order. */
    public static synchronized int reloadFromConfig() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("svquest/quests");
        try {
            Files.createDirectories(dir);
            List<Path> files;
            try (var stream = Files.list(dir)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted()
                        .toList();
            }
            if (files.isEmpty()) {
                Path starter = dir.resolve("00-starter.json");
                try (InputStream in = QuestCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in == null) throw new IOException("Missing bundled config template " + DEFAULT_RESOURCE);
                    Files.copy(in, starter, StandardCopyOption.REPLACE_EXISTING);
                }
                files = List.of(starter);
                SVQuest.LOGGER.info("Created editable SVQuest quest config: {}", starter);
            }

            ArrayList<Quest> loaded = new ArrayList<>();
            for (Path file : files) loaded.addAll(parseDocument(readStrict(file), file.toString()));
            install(loaded, dir.toString());
            return loaded.size();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reload SVQuest config catalog", e);
        }
    }

    /** Installs a catalog received from the authoritative server on the client. */
    public static synchronized int installRemoteJson(String json) {
        try {
            List<Quest> loaded = parseDocument(json, "server-sync");
            install(loaded, "server-sync");
            return loaded.size();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to install server SVQuest catalog", e);
        }
    }

    public static synchronized int installRemoteCompressedBase64(String encoded) {
        try {
            byte[] compressed = Base64.getDecoder().decode(encoded);
            byte[] raw;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                raw = gzip.readAllBytes();
            }
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw)).toString();
            return installRemoteJson(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode server SVQuest catalog", e);
        }
    }

    public static String compressedBase64() { return compressedCatalog; }

    public static Quest byIndex(int index) {
        List<Quest> quests = QUESTS;
        if (quests.isEmpty()) return null;
        return quests.get(Math.max(0, Math.min(index, quests.size() - 1)));
    }

    public static Quest byId(String id) { return id == null ? null : BY_ID.get(id); }

    public static boolean unlocked(Set<String> claimed, Quest quest) {
        if (quest == null) return false;
        for (String prerequisite : quest.prerequisites()) if (!claimed.contains(prerequisite)) return false;
        return true;
    }

    public static String progressKey(Quest quest, int objectiveIndex) { return quest.id() + "#" + objectiveIndex; }

    public static boolean matches(Objective objective, Map<String, String> meta) {
        String target = clean(objective.target());
        if (target.isEmpty() || target.equals("*")) return true;
        String key = clean(objective.metaKey());
        if (key.isEmpty()) key = "target";
        String actual = meta == null ? "" : clean(meta.get(key));
        if (actual.equalsIgnoreCase(target)) return true;
        for (String part : actual.split(",")) if (part.trim().equalsIgnoreCase(target)) return true;
        return false;
    }

    private static void install(List<Quest> quests, String source) throws IOException {
        Catalog validated = validate(quests, source);
        QUESTS = validated.quests();
        BY_ID = validated.byId();
        compressedCatalog = gzipBase64(exportJson(validated.quests()));
        long objectives = validated.quests().stream().mapToLong(q -> q.objectives().size()).sum();
        SVQuest.LOGGER.info("SVQuest catalog loaded from {}: {} quests / {} objectives.", source, validated.quests().size(), objectives);
    }

    private static Catalog validate(List<Quest> quests, String source) {
        if (quests == null || quests.isEmpty()) throw new IllegalStateException("SVQuest catalog is empty: " + source);
        LinkedHashMap<String, Quest> map = new LinkedHashMap<>();
        for (Quest quest : quests) {
            if (quest.id() == null || quest.id().isBlank()) throw new IllegalStateException("Blank quest id in " + source);
            if (quest.id().length() > 96) throw new IllegalStateException("Quest id is too long: " + quest.id());
            if (map.put(quest.id(), quest) != null) throw new IllegalStateException("Duplicate quest id: " + quest.id());
            if (quest.objectives().isEmpty()) throw new IllegalStateException("Quest has no objectives: " + quest.id());
            for (Objective objective : quest.objectives()) {
                if (objective.type() == null || objective.type().isBlank()) throw new IllegalStateException("Blank objective type: " + quest.id());
                if (objective.amount() <= 0) throw new IllegalStateException("Objective amount must be positive: " + quest.id());
            }
        }
        for (Quest quest : quests) {
            for (String prerequisite : quest.prerequisites()) {
                if (!map.containsKey(prerequisite)) throw new IllegalStateException("Dangling prerequisite: " + quest.id() + " -> " + prerequisite);
                if (quest.id().equals(prerequisite)) throw new IllegalStateException("Self prerequisite: " + quest.id());
            }
        }
        detectCycles(map);
        return new Catalog(Collections.unmodifiableList(new ArrayList<>(quests)), Collections.unmodifiableMap(map));
    }

    private static void detectCycles(Map<String, Quest> quests) {
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String id : quests.keySet()) dfs(id, quests, visiting, visited);
    }

    private static void dfs(String id, Map<String, Quest> quests, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalStateException("Prerequisite cycle detected at quest: " + id);
        for (String prerequisite : quests.get(id).prerequisites()) dfs(prerequisite, quests, visiting, visited);
        visiting.remove(id);
        visited.add(id);
    }

    private static List<Quest> parseDocument(String json, String source) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray array;
        if (root.isJsonArray()) array = root.getAsJsonArray();
        else if (root.isJsonObject() && root.getAsJsonObject().has("quests")) array = root.getAsJsonObject().getAsJsonArray("quests");
        else if (root.isJsonObject()) { array = new JsonArray(); array.add(root); }
        else throw new IllegalStateException("Unsupported quest JSON root in " + source);

        ArrayList<Quest> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalStateException("Quest entry is not an object in " + source);
            JsonObject q = element.getAsJsonObject();
            if (q.has("enabled") && !bool(q, "enabled", true)) continue;
            String id = str(q, "id");
            String category = str(q, "category");
            String phase = str(q, "phase");
            if (phase.isBlank()) phase = category;

            ArrayList<Objective> objectives = new ArrayList<>();
            JsonArray objectiveArray = q.has("objectives") && q.get("objectives").isJsonArray() ? q.getAsJsonArray("objectives") : new JsonArray();
            for (JsonElement objectiveElement : objectiveArray) {
                JsonObject o = objectiveElement.getAsJsonObject();
                objectives.add(new Objective(
                        str(o, "type").toUpperCase(Locale.ROOT),
                        str(o, "target"),
                        str(o, "metaKey"),
                        Math.max(1L, lng(o, "amount", 1L)),
                        str(o, "mode"),
                        str(o, "label"),
                        str(o, "featureId")
                ));
            }

            ArrayList<Reward> rewards = new ArrayList<>();
            JsonArray rewardArray = q.has("rewards") && q.get("rewards").isJsonArray() ? q.getAsJsonArray("rewards") : new JsonArray();
            for (JsonElement rewardElement : rewardArray) {
                JsonObject r = rewardElement.getAsJsonObject();
                rewards.add(new Reward(
                        str(r, "type").toUpperCase(Locale.ROOT),
                        str(r, "item"),
                        (int) Math.max(1L, lng(r, "count", 1L)),
                        Math.max(0L, lng(r, "amount", 0L)),
                        str(r, "command"),
                        str(r, "label")
                ));
            }

            ArrayList<String> prerequisites = new ArrayList<>();
            if (q.has("prerequisites") && q.get("prerequisites").isJsonArray()) {
                for (JsonElement p : q.getAsJsonArray("prerequisites")) prerequisites.add(p.getAsString().trim());
            }
            out.add(new Quest(id, category, phase, str(q, "title"), str(q, "description"),
                    List.copyOf(objectives), List.copyOf(rewards), List.copyOf(prerequisites)));
        }
        return out;
    }

    private static String exportJson(List<Quest> quests) {
        JsonObject root = new JsonObject();
        root.add("quests", GSON.toJsonTree(quests));
        return GSON.toJson(root);
    }

    private static String gzipBase64(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String readResourceStrict(String path) throws IOException {
        try (InputStream in = QuestCatalog.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource " + path);
            return readStrict(in, path);
        }
    }

    private static String readStrict(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) { return readStrict(in, path.toString()); }
    }

    private static String readStrict(InputStream in, String source) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(in, decoder)) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) >= 0) out.append(buffer, 0, n);
            return out.toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 in " + source, e);
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try { return obj.get(key).getAsString().trim(); } catch (Exception ignored) { return ""; }
    }

    private static long lng(JsonObject obj, String key, long fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try { return obj.get(key).getAsLong(); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try { return obj.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private record Catalog(List<Quest> quests, Map<String, Quest> byId) {}
}
