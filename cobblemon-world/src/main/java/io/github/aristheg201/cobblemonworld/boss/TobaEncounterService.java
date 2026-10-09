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
    private static final Map<UUID, UUID> ACTIVE_PROXIES = new ConcurrentHashMap<>();
    private static final java.util.List<net.minecraft.world.entity.Entity> LOADED_ACTORS = new java.util.ArrayList<>();
    private static MinecraftServer server;
    private static long ticks;

    private TobaEncounterService() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof MysteriousFigureEntity || (entity instanceof NPCEntity npc
                    && npc.getInteraction() instanceof io.github.aristheg201.cobblemonworld.npc.CWorldNpcInteraction interaction
                    && interaction.definitionId().equals("mysterious"))) LOADED_ACTORS.add(entity);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (UUID owner : ACTIVE_ACTORS.keySet()) clearTransientEncounterState(owner);
            for (UUID entity : ACTIVE_ACTORS.values()) discardEntity(entity);
            for (UUID entity : ACTIVE_PROXIES.values()) discardEntity(entity);
            ProgressionStore.INSTANCE.save();
            ACTIVE_ACTORS.clear();
            ACTIVE_PROXIES.clear(); LOADED_ACTORS.clear();
            server = null;
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,s) -> {
            clearTransientEncounterState(handler.player.getUUID());
            discardEntity(ACTIVE_ACTORS.remove(handler.player.getUUID()));
            discardEntity(ACTIVE_PROXIES.remove(handler.player.getUUID()));
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
        return (p.narrative.schema == 0 || io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.main(p) != null && "mysterious".equals(io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.main(p).target()))
                && p.storyFlags.contains("school_trials_complete")
                && p.storyFlags.contains("toba_record_access")
                && p.storyFlags.contains("toba_meeting_revealed")
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
        for (var entity : LOADED_ACTORS) {
            if (!ACTIVE_ACTORS.containsValue(entity.getUUID()) && !ACTIVE_PROXIES.containsValue(entity.getUUID())) entity.discard();
        }
        LOADED_ACTORS.clear();
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
            } else if (current == TobaBossState.IDENTITY_REVEALED && (!isAtConfiguredLocation(player) || io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.state(player).narrative.finished.contains("final_explanation"))) {
                discardEntity(ACTIVE_ACTORS.remove(player.getUUID()));
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
            CWorldNetworking.toast(player, "story", "???", "story.cobblemonworld.final.arrived");
        }
    }

    public static TrainerBattleService.BattleAttempt beginFromConversation(ServerPlayer player) {
        UUID id = ACTIVE_ACTORS.get(player.getUUID());
        if (id == null) return rejected(player,"battle.cobblemonworld.unavailable");
        var actor = player.serverLevel().getEntity(id);
        if (actor instanceof MysteriousFigureEntity figure) return beginPhaseOne(player, figure);
        return rejected(player,"battle.cobblemonworld.unavailable");
    }
    private static TrainerBattleService.BattleAttempt rejected(ServerPlayer player,String reason) {
        io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.info("Final trainer action rejected player={} reason={}",player.getUUID(),reason);
        return new TrainerBattleService.BattleAttempt(false,reason);
    }
    public static TrainerBattleService.BattleAttempt beginPhaseOne(ServerPlayer player, MysteriousFigureEntity actor) {
        if (state(player) != TobaBossState.READY) return rejected(player,"battle.cobblemonworld.story_locked");
        if (!actor.isAlive() || actor.level()!=player.level() || actor.distanceToSqr(player)>64
                || actor.getOwnerUuid()!=null && !actor.getOwnerUuid().equals(player.getUUID()))
            return rejected(player,"battle.cobblemonworld.unavailable");
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null)
            return rejected(player,"battle.cobblemonworld.player_busy");

        var definition = NpcDefinitionRegistry.INSTANCE.get("mysterious");
        if (definition == null) return rejected(player,"battle.cobblemonworld.unavailable");

        NPCEntity proxy = TrainerBattleService.createNpc(player, definition);
        proxy.setInvisible(true);
        proxy.setCustomNameVisible(false);
        proxy.moveTo(actor.getX(), actor.getY(), actor.getZ(), actor.getYRot(), actor.getXRot());
        if (!player.serverLevel().addFreshEntity(proxy)) return rejected(player,"battle.cobblemonworld.unavailable");
        ACTIVE_PROXIES.put(player.getUUID(), proxy.getUUID());

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.add("mysterious_phase_one_active");
        ProgressionStore.INSTANCE.save();


        try {
            // Apply the same authoritative level-cap, party, binding and native
            // battle-result checks as every other trainer, including Mara.
            var result = TrainerBattleService.tryStartBattle(proxy,player,"mysterious");
            if (!result.started()) {
                proxy.discard(); ACTIVE_PROXIES.remove(player.getUUID()); p.storyFlags.remove("mysterious_phase_one_active"); ProgressionStore.INSTANCE.save();
            }
            return result;
        } catch (Exception e) {
            io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.error("Final trainer battle failed for {}", player.getUUID(), e);
            proxy.discard();
            ACTIVE_PROXIES.remove(player.getUUID());
            p.storyFlags.remove("mysterious_phase_one_active");
            ProgressionStore.INSTANCE.save();
            return rejected(player,"battle.cobblemonworld.unavailable");
        }
    }

    public static void cobblemonPhaseWon(ServerPlayer player) {
        if (state(player) != TobaBossState.PHASE_ONE_COBBLEMON) return;

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.storyFlags.remove("mysterious_phase_one_active");
        p.storyFlags.add("mysterious_phase_one_defeated");
        p.storyFlags.remove("toba_phase2_active");
        discardEntity(ACTIVE_PROXIES.remove(player.getUUID()));
        ProgressionStore.INSTANCE.save();

        if (findEntity(ACTIVE_ACTORS.get(player.getUUID())) instanceof MysteriousFigureEntity actor) {
            actor.setCustomName(Component.literal("TOBA"));
            actor.setCustomNameVisible(true);
        }

        CampaignService.setFlag(player, "toba_identity_revealed");
        CampaignService.setFlag(player, "toba_battle_won");

        CWorldNetworking.toast(player, "story", "TOBA", "story.cobblemonworld.final.revealed");
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
        discardEntity(ACTIVE_PROXIES.remove(player.getUUID()));
        CWorldNetworking.toast(player, "story", "story.cobblemonworld.final.meeting", "story.cobblemonworld.final.retry");
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
