package vn.svframe.svquest.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Full SVQuest catalog restored from beta.5. */
public final class QuestCatalog {
    private static final Gson GSON = new Gson();
    private static final int CATALOG_PARTS = 8;
    private static final int ENCODED_LENGTH = 44932;
    private static final String JSON_SHA256 = "9c8973547f7cafc6d8b272fada60e34e2d3cb7798ef31982c838f0ef1b3af555";

    private QuestCatalog() {}

    public record Objective(String type, String target, String metaKey, long amount, String mode,
                            String label, String featureId) {
        public boolean maxMode() { return "max".equalsIgnoreCase(mode); }
    }

    public record Reward(String type, String item, int count, long amount, String command, String label) {}

    public record Quest(String id, String category, String title, String description,
                        List<Objective> objectives, List<Reward> rewards, List<String> prerequisites) {
        public String phase() {
            return switch (category == null ? "" : category.toLowerCase(Locale.ROOT)) {
                case "progression" -> "TIẾN TRÌNH";
                case "activity" -> "HOẠT ĐỘNG";
                case "pokemon" -> "POKÉMON";
                case "endgame" -> "ENDGAME";
                default -> "NHIỆM VỤ";
            };
        }
    }

    public static final List<Quest> QUESTS;
    public static final Map<String, Quest> BY_ID;

    static {
        List<Quest> loaded = loadBundled();
        if (loaded.size() != 618) {
            throw new IllegalStateException("SVQuest full catalog did not load: expected 618 quests, got " + loaded.size());
        }
        QUESTS = Collections.unmodifiableList(loaded);
        LinkedHashMap<String, Quest> map = new LinkedHashMap<>();
        for (Quest quest : loaded) map.put(quest.id(), quest);
        if (map.size() != loaded.size()) throw new IllegalStateException("SVQuest full catalog contains duplicate quest ids");
        BY_ID = Collections.unmodifiableMap(map);
    }

    public static Quest byIndex(int index) { return QUESTS.get(Math.max(0, Math.min(index, QUESTS.size() - 1))); }
    public static Quest byId(String id) { return BY_ID.get(id); }

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

    private static List<Quest> loadBundled() {
        try {
            StringBuilder encoded = new StringBuilder(ENCODED_LENGTH);
            for (int i = 0; i < CATALOG_PARTS; i++) {
                String path = String.format(Locale.ROOT, "/svquest/full/default_quests.%02d.b64", i);
                try (InputStream raw = QuestCatalog.class.getResourceAsStream(path)) {
                    if (raw == null) throw new IllegalStateException("Missing " + path);
                    encoded.append(new String(raw.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", ""));
                }
            }
            if (encoded.length() != ENCODED_LENGTH) {
                throw new IllegalStateException("Corrupt SVQuest catalog payload length: " + encoded.length());
            }

            byte[] gz = Base64.getDecoder().decode(encoded.toString());
            byte[] rawJson = inflateGzipPayloadIgnoringTrailer(gz);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawJson));
            if (!JSON_SHA256.equals(digest)) {
                throw new IllegalStateException("Corrupt SVQuest catalog JSON SHA-256: " + digest);
            }
            String json = new String(rawJson, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("quests") || !root.get("quests").isJsonArray()) {
                throw new IllegalStateException("SVQuest catalog JSON has no quests array");
            }

            JsonArray quests = root.getAsJsonArray("quests");
            ArrayList<Quest> out = new ArrayList<>(quests.size());
            for (JsonElement element : quests) {
                JsonObject q = element.getAsJsonObject();
                String id = str(q, "id");
                if (id.isBlank()) continue;

                ArrayList<Objective> objectives = new ArrayList<>();
                JsonArray objectiveArray = q.has("objectives") ? q.getAsJsonArray("objectives") : new JsonArray();
                for (JsonElement objectiveElement : objectiveArray) {
                    JsonObject o = objectiveElement.getAsJsonObject();
                    String type = str(o, "type").toUpperCase(Locale.ROOT);
                    String target = str(o, "target");
                    String metaKey = str(o, "metaKey");
                    long amount = Math.max(1L, lng(o, "amount", 1L));
                    String mode = str(o, "mode");
                    String label = str(o, "label");
                    objectives.add(new Objective(type, target, metaKey, amount, mode, label, featureFor(type, target)));
                }

                ArrayList<Reward> rewards = new ArrayList<>();
                JsonArray rewardArray = q.has("rewards") ? q.getAsJsonArray("rewards") : new JsonArray();
                for (JsonElement rewardElement : rewardArray) {
                    JsonObject r = rewardElement.getAsJsonObject();
                    rewards.add(new Reward(str(r, "type").toUpperCase(Locale.ROOT), str(r, "item"),
                            (int) lng(r, "count", 1), lng(r, "amount", 0), str(r, "command"), str(r, "label")));
                }

                ArrayList<String> prerequisites = new ArrayList<>();
                if (q.has("prerequisites")) for (JsonElement p : q.getAsJsonArray("prerequisites")) prerequisites.add(p.getAsString());
                out.add(new Quest(id, str(q, "category"), str(q, "title"), str(q, "description"),
                        List.copyOf(objectives), List.copyOf(rewards), List.copyOf(prerequisites)));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode full SVQuest beta.5 catalog", e);
        }
    }

    /**
     * The restored base64 has a damaged GZIP trailer but an intact DEFLATE stream. We deliberately ignore
     * only the CRC/ISIZE trailer, then require the decompressed JSON SHA-256 above. This cannot silently
     * accept damaged quest data.
     */
    private static byte[] inflateGzipPayloadIgnoringTrailer(byte[] gz) throws DataFormatException {
        if (gz.length < 18 || (gz[0] & 0xff) != 0x1f || (gz[1] & 0xff) != 0x8b || (gz[2] & 0xff) != 8) {
            throw new DataFormatException("Invalid GZIP header");
        }
        int flags = gz[3] & 0xff;
        int pos = 10;
        if ((flags & 0x04) != 0) {
            if (pos + 2 > gz.length) throw new DataFormatException("Truncated GZIP FEXTRA");
            int xlen = (gz[pos] & 0xff) | ((gz[pos + 1] & 0xff) << 8);
            pos += 2 + xlen;
        }
        if ((flags & 0x08) != 0) pos = skipZeroTerminated(gz, pos);
        if ((flags & 0x10) != 0) pos = skipZeroTerminated(gz, pos);
        if ((flags & 0x02) != 0) pos += 2;
        int end = gz.length - 8;
        if (pos >= end) throw new DataFormatException("Invalid GZIP payload bounds");

        Inflater inflater = new Inflater(true);
        inflater.setInput(gz, pos, end - pos);
        ByteArrayOutputStream out = new ByteArrayOutputStream(600_000);
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n > 0) {
                    out.write(buffer, 0, n);
                    continue;
                }
                if (inflater.needsDictionary()) throw new DataFormatException("Catalog DEFLATE stream requires dictionary");
                if (inflater.needsInput()) throw new DataFormatException("Catalog DEFLATE stream ended early");
                throw new DataFormatException("Catalog DEFLATE stream made no progress");
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static int skipZeroTerminated(byte[] bytes, int pos) throws DataFormatException {
        while (pos < bytes.length && bytes[pos++] != 0) { }
        if (pos > bytes.length) throw new DataFormatException("Truncated GZIP string field");
        return pos;
    }

    private static String featureFor(String type, String target) {
        return switch (type) {
            case "FEATURE_OPEN" -> target;
            case "SHOP_BUY", "SHOP_SELL" -> "shop";
            case "CRATE_OPEN", "ARCADE_GACHA_PULL" -> "gacha";
            case "BREED_EGG", "HATCH" -> "breeding";
            case "POKESKILL_PURCHASE", "POKESKILL_COUNT" -> "pokemon_skills";
            case "GTS_LIST", "GTS_PURCHASE" -> "gts";
            case "WONDERTRADE" -> "wonder_trade";
            case "STS_SELL" -> "sts";
            case "RESEARCH_CAPTURE", "RESEARCH_DEFEAT", "RESEARCH_EVOLVE", "RESEARCH_LEVEL_UP", "RESEARCH_FISH", "RESEARCH_FRIENDSHIP" -> "research";
            case "HUNT_COMPLETE" -> "hunts";
            case "RAID_WIN" -> "raids";
            case "RANKED_WIN" -> "ranked";
            case "TOWER_WIN" -> "battle_tower";
            case "FACTORY_RUN_COMPLETE" -> "battle_factory";
            case "EXPEDITION_COMPLETE" -> "expeditions";
            case "SHOWCASE_PLACE", "SVF_SHOWCASE_SUBMIT", "SVF_SHOWCASE_VOTE" -> "showcase";
            case "SKIN_PURCHASE" -> "skins";
            case "FUSION_DANCE", "FUSION_POTARA" -> "fusion";
            default -> "";
        };
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try { return obj.get(key).getAsString(); } catch (Exception ignored) { return ""; }
    }

    private static long lng(JsonObject obj, String key, long fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try { return obj.get(key).getAsLong(); } catch (Exception ignored) { return fallback; }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
