package vn.svframe.lively.quest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only world/interaction checks for exploration, social, semantic delivery and escort completion. */
public final class QuestWorldProgressionBootstrap implements ModInitializer {
    private final ConcurrentHashMap<UUID, Long> lastInteractionSignal = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)
                    || LivelyApi.npcs() == null) return ActionResult.PASS;
            UUID npcId = LivelyApi.npcs().npcForEntity(entity.getUuid()).orElse(null);
            if (npcId == null) return ActionResult.PASS;
            long tick = serverPlayer.getServer().getTicks();
            long previous = lastInteractionSignal.getOrDefault(serverPlayer.getUuid(), Long.MIN_VALUE / 2L);
            if (tick - previous >= 20L) {
                lastInteractionSignal.put(serverPlayer.getUuid(), tick);
                ActorId owner = new ActorId(serverPlayer.getUuid(), ActorId.Kind.PLAYER);
                LivelyApi.quests().signal(owner, QuestRuntime.ObjectiveType.SOCIAL, npcId.toString(), 1L,
                        Map.of("actor", npcId.toString(), "npc", npcId.toString()));
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20L != 0L) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) progress(player);
            if (server.getTicks() % 1200L == 0L && lastInteractionSignal.size() > 4096) {
                long cutoff = server.getTicks() - 1200L;
                lastInteractionSignal.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            }
        });
    }

    private void progress(ServerPlayerEntity player) {
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        for (QuestRuntime.Quest quest : LivelyApi.quests().byOwner(owner).stream()
                .filter(value -> value.status() == QuestRuntime.Status.ACTIVE).limit(64).toList()) {
            for (QuestRuntime.Objective objective : quest.objectives()) {
                if (quest.progress().getOrDefault(objective.id(), 0L) >= objective.required()) continue;
                boolean complete = switch (objective.type()) {
                    case EXPLORATION -> atDestination(player, objective);
                    case DELIVERY -> Boolean.parseBoolean(objective.facts().getOrDefault("semantic_delivery", "false"))
                            && atDestination(player, objective);
                    case ESCORT -> atDestination(player, objective) && escortArrived(quest, objective);
                    default -> false;
                };
                if (complete) LivelyApi.quests().progress(quest.id(), objective.id(), 1L);
            }
        }
    }

    private boolean escortArrived(QuestRuntime.Quest quest, QuestRuntime.Objective objective) {
        if (quest.issuer() == null || quest.issuer().kind() != ActorId.Kind.NPC || LivelyApi.npcs() == null) return false;
        Vec3d position = LivelyApi.npcs().position(quest.issuer().uuid()).orElse(null);
        String world = LivelyApi.npcs().worldKey(quest.issuer().uuid()).orElse(null);
        return position != null && world != null && atDestination(world, position, objective);
    }

    private boolean atDestination(ServerPlayerEntity player, QuestRuntime.Objective objective) {
        return atDestination(player.getServerWorld().getRegistryKey().getValue().toString(), player.getPos(), objective);
    }

    private boolean atDestination(String world, Vec3d position, QuestRuntime.Objective objective) {
        String structureId = objective.facts().getOrDefault("structure", objective.target());
        SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(structureId).orElse(null);
        if (structure != null) return structure.bounds().world().equals(world) && inside(structure.bounds(), position);

        Map<String, String> facts = objective.facts();
        String targetWorld = facts.get("world");
        if (targetWorld == null || !targetWorld.equals(world)) return false;
        try {
            double x = Double.parseDouble(facts.get("x"));
            double y = Double.parseDouble(facts.get("y"));
            double z = Double.parseDouble(facts.get("z"));
            double radius = Math.max(1D, Math.min(32D, Double.parseDouble(facts.getOrDefault("radius", "3"))));
            return position.squaredDistanceTo(new Vec3d(x, y, z)) <= radius * radius;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean inside(SemanticStructureRegistry.Bounds bounds, Vec3d position) {
        return position.x >= bounds.minX() && position.x <= bounds.maxX() + 1D
                && position.y >= bounds.minY() && position.y <= bounds.maxY() + 1D
                && position.z >= bounds.minZ() && position.z <= bounds.maxZ() + 1D;
    }
}
