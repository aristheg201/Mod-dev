package io.github.aristheg201.cobblemonworld.boss;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.npc.TrainerBattleService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TobaEncounterService {
    private static final Map<UUID, UUID> ACTIVE_ACTORS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ACTIVE_BOSSES = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static long ticks;

    private TobaEncounterService() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            ACTIVE_ACTORS.clear();
            ACTIVE_BOSSES.clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(TobaEncounterService::tick);
    }

    public static TobaBossState state(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.storyFlags.contains("toba_defeated")) return TobaBossState.DEFEATED;
        if (p.storyFlags.contains("toba_phase2_active")) return TobaBossState.PHASE_TWO_RPG;
        if (p.storyFlags.contains("mysterious_phase_one_active")) return TobaBossState.PHASE_ONE_COBBLEMON;
        return eligible(player) ? TobaBossState.READY : TobaBossState.LOCKED;
    }

    public static boolean eligible(ServerPlayer player) {
        if (!CWorldConfig.INSTANCE.finalEncounterEnabled) return false;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        return p.storyFlags.contains("main_story_complete")
                && p.storyFlags.contains("toba_meeting_revealed")
                && !p.storyFlags.contains("toba_defeated");
    }

    public static boolean isAtConfiguredLocation(ServerPlayer player) {
        if (!CWorldConfig.INSTANCE.finalEncounterEnabled) return false;
        String dimension = player.level().dimension().location().toString();
        if (!dimension.equals(CWorldConfig.INSTANCE.finalEncounterDimension)) return false;

        double dx = player.getX() - CWorldConfig.INSTANCE.finalEncounterX;
        double dy = player.getY() - CWorldConfig.INSTANCE.finalEncounterY;
        double dz = player.getZ() - CWorldConfig.INSTANCE.finalEncounterZ;
        double radius = Math.max(1.0, CWorldConfig.INSTANCE.finalEncounterActivationRadius);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static void tick(MinecraftServer server) {
        if (++ticks % 20L != 0L) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (state(player) == TobaBossState.READY && isAtConfiguredLocation(player)) {
                ensureMysteriousActor(player);
            } else if (state(player) == TobaBossState.READY && !isAtConfiguredLocation(player)) {
                discardEntity(ACTIVE_ACTORS.remove(player.getUUID()));
            }
        }
    }

    private static void ensureMysteriousActor(ServerPlayer player) {
        UUID existing = ACTIVE_ACTORS.get(player.getUUID());
        if (findEntity(existing) instanceof MysteriousFigureEntity) return;

        MysteriousFigureEntity actor = new MysteriousFigureEntity(ModBossEntities.MYSTERIOUS_FIGURE, player.serverLevel());
        actor.setOwnerUuid(player.getUUID());
        actor.moveTo(
                CWorldConfig.INSTANCE.finalEncounterX,
                CWorldConfig.INSTANCE.finalEncounterY,
                CWorldConfig.INSTANCE.finalEncounterZ,
                CWorldConfig.INSTANCE.finalEncounterYaw,
                0.0F
        );
        if (player.serverLevel().addFreshEntity(actor)) {
            ACTIVE_ACTORS.put(player.getUUID(), actor.getUUID());
            CWorldNetworking.toast(player, "story", "???", "You finally came.");
        }
    }

    public static void beginPhaseOne(ServerPlayer player, MysteriousFigureEntity actor) {
        if (state(player) != TobaBossState.READY) return;

        var definition = NpcDefinitionRegistry.INSTANCE.get("mysterious");
        if (definition == null) return;

        NPCEntity proxy = TrainerBattleService.createNpc(player, definition);
        proxy.setInvisible(true);
        proxy.setCustomNameVisible(false);
        proxy.moveTo(actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(), actor.getXRot());
        if (!player.serverLevel().addFreshEntity(proxy)) return;

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.add("mysterious_phase_one_active");
        ProgressionStore.INSTANCE.save();

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "???: Good. I needed to know you could survive what comes next."));
        try {
            BattleBuilder.INSTANCE.pvn(player, proxy);
        } catch (Exception e) {
            proxy.discard();
            p.storyFlags.remove("mysterious_phase_one_active");
            ProgressionStore.INSTANCE.save();
        }
    }

    public static void cobblemonPhaseWon(ServerPlayer player) {
        if (state(player) != TobaBossState.PHASE_ONE_COBBLEMON) return;

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.remove("mysterious_phase_one_active");
        p.storyFlags.add("mysterious_phase_one_defeated");
        p.storyFlags.add("toba_phase2_active");
        ProgressionStore.INSTANCE.save();

        MysteriousFigureEntity actor = findEntity(ACTIVE_ACTORS.remove(player.getUUID())) instanceof MysteriousFigureEntity m ? m : null;
        double x = actor == null ? CWorldConfig.INSTANCE.finalEncounterX : actor.getX();
        double y = actor == null ? CWorldConfig.INSTANCE.finalEncounterY : actor.getY();
        double z = actor == null ? CWorldConfig.INSTANCE.finalEncounterZ : actor.getZ();
        float yaw = actor == null ? CWorldConfig.INSTANCE.finalEncounterYaw : actor.getYRot();
        if (actor != null) actor.discard();

        TobaEntity boss = new TobaEntity(ModBossEntities.TOBA, player.serverLevel());
        boss.setOwnerUuid(player.getUUID());
        boss.moveTo(x, y, z, yaw, 0.0F);
        if (player.serverLevel().addFreshEntity(boss)) {
            ACTIVE_BOSSES.put(player.getUUID(), boss.getUUID());
            TobaCombatService.begin(player);
            CWorldNetworking.toast(player, "story", "THE ONE BELOW ALL", "Phase II");
        } else {
            resetAfterFailure(player);
        }
    }

    public static TobaEntity activeBoss(ServerPlayer player) {
        var entity = findEntity(ACTIVE_BOSSES.get(player.getUUID()));
        return entity instanceof TobaEntity toba ? toba : null;
    }

    public static void onTobaDefeated(TobaEntity boss) {
        UUID ownerId = boss.getOwnerUuid();
        if (ownerId == null || server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        ACTIVE_BOSSES.remove(ownerId);
        if (player != null) defeat(player);
    }

    public static void defeat(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.remove("toba_phase2_active");
        p.storyFlags.add("toba_defeated");
        p.currentStory = "postgame";
        p.currentObjective = "The seals are gone. Decide what the world becomes next.";
        ProgressionStore.INSTANCE.save();
        TobaCombatService.reset(player);
        CWorldNetworking.toast(player, "story", "THE ONE BELOW ALL", "Defeated");
    }

    public static void resetAfterFailure(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.remove("mysterious_phase_one_active");
        p.storyFlags.remove("toba_phase2_active");
        ProgressionStore.INSTANCE.save();

        discardEntity(ACTIVE_ACTORS.remove(player.getUUID()));
        discardEntity(ACTIVE_BOSSES.remove(player.getUUID()));
        TobaCombatService.reset(player);
        CWorldNetworking.toast(player, "story", "Final Encounter", "Reset. Return to the meeting point when ready.");
    }

    private static net.minecraft.world.entity.Entity findEntity(UUID uuid) {
        if (uuid == null || server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void discardEntity(UUID uuid) {
        var entity = findEntity(uuid);
        if (entity != null) entity.discard();
    }
}
