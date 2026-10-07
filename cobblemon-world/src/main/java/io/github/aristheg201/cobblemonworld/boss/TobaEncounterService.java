package io.github.aristheg201.cobblemonworld.boss;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.npc.TrainerBattleService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.CampaignService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TobaEncounterService {
    private static final Map<UUID, UUID> ACTIVE_ACTORS = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static long ticks;

    private TobaEncounterService() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (UUID owner : ACTIVE_ACTORS.keySet()) clearTransientEncounterState(owner);
            for (UUID entity : ACTIVE_ACTORS.values()) discardEntity(entity);
            ProgressionStore.INSTANCE.save();
            ACTIVE_ACTORS.clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(TobaEncounterService::tick);
    }

    public static TobaBossState state(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.storyFlags.contains("toba_identity_revealed") || p.storyFlags.contains("toba_defeated")) {
            return TobaBossState.IDENTITY_REVEALED;
        }
        if (p.storyFlags.contains("mysterious_phase_one_active")) return TobaBossState.PHASE_ONE_COBBLEMON;
        return eligible(player) ? TobaBossState.READY : TobaBossState.LOCKED;
    }

    public static boolean eligible(ServerPlayer player) {
        if (!CWorldConfig.INSTANCE.finalEncounterEnabled) return false;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        return p.storyFlags.contains("toba_meeting_revealed")
                && !p.storyFlags.contains("toba_identity_revealed")
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
            TobaBossState current = state(player);

            if (current == TobaBossState.PHASE_ONE_COBBLEMON) {
                if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) == null) {
                    resetAfterFailure(player);
                }
                continue;
            }

            if (current == TobaBossState.READY && isAtConfiguredLocation(player)) {
                ensureMysteriousActor(player);
            } else if (current == TobaBossState.READY) {
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

        player.sendSystemMessage(Component.literal("???: You want my name? Beat me first."));
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
        p.storyFlags.remove("toba_phase2_active");
        ProgressionStore.INSTANCE.save();

        if (findEntity(ACTIVE_ACTORS.get(player.getUUID())) instanceof MysteriousFigureEntity actor) {
            actor.setCustomName(Component.literal("TOBA"));
            actor.setCustomNameVisible(true);
        }

        CampaignService.setFlag(player, "toba_identity_revealed");
        CampaignService.setFlag(player, "main_story_complete");
        player.sendSystemMessage(Component.literal("TOBA: Now you know my name."));
        CWorldNetworking.toast(player, "story", "TOBA", "Identity revealed.");
    }

    public static TobaEntity activeBoss(ServerPlayer player) {
        return null;
    }

    public static void onTobaDefeated(TobaEntity boss) {
        if (boss != null) boss.discard();
    }

    public static void resetAfterFailure(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.remove("mysterious_phase_one_active");
        p.storyFlags.remove("toba_phase2_active");
        ProgressionStore.INSTANCE.save();

        discardEntity(ACTIVE_ACTORS.remove(player.getUUID()));
        CWorldNetworking.toast(player, "story", "Meeting", "Battle reset. Return when ready.");
    }

    private static void clearTransientEncounterState(UUID ownerId) {
        if (ownerId == null) return;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(ownerId);
        p.storyFlags.remove("mysterious_phase_one_active");
        p.storyFlags.remove("toba_phase2_active");
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
