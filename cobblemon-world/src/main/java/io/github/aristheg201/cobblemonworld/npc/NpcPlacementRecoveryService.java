package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public final class NpcPlacementRecoveryService {
    private static long ticks;
    private static final Map<NpcPlacement, Long> MISSING_SINCE = new HashMap<>();

    private NpcPlacementRecoveryService() {}

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ticks = 0; MISSING_SINCE.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 40L != 0L || server.getPlayerList().getPlayers().isEmpty()) return;
            MISSING_SINCE.keySet().retainAll(NpcPlacementStore.INSTANCE.all().values());

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
                // A chunk can be present while its saved entities are still loading asynchronously.
                // Wait for that load to finish before deciding the authored NPC is missing.
                if (!level.hasChunkAt(pos) || !level.isPositionEntityTicking(pos)
                        || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong())) {
                    MISSING_SINCE.remove(placement); continue;
                }

                // Recovery only matters while a player can actually observe/use the NPC.
                // Check proximity before UUID/entity work so large authored NPC catalogs do
                // not scan loaded entity maps for remote placements every two seconds.
                var bootstrapPlayer = level.players().stream()
                        .filter(player -> player.distanceToSqr(placement.x(), placement.y(), placement.z()) <= 32.0 * 32.0)
                        .findFirst()
                        .orElse(null);
                if (bootstrapPlayer == null) { MISSING_SINCE.remove(placement); continue; }

                if (loadedInExpectedLevel(level, placement)) { MISSING_SINCE.remove(placement); continue; }

                // Entity lookup can briefly be empty while a previously loaded chunk
                // transitions out and back in. Never replace its saved UUID in that gap.
                // A genuinely missing NPC must stay absent in a nearby ticking chunk
                // over multiple recovery passes before a replacement is created.
                long since = MISSING_SINCE.computeIfAbsent(placement, ignored -> ticks);
                if (ticks - since < 80) continue;

                var definition = NpcDefinitionRegistry.INSTANCE.get(placement.id());
                if (definition == null || definition.specialActor() || io.github.aristheg201.cobblemonworld.narrative.PersonalActors.personal(definition.id())) continue;

                NPCEntity npc = TrainerBattleService.createNpc(bootstrapPlayer, definition);
                npc.moveTo(placement.x(), placement.y(), placement.z(), placement.yaw(), 0.0F);
                AnchoredNpcService.authoredPose(npc, placement.yaw());
                if (!level.addFreshEntity(npc)) continue;
                MISSING_SINCE.remove(placement);

                NpcPlacementStore.INSTANCE.put(new NpcPlacement(
                        placement.id(), placement.dimension(),
                        placement.x(), placement.y(), placement.z(),
                        placement.yaw(), 0.0F, npc.getUUID().toString()
                ));
            }
        });
    }

    private static boolean loadedInExpectedLevel(ServerLevel level, NpcPlacement placement) {
        if (placement.entityUuid() == null || placement.entityUuid().isBlank()) return false;
        try {
            UUID uuid = UUID.fromString(placement.entityUuid());
            if (!(level.getEntity(uuid) instanceof NPCEntity npc)) return false;
            NpcBindingService.restore(npc, placement, false);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
