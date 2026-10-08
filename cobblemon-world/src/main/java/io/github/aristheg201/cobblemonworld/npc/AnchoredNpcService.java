package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Explicit pose controller. Vanilla navigation stays disabled; authored anchors remain authoritative. */
public final class AnchoredNpcService {
    private record Attention(UUID player, long until) {}
    private static final Map<UUID, Attention> ATTENTION = new HashMap<>();
    private static final Set<NPCEntity> LOADED = new LinkedHashSet<>();
    private AnchoredNpcService() {}
    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ATTENTION.clear(); LOADED.clear(); });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof NPCEntity npc) {
                var placement = NpcBindingService.placement(npc);
                // Bind during load, before vanilla gets its first despawn tick.
                if (placement == null || !NpcBindingService.restore(npc, placement, true)) LOADED.add(npc);
            }
        });
        // Spawn chunks can load before the placement store's SERVER_STARTED callback.
        // Resolve them on the first tick, after all persisted stores are available.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (var level : server.getAllLevels()) for (var entity : level.getAllEntities())
                if (entity instanceof NPCEntity npc) LOADED.add(npc);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Re-placing/removing an NPC whose former chunk is unloaded must not bring back
            // its old saved entity later. Check after the placement command has saved its UUID.
            for (var npc : LOADED) {
                if (io.github.aristheg201.cobblemonworld.narrative.PersonalActors.owner(npc) != null) continue;
                var saved = NpcBindingService.placement(npc);
                if (saved != null && NpcBindingService.restore(npc, saved, true)) continue;
                if (!(npc.getInteraction() instanceof CWorldNpcInteraction interaction)) continue;
                var definition = NpcDefinitionRegistry.INSTANCE.get(interaction.definitionId());
                if (definition == null) { npc.discard(); continue; }
                if (definition != null && definition.specialActor()) continue;
                var placement = NpcPlacementStore.INSTANCE.get(interaction.definitionId());
                if (placement == null || !npc.getUUID().toString().equals(placement.entityUuid())) npc.discard();
            }
            LOADED.clear();
            for (var placement : NpcPlacementStore.INSTANCE.all().values()) {
                var id = ResourceLocation.tryParse(placement.dimension());
                if (id == null || placement.entityUuid() == null) continue;
                var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (level == null) continue;
                UUID uuid;
                try { uuid = UUID.fromString(placement.entityUuid()); } catch (IllegalArgumentException e) { continue; }
                if (!(level.getEntity(uuid) instanceof NPCEntity npc)) continue;
                if (!NpcBindingService.restore(npc, placement, false)) continue;
                tickPose(npc, placement);
            }
        });
    }
    public static void tickPose(NPCEntity npc, NpcPlacement placement) {
        var level=npc.level(); var server=npc.getServer(); UUID uuid=npc.getUUID();
                if (npc.distanceToSqr(placement.x(), placement.y(), placement.z()) > 0.0001) {
                    npc.setPos(placement.x(), placement.y(), placement.z());
                }
                npc.setDeltaMovement(Vec3.ZERO);
                npc.setNoAi(true);
                npc.setXRot(0);
                float body = placement.yaw();
                float head = body;
                var attention = ATTENTION.get(uuid);
                var player = attention == null ? null : server.getPlayerList().getPlayer(attention.player());
                if (player != null && level.getGameTime() < attention.until() && player.level() == level && player.distanceToSqr(npc) < 100) {
                    body = facing(npc, player);
                    head = body;
                } else {
                    ATTENTION.remove(uuid);
                    // Small, slow idle glance; deterministic phase keeps neighboring NPCs from moving together.
                    head += (float) Math.sin((level.getGameTime() + (uuid.hashCode() & 255)) / 38.0) * 9.0f;
                }
                npc.setYRot(Mth.approachDegrees(npc.getYRot(), body, 5));
                npc.setYBodyRot(npc.getYRot());
                npc.setYHeadRot(Mth.approachDegrees(npc.getYHeadRot(), head, 6));
    }
    private static float facing(NPCEntity npc, ServerPlayer player) {
        return (float) Math.toDegrees(Math.atan2(-(player.getX() - npc.getX()), player.getZ() - npc.getZ()));
    }
    public static void facePlayer(NPCEntity npc, ServerPlayer player) {
        float yaw = facing(npc, player);
        npc.setXRot(0); npc.setYRot(yaw); npc.setYBodyRot(yaw); npc.setYHeadRot(yaw);
        ATTENTION.put(npc.getUUID(), new Attention(player.getUUID(), player.serverLevel().getGameTime() + 100));
    }
    public static void authoredPose(NPCEntity npc, float yaw) {
        ATTENTION.remove(npc.getUUID());
        npc.setXRot(0); npc.setYRot(yaw); npc.setYBodyRot(yaw); npc.setYHeadRot(yaw);
    }
}
