package io.github.aristheg201.cobblemonworld.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcPlacementStore {
    public static final NpcPlacementStore INSTANCE = new NpcPlacementStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, NpcPlacement> placements = new LinkedHashMap<>();
    private Path saveFile;

    private NpcPlacementStore() {}

    public synchronized void load(MinecraftServer server) {
        saveFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("cobblemonworld")
                .resolve("npc_placements.json");
        placements.clear();
        if (!Files.exists(saveFile)) return;
        try {
            Root root = GSON.fromJson(Files.readString(saveFile, StandardCharsets.UTF_8), Root.class);
            if (root != null && root.placements != null) placements.putAll(root.placements);
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to load NPC placements from {}", saveFile, e);
        }
    }

    public synchronized void save(MinecraftServer ignored) { save(); }

    public synchronized void save() {
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            Root root = new Root();
            root.placements.putAll(placements);
            Path temp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, saveFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to save NPC placements to {}", saveFile, e);
        }
    }

    public synchronized void put(NpcPlacement placement) {
        placements.put(placement.id(), placement);
        save();
    }

    public synchronized NpcPlacement get(String id) { return placements.get(id); }

    public synchronized Map<String, NpcPlacement> all() { return Map.copyOf(placements); }

    public synchronized boolean remove(String id) {
        boolean changed = placements.remove(id) != null;
        if (changed) save();
        return changed;
    }

    private static final class Root {
        int schemaVersion = 1;
        Map<String, NpcPlacement> placements = new LinkedHashMap<>();
    }
}
