package io.github.aristheg201.cobblemonworld.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class IslandWarStore {
    public static final IslandWarStore INSTANCE = new IslandWarStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path path;
    private IslandWarState state = new IslandWarState();

    private IslandWarStore() {}

    public synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("cobblemonworld").resolve("island_war.json");
        state = new IslandWarState();
        if (Files.exists(path)) {
            try {
                IslandWarState loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), IslandWarState.class);
                if (loaded != null) state = loaded;
            } catch (Exception e) {
                CobblemonWorldMod.LOGGER.error("Failed to load faction island state", e);
            }
        }
        state.normalize();
    }

    public synchronized IslandWarState state() {
        state.normalize();
        return state;
    }

    public synchronized void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to save faction island state", e);
        }
    }
}
