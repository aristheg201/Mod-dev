package vn.svframe.lively.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;

/** Physical representation of a logical NPC. Implementations must never mutate terrain. */
public interface NpcBody {
    UUID npcId();
    NpcDefinition.BodyType type();
    boolean spawned();
    Optional<UUID> entityUuid();
    default Optional<Vec3d> position() { return Optional.empty(); }
    default Optional<String> worldKey() { return Optional.empty(); }
    void spawn(MinecraftServer server, NpcDefinition definition);
    void despawn(MinecraftServer server);
    void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch);
    default void moveStep(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        teleport(server, worldKey, position, yaw, pitch);
    }
    void lookAt(MinecraftServer server, Vec3d target);
    void tick(MinecraftServer server, NpcDefinition definition);
    default void onViewerJoin(ServerPlayerEntity player, NpcDefinition definition) {}
    default void onInteract(ServerPlayerEntity player) {}
}
