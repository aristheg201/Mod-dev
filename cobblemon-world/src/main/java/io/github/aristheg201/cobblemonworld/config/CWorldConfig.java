package io.github.aristheg201.cobblemonworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CWorldConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("cobblemonworld")
            .resolve("server.json");

    public static CWorldConfig INSTANCE = defaults();

    public int defaultLevelCap = 15;
    public int maxLevelCap = 100;

    public boolean blockOverCapSendOut = true;
    public boolean blockOverCapBattles = true;
    public boolean blockOverCapCapture = true;
    public boolean blockOverCapNaturalSpawns = true;
    public boolean blockExperiencePastCap = true;

    public String overCapSpawnPolicy = "CANCEL";

    public static void load() {
        try {
            Files.createDirectories(PATH.getParent());
            if (!Files.exists(PATH)) {
                INSTANCE = defaults();
                save();
                return;
            }

            CWorldConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), CWorldConfig.class);
            INSTANCE = loaded == null ? defaults() : loaded;
            INSTANCE.normalize();
        } catch (Exception e) {
            CobblemonWorldMod.LOGGER.error("Failed to load {}. Using safe defaults.", PATH, e);
            INSTANCE = defaults();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CobblemonWorldMod.LOGGER.error("Failed to save {}", PATH, e);
        }
    }

    private static CWorldConfig defaults() {
        return new CWorldConfig();
    }

    private void normalize() {
        if (defaultLevelCap < 1) defaultLevelCap = 1;
        if (maxLevelCap < defaultLevelCap) maxLevelCap = defaultLevelCap;
        if (!"CANCEL".equalsIgnoreCase(overCapSpawnPolicy)) {
            CobblemonWorldMod.LOGGER.warn("Unknown overCapSpawnPolicy '{}'; using CANCEL.", overCapSpawnPolicy);
            overCapSpawnPolicy = "CANCEL";
        }
    }
}
