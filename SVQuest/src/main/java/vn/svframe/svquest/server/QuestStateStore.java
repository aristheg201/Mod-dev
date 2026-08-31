package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public final class QuestStateStore {
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("svquest/playerdata");
    private final Map<UUID, PlayerState> cache = new HashMap<>();

    public QuestStateStore() {
        try { Files.createDirectories(dir); }
        catch (IOException e) { SVQuest.LOGGER.error("Cannot create SVQuest playerdata directory", e); }
    }

    public synchronized PlayerState get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public synchronized void unload(UUID id) {
        PlayerState state = cache.remove(id);
        if (state != null) save(id, state);
    }

    public synchronized void saveAll() {
        cache.forEach(this::save);
    }

    private PlayerState load(UUID id) {
        PlayerState state = new PlayerState();
        Path file = dir.resolve(id + ".properties");
        if (!Files.isRegularFile(file)) return state;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            state.questIndex = parseInt(p.getProperty("questIndex"), 0);
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("progress.")) state.progress.put(key.substring(9), parseInt(p.getProperty(key), 0));
            }
        } catch (Exception e) {
            SVQuest.LOGGER.error("Could not load SVQuest state for {}. Using a safe empty state.", id, e);
        }
        state.normalize();
        return state;
    }

    private void save(UUID id, PlayerState state) {
        try {
            Files.createDirectories(dir);
            Properties p = new Properties();
            p.setProperty("questIndex", Integer.toString(state.questIndex));
            state.progress.forEach((k, v) -> p.setProperty("progress." + k, Integer.toString(v)));
            try (OutputStream out = Files.newOutputStream(dir.resolve(id + ".properties"))) {
                p.store(out, "SVQuest player state");
            }
        } catch (Exception e) {
            SVQuest.LOGGER.error("Could not save SVQuest state for {}", id, e);
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return fallback; }
    }

    public static final class PlayerState {
        private int questIndex;
        private final Map<String, Integer> progress = new HashMap<>();

        public int questIndex() { return questIndex; }
        public int progress(String key) { return Math.max(0, progress.getOrDefault(key, 0)); }

        public void add(String key, int amount) {
            if (amount == 0 || key == null || key.isBlank()) return;
            progress.merge(key, amount, Integer::sum);
            if (progress.get(key) < 0) progress.put(key, 0);
            advanceWhileComplete();
        }

        public void set(String key, int value) {
            if (key == null || key.isBlank()) return;
            progress.put(key, Math.max(0, value));
            advanceWhileComplete();
        }

        private void advanceWhileComplete() {
            while (questIndex < QuestCatalog.QUESTS.size() - 1) {
                var quest = QuestCatalog.byIndex(questIndex);
                boolean complete = quest.objectives().stream().allMatch(o -> progress(o.key()) >= o.target());
                if (!complete) break;
                questIndex++;
            }
        }

        private void normalize() {
            questIndex = Math.max(0, Math.min(questIndex, QuestCatalog.QUESTS.size() - 1));
            progress.replaceAll((k, v) -> Math.max(0, v));
            advanceWhileComplete();
        }

        public String encode() {
            StringBuilder out = new StringBuilder();
            out.append("v=1\n");
            out.append("questIndex=").append(questIndex).append('\n');
            progress.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    out.append("p.").append(safe(e.getKey())).append('=').append(e.getValue()).append('\n'));
            return out.toString();
        }

        private static String safe(String s) {
            return s.replace("\\", "").replace("\n", "").replace("\r", "").replace("=", "");
        }
    }
}
