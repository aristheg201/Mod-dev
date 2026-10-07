package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class NpcPlacementRecoveryService {
    private static long ticks;

    private NpcPlacementRecoveryService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 40L != 0L || server.getPlayerList().getPlayers().isEmpty()) return;

            for (NpcPlacement placement : NpcPlacementStore.INSTANCE.all().values()) {
                ResourceLocation dimensionId;
                try {
                    dimensionId = ResourceLocation.parse(placement.dimension());
                } catch (Exception ignored) {
                    continue;
                }

                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
                ServerLevel level = server.getLevel(key);
                if (level == null) continue;

                var pos = net.minecraft.core.BlockPos.containing(placement.x(), placement.y(), placement.z());
                if (!level.hasChunkAt(pos)) continue;

                // Recovery only matters while a player can actually observe/use the NPC.
                // Check proximity before UUID/entity work so large authored NPC catalogs do
                // not scan loaded entity maps for remote placements every two seconds.
                var bootstrapPlayer = level.players().stream()
                        .filter(player -> player.distanceToSqr(placement.x(), placement.y(), placement.z()) <= 160.0 * 160.0)
                        .findFirst()
                        .orElse(null);
                if (bootstrapPlayer == null) continue;

                if (loadedInExpectedLevel(level, placement)) continue;

                var definition = NpcDefinitionRegistry.INSTANCE.get(placement.id());
                if (definition == null || definition.specialActor()) continue;

                NPCEntity npc = TrainerBattleService.createNpc(bootstrapPlayer, definition);
                npc.moveTo(placement.x(), placement.y(), placement.z(), placement.yaw(), placement.pitch());
                if (!level.addFreshEntity(npc)) continue;

                NpcPlacementStore.INSTANCE.put(new NpcPlacement(
                        placement.id(), placement.dimension(),
                        placement.x(), placement.y(), placement.z(),
                        placement.yaw(), placement.pitch(), npc.getUUID().toString()
                ));
            }
        });
    }

    private static boolean loadedInExpectedLevel(ServerLevel level, NpcPlacement placement) {
        if (placement.entityUuid() == null || placement.entityUuid().isBlank()) return false;
        try {
            UUID uuid = UUID.fromString(placement.entityUuid());
            return level.getEntity(uuid) instanceof NPCEntity;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
