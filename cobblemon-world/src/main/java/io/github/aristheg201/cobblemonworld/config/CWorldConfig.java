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
    /** When Cobblemon's spawn cause is not a player, resolve the lowest cap of nearby players. */
    public double naturalSpawnCapFallbackRadius = 128.0;

    // Final encounter is intentionally disabled until configured by the server owner.
    public boolean finalEncounterEnabled = false;
    public String finalEncounterDimension = "minecraft:overworld";
    public double finalEncounterX = 0.0;
    public double finalEncounterY = 64.0;
    public double finalEncounterZ = 0.0;
    public float finalEncounterYaw = 0.0F;
    public double finalEncounterActivationRadius = 8.0;

    // Weekly faction island.
    public String factionTimezone = "Asia/Bangkok";
    public int factionIslandRadius = 96;
    public int factionIslandY = 120;
    public int factionGateWarMinutes = 30;
    public int factionConquestMinutes = 60;
    public int factionGateScoreRequired = 5;
    public int factionMaxQualified = 8;
    public int factionCaptureSeconds = 30;
    public double factionCaptureRadius = 10.0;
    public int factionIslandBuildBlocksPerTick = 4000;
    public int factionOccupationSpawnIntervalTicks = 200;
    public double factionRareBonusChance = 0.20;
    public double factionLegendaryBonusChance = 0.01;

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
        if (naturalSpawnCapFallbackRadius < 16.0) naturalSpawnCapFallbackRadius = 16.0;
        if (factionIslandRadius < 48) factionIslandRadius = 48;
        if (factionIslandY < 64) factionIslandY = 64;
        if (factionGateWarMinutes < 1) factionGateWarMinutes = 1;
        if (factionConquestMinutes < 1) factionConquestMinutes = 1;
        if (factionGateScoreRequired < 1) factionGateScoreRequired = 1;
        if (factionMaxQualified < 1) factionMaxQualified = 1;
        if (factionCaptureSeconds < 5) factionCaptureSeconds = 5;
        if (factionCaptureRadius < 3.0) factionCaptureRadius = 3.0;
        if (factionIslandBuildBlocksPerTick < 100) factionIslandBuildBlocksPerTick = 100;
        if (factionOccupationSpawnIntervalTicks < 20) factionOccupationSpawnIntervalTicks = 20;
        factionRareBonusChance = Math.max(0.0, Math.min(1.0, factionRareBonusChance));
        factionLegendaryBonusChance = Math.max(0.0, Math.min(1.0, factionLegendaryBonusChance));
        if (!"CANCEL".equalsIgnoreCase(overCapSpawnPolicy)) {
            CobblemonWorldMod.LOGGER.warn("Unknown overCapSpawnPolicy '{}'; using CANCEL.", overCapSpawnPolicy);
            overCapSpawnPolicy = "CANCEL";
        }
    }
}
