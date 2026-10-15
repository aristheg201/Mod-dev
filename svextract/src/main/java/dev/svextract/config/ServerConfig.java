package dev.svextract.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Immutable validated configuration; active sessions retain their admission snapshot. */
public record ServerConfig(int schemaVersion, boolean requirePointBlank, Performance performance,
                           Mission mission) {
    public record Performance(int instanceCapacity, int blocksPerTick, int chunksPerTick,
            int maxEnemiesPerInstance, int spawnRequestsPerTick, int aiDecisionIntervalTicks,
            int noiseCooldownTicks, int hudIntervalTicks, int handshakeTimeoutTicks,
            int databaseQueueCapacity, int databaseShutdownSeconds, int completionsPerTick,
            int telemetrySamples, int admissionP95Ms, int maxSnapshotBytes, int actorShotRequestsPerDecision, int commandCooldownTicks,int instancePreparationTimeoutTicks) {}
    public record Mission(int timeoutSeconds, int extractionSeconds, int regionSpacing,
                          int enemySpawnIntervalTicks, double noiseRadius, double activationRadius,
                          int friendlyBots, String botGun, int actorShotIntervalTicks, double actorGunRange) {}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static ServerConfig defaults() {
        return new ServerConfig(1, true,
                new Performance(1, 128, 1, 8, 1, 20, 10, 10, 200, 64, 15, 4, 2400, 40, 2097152, 2, 40, 2400),
                new Mission(900, 15, 512, 200, 48, 40, 1, "pointblank:glock17", 40, 32));
    }

    public static ServerConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.toJson(defaults()) + "\n");
        }
        ServerConfig config;
        try { var tree = com.google.gson.JsonParser.parseString(Files.readString(file));
            if (tree.isJsonObject() && tree.getAsJsonObject().has("performance") && tree.getAsJsonObject().get("performance").isJsonObject()) {
                var performance = tree.getAsJsonObject().getAsJsonObject("performance");
                if (!performance.has("instancePreparationTimeoutTicks")) performance.addProperty("instancePreparationTimeoutTicks",2400);
                if (!performance.has("commandCooldownTicks")) performance.addProperty("commandCooldownTicks", 40);
            }
            config = JSON.fromJson(tree, ServerConfig.class); }
        catch (RuntimeException e) { throw new IOException(file + ": invalid JSON: " + e.getMessage(), e); }
        if (config == null) throw new IOException(file + ": configuration must be an object");
        List<String> errors = config.errors();
        if (!errors.isEmpty()) throw new IOException(file + ": " + String.join("; ", errors));
        return config;
    }

    public List<String> errors() {
        List<String> e = new ArrayList<>();
        range(e, "schemaVersion", schemaVersion, 1, 1);
        if (performance == null || mission == null) { e.add("performance and mission are required"); return e; }
        Performance p = performance;
        range(e,"performance.instancePreparationTimeoutTicks",p.instancePreparationTimeoutTicks,200,12000);
        range(e,"performance.instanceCapacity",p.instanceCapacity,1,8);
        range(e,"performance.blocksPerTick",p.blocksPerTick,16,2048);
        range(e,"performance.chunksPerTick",p.chunksPerTick,1,4);
        range(e,"performance.maxEnemiesPerInstance",p.maxEnemiesPerInstance,0,32);
        range(e,"performance.spawnRequestsPerTick",p.spawnRequestsPerTick,1,4);
        range(e,"performance.aiDecisionIntervalTicks",p.aiDecisionIntervalTicks,10,200);
        range(e,"performance.noiseCooldownTicks",p.noiseCooldownTicks,1,200);
        range(e,"performance.hudIntervalTicks",p.hudIntervalTicks,5,100);
        range(e,"performance.handshakeTimeoutTicks",p.handshakeTimeoutTicks,40,1200);
        range(e,"performance.databaseQueueCapacity",p.databaseQueueCapacity,4,1024);
        range(e,"performance.databaseShutdownSeconds",p.databaseShutdownSeconds,1,60);
        range(e,"performance.completionsPerTick",p.completionsPerTick,1,16);
        range(e,"performance.telemetrySamples",p.telemetrySamples,100,12000);
        range(e,"performance.admissionP95Ms",p.admissionP95Ms,10,100);
        range(e,"performance.maxSnapshotBytes",p.maxSnapshotBytes,65536,8388608);
        range(e,"performance.actorShotRequestsPerDecision",p.actorShotRequestsPerDecision,1,4);
        range(e,"performance.commandCooldownTicks",p.commandCooldownTicks,10,200);
        range(e,"mission.timeoutSeconds",mission.timeoutSeconds,60,7200);
        range(e,"mission.extractionSeconds",mission.extractionSeconds,3,120);
        range(e,"mission.regionSpacing",mission.regionSpacing,256,2048);
        range(e,"mission.enemySpawnIntervalTicks",mission.enemySpawnIntervalTicks,40,1200);
        range(e,"mission.friendlyBots",mission.friendlyBots,0,2);
        range(e,"mission.actorShotIntervalTicks",mission.actorShotIntervalTicks,20,400);
        if(mission.botGun==null||!mission.botGun.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))e.add("mission.botGun must be a registry identifier");
        if(!Double.isFinite(mission.actorGunRange)||mission.actorGunRange<8||mission.actorGunRange>48)e.add("mission.actorGunRange must be finite and between 8 and 48");
        if (!Double.isFinite(mission.noiseRadius) || mission.noiseRadius < 8 || mission.noiseRadius > 128)
            e.add("mission.noiseRadius must be finite and between 8 and 128");
        if (!Double.isFinite(mission.activationRadius) || mission.activationRadius < 8 || mission.activationRadius > 128)
            e.add("mission.activationRadius must be finite and between 8 and 128");
        return e;
    }
    private static void range(List<String> e,String name,int value,int min,int max) {
        if (value < min || value > max) e.add(name + " must be between " + min + " and " + max);
    }
}
