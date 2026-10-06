package io.github.aristheg201.cobblemonworld.faction;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.notification.NotificationService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IslandWarService {
    public static final ResourceLocation DIMENSION_ID =
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "faction_islands");
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, DIMENSION_ID);

    private static final String[] AFFINITIES = {
            "fire", "water", "grass", "electric", "ice", "dragon",
            "ghost", "steel", "fairy", "ancient", "ultra", "legendary"
    };
    private static final int[][] CONTROL_OFFSETS = {
            {0, 0}, {36, 0}, {-36, 0}, {0, 36}, {0, -36}
    };

    private static final ArrayDeque<Placement> BUILD_QUEUE = new ArrayDeque<>();
    private static MinecraftServer server;
    private static long ticks;

    private IslandWarService() {}

    public static void register() {
        IslandSpawnPoolRegistry.INSTANCE.load();

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            IslandWarStore.INSTANCE.load(s);
            IslandWarState state = IslandWarStore.INSTANCE.state();
            if (!state.weekId.isBlank() && !state.islandBuilt && state.phase != IslandWarPhase.DORMANT) {
                queueIslandBuild();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> IslandWarStore.INSTANCE.save());
        ServerTickEvents.END_SERVER_TICK.register(IslandWarService::tick);

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim)) return;
            if (!(source.getEntity() instanceof ServerPlayer killer)) return;
            recordGateKill(killer, victim);
        });
    }

    private static void tick(MinecraftServer server) {
        processBuildQueue();
        ticks++;

        if (ticks % 20L != 0L) return;
        IslandWarState state = IslandWarStore.INSTANCE.state();
        ZonedDateTime now = ZonedDateTime.now(resolveZone());

        String currentWeek = weekId(now);
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY && !currentWeek.equals(state.weekId)) {
            startNewWar(currentWeek);
            state = IslandWarStore.INSTANCE.state();
        }

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - state.phaseStartedEpochMillis);
        if (state.phase == IslandWarPhase.GATE_WAR
                && elapsedMillis >= CWorldConfig.INSTANCE.factionGateWarMinutes * 60_000L) {
            beginConquest();
            state = IslandWarStore.INSTANCE.state();
        } else if (state.phase == IslandWarPhase.CONQUEST
                && elapsedMillis >= CWorldConfig.INSTANCE.factionConquestMinutes * 60_000L) {
            resolveConquestByScore();
            state = IslandWarStore.INSTANCE.state();
        }

        if (state.phase == IslandWarPhase.CONQUEST && state.islandBuilt) {
            tickCapturePoints();
        }

        if (state.phase == IslandWarPhase.OCCUPATION
                && state.islandBuilt
                && ticks % CWorldConfig.INSTANCE.factionOccupationSpawnIntervalTicks == 0L) {
            ServerLevel island = server.getLevel(DIMENSION);
            if (island != null) IslandSpawnDirector.tick(island, state);
        }

        if (ticks % 200L == 0L) IslandWarStore.INSTANCE.save();
    }

    private static ZoneId resolveZone() {
        try {
            return ZoneId.of(CWorldConfig.INSTANCE.factionTimezone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Bangkok");
        }
    }

    private static String weekId(ZonedDateTime now) {
        WeekFields wf = WeekFields.ISO;
        return now.get(wf.weekBasedYear()) + "-" + String.format(java.util.Locale.ROOT, "%02d", now.get(wf.weekOfWeekBasedYear()));
    }

    private static void startNewWar(String weekId) {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        state.weekId = weekId;
        state.phase = IslandWarPhase.GATE_WAR;
        state.affinity = AFFINITIES[Math.floorMod(weekId.hashCode(), AFFINITIES.length)];
        state.ownerFaction = "";
        state.phaseStartedEpochMillis = System.currentTimeMillis();
        state.islandBuilt = false;
        state.gateScores.clear();
        state.qualifiedFactions.clear();
        state.capturePoints.clear();
        state.normalize();

        queueIslandBuild();
        IslandWarStore.INSTANCE.save();
        broadcast("The weekly island has appeared. Affinity: " + state.affinity.toUpperCase(java.util.Locale.ROOT));
        CobblemonWorldMod.LOGGER.info("Started faction island war {} with {} affinity.", weekId, state.affinity);
    }

    private static void beginConquest() {
        IslandWarState state = IslandWarStore.INSTANCE.state();

        List<Map.Entry<String, Integer>> ranking = new ArrayList<>(state.gateScores.entrySet());
        ranking.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));

        for (var entry : ranking) {
            if (entry.getValue() >= CWorldConfig.INSTANCE.factionGateScoreRequired) {
                state.qualifiedFactions.add(entry.getKey());
            }
        }
        for (var entry : ranking) {
            if (state.qualifiedFactions.size() >= CWorldConfig.INSTANCE.factionMaxQualified) break;
            if (entry.getValue() <= 0) continue;
            state.qualifiedFactions.add(entry.getKey());
        }

        state.phase = IslandWarPhase.CONQUEST;
        state.phaseStartedEpochMillis = System.currentTimeMillis();
        IslandWarStore.INSTANCE.save();
        broadcast("Gate War ended. Qualified factions can now contest the five island control points.");
    }

    private static void tickCapturePoints() {
        ServerLevel island = server.getLevel(DIMENSION);
        if (island == null) return;

        IslandWarState state = IslandWarStore.INSTANCE.state();
        int centerY = CWorldConfig.INSTANCE.factionIslandY + 2;
        double radiusSqr = CWorldConfig.INSTANCE.factionCaptureRadius * CWorldConfig.INSTANCE.factionCaptureRadius;

        for (int i = 0; i < CONTROL_OFFSETS.length; i++) {
            int px = CONTROL_OFFSETS[i][0];
            int pz = CONTROL_OFFSETS[i][1];
            Map<String, Integer> present = new HashMap<>();

            for (ServerPlayer player : island.players()) {
                String faction = FactionBridge.factionName(player).orElse("");
                if (faction.isBlank() || !state.qualifiedFactions.contains(faction)) continue;
                double dx = player.getX() - (px + 0.5);
                double dy = player.getY() - centerY;
                double dz = player.getZ() - (pz + 0.5);
                if (dx * dx + dy * dy + dz * dz <= radiusSqr) {
                    present.merge(faction, 1, Integer::sum);
                }
            }

            IslandWarState.CapturePoint point = state.capturePoints.get(i);
            if (present.size() != 1) {
                point.progressSeconds = Math.max(0, point.progressSeconds - 2);
                if (point.progressSeconds == 0) point.contestingFaction = "";
                continue;
            }

            String faction = present.keySet().iterator().next();
            if (faction.equals(point.ownerFaction)) {
                point.contestingFaction = "";
                point.progressSeconds = 0;
                continue;
            }
            if (!faction.equals(point.contestingFaction)) {
                point.contestingFaction = faction;
                point.progressSeconds = 1;
            } else {
                point.progressSeconds++;
            }

            if (point.progressSeconds >= CWorldConfig.INSTANCE.factionCaptureSeconds) {
                point.ownerFaction = faction;
                point.contestingFaction = "";
                point.progressSeconds = 0;
                broadcast(faction + " captured control point " + (i + 1) + ".");
            }
        }

        Map<String, Integer> owned = new LinkedHashMap<>();
        for (var point : state.capturePoints) {
            if (!point.ownerFaction.isBlank()) owned.merge(point.ownerFaction, 1, Integer::sum);
        }

        for (var entry : owned.entrySet()) {
            if (entry.getValue() >= 3) {
                declareOwner(entry.getKey());
                return;
            }
        }
    }

    private static void resolveConquestByScore() {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        Map<String, Integer> points = new HashMap<>();
        for (var point : state.capturePoints) {
            if (!point.ownerFaction.isBlank()) points.merge(point.ownerFaction, 1, Integer::sum);
        }

        String best = "";
        int bestPoints = -1;
        int bestGate = -1;
        for (String faction : state.qualifiedFactions) {
            int pointCount = points.getOrDefault(faction, 0);
            int gate = state.gateScores.getOrDefault(faction, 0);
            if (pointCount > bestPoints || (pointCount == bestPoints && gate > bestGate)) {
                best = faction;
                bestPoints = pointCount;
                bestGate = gate;
            }
        }

        if (best.isBlank()) {
            state.phase = IslandWarPhase.DORMANT;
            state.phaseStartedEpochMillis = System.currentTimeMillis();
            IslandWarStore.INSTANCE.save();
            broadcast("Faction Island ended without an owner.");
        } else {
            declareOwner(best);
        }
    }

    private static void declareOwner(String faction) {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        state.ownerFaction = faction;
        state.phase = IslandWarPhase.OCCUPATION;
        state.phaseStartedEpochMillis = System.currentTimeMillis();
        IslandWarStore.INSTANCE.save();
        broadcast(faction + " controls the " + state.affinity.toUpperCase(java.util.Locale.ROOT)
                + " island until the next Saturday war.");
    }

    public static void recordGateKill(ServerPlayer killer, ServerPlayer victim) {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        if (state.phase != IslandWarPhase.GATE_WAR) return;
        if (!killer.level().dimension().equals(DIMENSION) || !victim.level().dimension().equals(DIMENSION)) return;

        String killerFaction = FactionBridge.factionName(killer).orElse("");
        String victimFaction = FactionBridge.factionName(victim).orElse("");
        if (killerFaction.isBlank() || victimFaction.isBlank() || killerFaction.equals(victimFaction)) return;

        int score = state.gateScores.merge(killerFaction, 1, Integer::sum);
        if (score >= CWorldConfig.INSTANCE.factionGateScoreRequired
                && state.qualifiedFactions.size() < CWorldConfig.INSTANCE.factionMaxQualified
                && state.qualifiedFactions.add(killerFaction)) {
            NotificationService.faction(killer, killerFaction + " earned island entry.");
        }
        IslandWarStore.INSTANCE.save();
    }

    public static boolean join(ServerPlayer player) {
        if (!FactionBridge.available()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Factions mod is not installed."));
            return false;
        }
        String faction = FactionBridge.factionName(player).orElse("");
        if (faction.isBlank()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Join or create a faction first."));
            return false;
        }

        IslandWarState state = IslandWarStore.INSTANCE.state();
        ServerLevel island = player.getServer().getLevel(DIMENSION);
        if (island == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Faction island dimension is unavailable."));
            return false;
        }
        if (!state.islandBuilt) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("The island is still materializing."));
            return false;
        }

        double x;
        double z;
        if (state.phase == IslandWarPhase.GATE_WAR) {
            int lane = Math.floorMod(faction.hashCode(), 8);
            double angle = lane * (Math.PI * 2.0 / 8.0);
            double distance = CWorldConfig.INSTANCE.factionIslandRadius + 28.0;
            x = Math.cos(angle) * distance;
            z = Math.sin(angle) * distance;
        } else if (state.phase == IslandWarPhase.CONQUEST) {
            if (!state.qualifiedFactions.contains(faction)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your faction did not qualify through Gate War."));
                return false;
            }
            int lane = Math.floorMod(faction.hashCode(), 8);
            double angle = lane * (Math.PI * 2.0 / 8.0);
            double distance = CWorldConfig.INSTANCE.factionIslandRadius - 16.0;
            x = Math.cos(angle) * distance;
            z = Math.sin(angle) * distance;
        } else if (state.phase == IslandWarPhase.OCCUPATION) {
            if (!state.ownerFaction.equals(faction)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("This island is controlled by " + state.ownerFaction + "."));
                return false;
            }
            x = 0.0;
            z = 0.0;
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("No Faction Island event is active."));
            return false;
        }

        player.teleportTo(island, x + 0.5, CWorldConfig.INSTANCE.factionIslandY + 4.0, z + 0.5,
                EnumSet.noneOf(RelativeMovement.class), player.getYRot(), player.getXRot());
        return true;
    }

    public static String statusFor(ServerPlayer player) {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        String faction = FactionBridge.factionName(player).orElse("No Faction");
        int score = state.gateScores.getOrDefault(faction, 0);
        int owned = 0;
        for (var point : state.capturePoints) {
            if (faction.equals(point.ownerFaction)) owned++;
        }
        return "Faction: " + faction
                + " | Island: " + state.phase
                + " | Affinity: " + state.affinity.toUpperCase(java.util.Locale.ROOT)
                + " | Owner: " + (state.ownerFaction.isBlank() ? "None" : state.ownerFaction)
                + " | Gate score: " + score
                + " | Control: " + owned + "/5";
    }

    public static StatusView statusView(ServerPlayer player) {
        IslandWarState state = IslandWarStore.INSTANCE.state();
        String faction = FactionBridge.factionName(player).orElse("No Faction");
        int owned = 0;
        for (var point : state.capturePoints) if (faction.equals(point.ownerFaction)) owned++;
        return new StatusView(
                faction,
                state.phase.name(),
                state.affinity,
                state.ownerFaction,
                state.gateScores.getOrDefault(faction, 0),
                state.qualifiedFactions.contains(faction),
                owned,
                state.islandBuilt
        );
    }

    private static void broadcast(String message) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NotificationService.faction(player, message);
        }
    }

    private static void queueIslandBuild() {
        if (server == null) return;
        ServerLevel island = server.getLevel(DIMENSION);
        if (island == null) {
            CobblemonWorldMod.LOGGER.error("Faction island dimension {} is not loaded.", DIMENSION_ID);
            return;
        }

        BUILD_QUEUE.clear();
        IslandWarState state = IslandWarStore.INSTANCE.state();
        int radius = CWorldConfig.INSTANCE.factionIslandRadius;
        int y = CWorldConfig.INSTANCE.factionIslandY;
        BlockState accent = accentFor(state.affinity);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 > radius * radius) continue;

                double d = Math.sqrt(d2);
                int variation = (int) Math.round(Math.cos(d / 8.0) * 1.5);
                int topY = y + variation;
                BUILD_QUEUE.add(new Placement(new BlockPos(x, topY - 3, z), Blocks.STONE.defaultBlockState()));
                BUILD_QUEUE.add(new Placement(new BlockPos(x, topY - 2, z), Blocks.STONE.defaultBlockState()));
                BUILD_QUEUE.add(new Placement(new BlockPos(x, topY - 1, z), Blocks.DIRT.defaultBlockState()));
                BlockState top = ((Math.abs(x * 31 + z * 17) % 29) == 0) ? accent : Blocks.GRASS_BLOCK.defaultBlockState();
                BUILD_QUEUE.add(new Placement(new BlockPos(x, topY, z), top));
            }
        }

        for (int i = 0; i < CONTROL_OFFSETS.length; i++) {
            int x = CONTROL_OFFSETS[i][0];
            int z = CONTROL_OFFSETS[i][1];
            BUILD_QUEUE.add(new Placement(new BlockPos(x, y + 2, z), Blocks.BEACON.defaultBlockState()));
            BUILD_QUEUE.add(new Placement(new BlockPos(x, y + 3, z), accent));
        }

        for (int lane = 0; lane < 8; lane++) {
            double angle = lane * (Math.PI * 2.0 / 8.0);
            int cx = (int) Math.round(Math.cos(angle) * (radius + 28.0));
            int cz = (int) Math.round(Math.sin(angle) * (radius + 28.0));
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BUILD_QUEUE.add(new Placement(new BlockPos(cx + dx, y, cz + dz), Blocks.DEEPSLATE_TILES.defaultBlockState()));
                }
            }
            BUILD_QUEUE.add(new Placement(new BlockPos(cx, y + 1, cz), accent));
        }

        state.islandBuilt = false;
        IslandWarStore.INSTANCE.save();
    }

    private static void processBuildQueue() {
        if (server == null || BUILD_QUEUE.isEmpty()) return;
        ServerLevel island = server.getLevel(DIMENSION);
        if (island == null) return;

        int budget = CWorldConfig.INSTANCE.factionIslandBuildBlocksPerTick;
        while (budget-- > 0 && !BUILD_QUEUE.isEmpty()) {
            Placement placement = BUILD_QUEUE.removeFirst();
            island.setBlock(placement.pos(), placement.state(), 3);
        }

        if (BUILD_QUEUE.isEmpty()) {
            IslandWarStore.INSTANCE.state().islandBuilt = true;
            IslandWarStore.INSTANCE.save();
            CobblemonWorldMod.LOGGER.info("Faction island build completed.");
        }
    }

    private static BlockState accentFor(String affinity) {
        return switch (affinity) {
            case "fire" -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case "water" -> Blocks.PRISMARINE.defaultBlockState();
            case "grass" -> Blocks.MOSS_BLOCK.defaultBlockState();
            case "electric" -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case "ice" -> Blocks.PACKED_ICE.defaultBlockState();
            case "dragon" -> Blocks.PURPUR_BLOCK.defaultBlockState();
            case "ghost" -> Blocks.SOUL_SOIL.defaultBlockState();
            case "steel" -> Blocks.IRON_BLOCK.defaultBlockState();
            case "fairy" -> Blocks.PINK_CONCRETE.defaultBlockState();
            case "ancient" -> Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            case "ultra" -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case "legendary" -> Blocks.GOLD_BLOCK.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    public record StatusView(
            String factionName,
            String phase,
            String affinity,
            String ownerFaction,
            int gateScore,
            boolean qualified,
            int ownedControlPoints,
            boolean islandBuilt
    ) {}

    private record Placement(BlockPos pos, BlockState state) {}
}
