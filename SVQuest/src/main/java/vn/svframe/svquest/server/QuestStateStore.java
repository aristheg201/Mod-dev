package vn.svframe.svquest.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player quest state keyed by questId#objectiveIndex, not by a single questIndex. */
public final class QuestStateStore {
    private static final Gson GSON = new Gson();
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("svquest/playerdata");
    private final Path beta5Dir = FabricLoader.getInstance().getConfigDir().resolve("svquest/data");
    private final Map<UUID, PlayerState> cache = new HashMap<>();

    public QuestStateStore() {
        try { Files.createDirectories(dir); }
        catch (IOException e) { SVQuest.LOGGER.error("Cannot create SVQuest playerdata directory", e); }
    }

    public synchronized PlayerState get(UUID id) { return cache.computeIfAbsent(id, this::load); }
    public synchronized void unload(UUID id) { PlayerState s = cache.remove(id); if (s != null) save(id, s); }
    public synchronized void saveAll() { cache.forEach(this::save); }
    public synchronized void saveNow(UUID id) { PlayerState s = cache.get(id); if (s != null) save(id, s); }

    private PlayerState load(UUID id) {
        PlayerState state = new PlayerState();
        Path file = dir.resolve(id + ".properties");
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                for (String key : p.stringPropertyNames()) {
                    if (key.startsWith("progress.")) state.progress.put(key.substring(9), parseLong(p.getProperty(key), 0));
                    else if (key.startsWith("claimed.") && Boolean.parseBoolean(p.getProperty(key))) state.claimed.add(key.substring(8));
                }
            } catch (Exception e) {
                SVQuest.LOGGER.error("Could not load SVQuest v3 state for {}", id, e);
            }
        }
        if (state.progress.isEmpty() && state.claimed.isEmpty()) importBeta5(id, state);
        state.normalize();
        return state;
    }

    private void importBeta5(UUID id, PlayerState state) {
        Path json = beta5Dir.resolve(id + ".json");
        if (!Files.isRegularFile(json)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), JsonObject.class);
            if (root.has("progress")) {
                for (var e : root.getAsJsonObject("progress").entrySet()) {
                    try { state.progress.put(e.getKey(), Math.max(0, e.getValue().getAsLong())); } catch (Exception ignored) { }
                }
            }
            if (root.has("claimed")) for (var e : root.getAsJsonArray("claimed")) state.claimed.add(e.getAsString());
            SVQuest.LOGGER.info("Imported beta.5 SVQuest state for {}", id);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Could not import beta.5 SVQuest state for {}: {}", id, t.toString());
        }
    }

    private void save(UUID id, PlayerState state) {
        try {
            Files.createDirectories(dir);
            Properties p = new Properties();
            p.setProperty("schema", "3");
            state.progress.forEach((k, v) -> p.setProperty("progress." + k, Long.toString(v)));
            state.claimed.forEach(q -> p.setProperty("claimed." + q, "true"));
            Path target = dir.resolve(id + ".properties"), temp = dir.resolve(id + ".properties.tmp");
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "SVQuest player state v3"); }
            try { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { SVQuest.LOGGER.error("Could not save SVQuest state for {}", id, e); }
    }

    private static long parseLong(String value, long fallback) { try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; } }

    public static final class PlayerState {
        private final Map<String, Long> progress = new HashMap<>();
        private final Set<String> claimed = new HashSet<>();

        public long progress(String key) { return Math.max(0, progress.getOrDefault(key, 0L)); }
        public long progress(QuestCatalog.Quest quest, int index) { return progress(QuestCatalog.progressKey(quest, index)); }
        public boolean claimed(String questId) { return claimed.contains(questId); }
        public Set<String> claimedView() { return Set.copyOf(claimed); }
        public int claimedCount() { return claimed.size(); }
        public boolean unlocked(QuestCatalog.Quest quest) { return QuestCatalog.unlocked(claimed, quest); }

        /** One event increments all currently unlocked matching objectives. */
        public boolean emit(String type, long amount, Map<String, String> meta) {
            if (type == null || type.isBlank()) return false;
            String normalized = type.trim().toUpperCase(java.util.Locale.ROOT);
            long positive = Math.max(0, amount);
            boolean changed = false;
            for (QuestCatalog.Quest quest : QuestCatalog.QUESTS) {
                if (claimed(quest.id()) || !unlocked(quest)) continue;
                for (int i = 0; i < quest.objectives().size(); i++) {
                    QuestCatalog.Objective objective = quest.objectives().get(i);
                    if (!normalized.equals(objective.type()) || !QuestCatalog.matches(objective, meta)) continue;
                    String key = QuestCatalog.progressKey(quest, i);
                    long before = progress(key);
                    long after = objective.maxMode() ? Math.max(before, positive) : Math.min(objective.amount(), before + positive);
                    if (after != before) { progress.put(key, after); changed = true; }
                }
            }
            return changed;
        }

        /** Mirrors an absolute counter owned by another mod without repeatedly adding it every poll. */
        public boolean absolute(String type, long value, Map<String, String> meta) {
            if (type == null || type.isBlank()) return false;
            String normalized = type.trim().toUpperCase(java.util.Locale.ROOT);
            long positive = Math.max(0, value);
            boolean changed = false;
            for (QuestCatalog.Quest quest : QuestCatalog.QUESTS) {
                if (claimed(quest.id()) || !unlocked(quest)) continue;
                for (int i = 0; i < quest.objectives().size(); i++) {
                    QuestCatalog.Objective objective = quest.objectives().get(i);
                    if (!normalized.equals(objective.type()) || !QuestCatalog.matches(objective, meta)) continue;
                    String key = QuestCatalog.progressKey(quest, i);
                    long before = progress(key);
                    long after = Math.min(objective.amount(), Math.max(before, positive));
                    if (after != before) { progress.put(key, after); changed = true; }
                }
            }
            return changed;
        }

        public boolean complete(QuestCatalog.Quest quest) {
            if (quest == null || !unlocked(quest) || quest.objectives().isEmpty()) return false;
            for (int i = 0; i < quest.objectives().size(); i++) {
                if (progress(quest, i) < quest.objectives().get(i).amount()) return false;
            }
            return true;
        }

        public List<QuestCatalog.Quest> completeUnclaimed() {
            ArrayList<QuestCatalog.Quest> out = new ArrayList<>();
            for (QuestCatalog.Quest quest : QuestCatalog.QUESTS) if (!claimed(quest.id()) && complete(quest)) out.add(quest);
            return out;
        }

        public boolean claim(String id) { return id != null && claimed.add(id); }

        public void adminSet(String key, long value) {
            if (key == null || key.isBlank()) return;
            if (key.contains("#")) progress.put(key, Math.max(0, value));
            else absolute(key, Math.max(0, value), Map.of());
        }

        public void adminAdd(String key, long amount) {
            if (key == null || key.isBlank()) return;
            if (key.contains("#")) progress.merge(key, amount, (a, b) -> Math.max(0L, a + b));
            else emit(key, Math.max(0, amount), Map.of());
        }

        private void normalize() {
            progress.replaceAll((k, v) -> Math.max(0, v));
            claimed.removeIf(id -> QuestCatalog.byId(id) == null);
        }

        public String encode() {
            StringBuilder out = new StringBuilder(4096);
            out.append("v=3\n");
            out.append("claimed=");
            claimed.stream().sorted().forEach(id -> out.append(safe(id)).append(','));
            out.append('\n');
            progress.entrySet().stream().filter(e -> e.getValue() > 0).sorted(Map.Entry.comparingByKey()).forEach(e ->
                    out.append("p.").append(safe(e.getKey())).append('=').append(e.getValue()).append('\n'));
            return out.toString();
        }

        private static String safe(String s) { return s.replace("\\", "").replace("\n", "").replace("\r", "").replace("=", "").replace(",", ""); }
    }
}
