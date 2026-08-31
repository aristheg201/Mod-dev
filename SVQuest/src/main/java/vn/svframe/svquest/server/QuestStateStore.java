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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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

    public synchronized void saveNow(UUID id) {
        PlayerState state = cache.get(id);
        if (state != null) save(id, state);
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
                else if (key.startsWith("rewarded.") && Boolean.parseBoolean(p.getProperty(key))) state.rewarded.add(key.substring(9));
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
            state.rewarded.forEach(q -> p.setProperty("rewarded." + q, "true"));
            Path target = dir.resolve(id + ".properties");
            Path temp = dir.resolve(id + ".properties.tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                p.store(out, "SVQuest player state v2");
            }
            try {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        private final Set<String> rewarded = new HashSet<>();

        public int questIndex() { return questIndex; }
        public int progress(String key) { return Math.max(0, progress.getOrDefault(key, 0)); }
        public boolean rewarded(String questId) { return rewarded.contains(questId); }
        public boolean markRewarded(String questId) { return rewarded.add(questId); }

        /** Gameplay counters only count when relevant to the current step, except explicit carry-over metrics. */
        public void signal(String key, int amount) {
            if (amount <= 0 || key == null || key.isBlank()) return;
            if (!QuestCatalog.currentAccepts(questIndex, key)) return;
            progress.merge(key, amount, Integer::sum);
            advanceWhileComplete();
        }

        /** Absolute metrics (levels, ranks/floors when adapters expose an absolute value). */
        public void metric(String key, int value) {
            if (key == null || key.isBlank()) return;
            progress.merge(key, Math.max(0, value), Math::max);
            advanceWhileComplete();
        }

        /** Admin/debug path. Not used by player actions. */
        public void add(String key, int amount) {
            if (amount == 0 || key == null || key.isBlank()) return;
            progress.merge(key, amount, Integer::sum);
            if (progress.get(key) < 0) progress.put(key, 0);
            advanceWhileComplete();
        }

        /** Admin/debug path. Not used by player actions. */
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
            // Do not auto-advance on load: rewards/sequence must be processed by live server gameplay.
        }

        public String encode() {
            StringBuilder out = new StringBuilder();
            out.append("v=2\n");
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
