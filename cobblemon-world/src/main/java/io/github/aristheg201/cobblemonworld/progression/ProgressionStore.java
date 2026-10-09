package io.github.aristheg201.cobblemonworld.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressionStore {
    public static final ProgressionStore INSTANCE = new ProgressionStore();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, PlayerProgression> players = new HashMap<>();
    private MinecraftServer server;
    private Path saveFile;
    private boolean loadFailed;

    private ProgressionStore() {}

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        this.saveFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("cobblemonworld")
                .resolve("progression.json");
        players.clear();
        loadFailed = false;

        if (!Files.exists(saveFile)) {
            CobblemonWorldMod.LOGGER.info("No Cobblemon World progression save exists yet; starting fresh.");
            return;
        }

        try {
            RootData data = GSON.fromJson(Files.readString(saveFile, StandardCharsets.UTF_8), RootData.class);
            if (data == null || data.players == null || data.players.values().stream().anyMatch(java.util.Objects::isNull))
                throw new IOException("Progression root or player entry is missing");
            players.putAll(data.players);
            players.values().forEach(PlayerProgression::normalize);
            CobblemonWorldMod.LOGGER.info("Loaded Cobblemon World progression for {} players.", players.size());
        } catch (Exception e) {
            loadFailed = true;
            players.clear();
            CobblemonWorldMod.LOGGER.error("Failed to load progression data from {}. Existing file was left untouched.", saveFile, e);
        }
    }

    public synchronized PlayerProgression getOrCreate(UUID playerId) {
        if (loadFailed) throw new IllegalStateException("Progression could not be loaded; writes are locked to preserve the existing save");
        PlayerProgression progression = players.computeIfAbsent(playerId.toString(), ignored -> new PlayerProgression());
        return progression;
    }

    public synchronized void save(MinecraftServer ignored) {
        save();
    }

    public synchronized void save() { saveChecked(); }

    public synchronized boolean saveChecked() {
        if (server == null || saveFile == null || loadFailed) return false;

        try {
            Files.createDirectories(saveFile.getParent());
            RootData root = new RootData();
            root.players.putAll(players);

            Path temp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            CobblemonWorldMod.LOGGER.error("Failed to save progression data to {}", saveFile, e);
            return false;
        }
    }

    private static final class RootData {
        int schemaVersion = 1;
        Map<String, PlayerProgression> players = new HashMap<>();
    }
}
