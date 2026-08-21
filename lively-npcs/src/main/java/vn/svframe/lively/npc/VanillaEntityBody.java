package vn.svframe.lively.npc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Uses a real vanilla entity type so vanilla clients need no client-side mod or resource pack. */
public final class VanillaEntityBody implements NpcBody {
    private final UUID npcId;
    private Entity entity;

    public VanillaEntityBody(UUID npcId) { this.npcId = npcId; }

    @Override public UUID npcId() { return npcId; }
    @Override public NpcDefinition.BodyType type() { return NpcDefinition.BodyType.VANILLA; }
    @Override public boolean spawned() { return entity != null && !entity.isRemoved(); }
    @Override public Optional<UUID> entityUuid() { return spawned() ? Optional.of(entity.getUuid()) : Optional.empty(); }

    @Override
    public void spawn(MinecraftServer server, NpcDefinition definition) {
        if (spawned()) return;
        ServerWorld world = world(server, definition.world());
        if (world == null) throw new IllegalArgumentException("unknown world " + definition.world());
        Identifier typeId = Identifier.tryParse(definition.bodyKey());
        if (typeId == null) throw new IllegalArgumentException("invalid vanilla entity type");
        EntityType<?> type = Registries.ENTITY_TYPE.get(typeId);
        if (type == null || type == EntityType.PLAYER) throw new IllegalArgumentException("unsupported vanilla entity type " + typeId);
        Entity created = type.create(world, null, BlockPos.ofFloored(definition.x(), definition.y(), definition.z()), SpawnReason.COMMAND, false, false);
        if (created == null) throw new IllegalStateException("entity factory returned null for " + typeId);
        created.setUuid(npcId);
        created.refreshPositionAndAngles(definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch());
        created.setCustomName(Text.literal(definition.name()));
        created.setCustomNameVisible(definition.nameVisible());
        created.setInvulnerable(definition.invulnerable());
        created.setNoGravity(!definition.gravity());
        created.setSilent(definition.silent());
        if (created instanceof MobEntity mob) {
            mob.setPersistent();
            mob.setAiDisabled(true); // Lively owns movement/decisions; vanilla goal AI must not fight it.
        }
        if (!world.spawnEntity(created)) throw new IllegalStateException("world rejected npc entity spawn");
        entity = created;
    }

    @Override
    public void despawn(MinecraftServer server) {
        if (entity != null && !entity.isRemoved()) entity.discard();
        entity = null;
    }

    @Override
    public void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        if (!spawned()) return;
        ServerWorld target = world(server, worldKey);
        if (target == null) return;
        Entity current = entity;
        boolean success = current.teleport(target, position.x, position.y, position.z, Set.of(), yaw, pitch);
        if (!success) current.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        if (current.getWorld() != target) {
            Entity replacement = target.getEntity(npcId);
            if (replacement != null) entity = replacement;
        }
    }

    @Override
    public void lookAt(MinecraftServer server, Vec3d target) {
        if (!spawned()) return;
        Vec3d delta = target.subtract(entity.getPos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        entity.setYaw(yaw); entity.setPitch(pitch); entity.setHeadYaw(yaw);
    }

    @Override
    public void tick(MinecraftServer server, NpcDefinition definition) {
        if (!spawned()) return;
        if (entity instanceof LivingEntity living) {
            living.setInvulnerable(definition.invulnerable());
            living.setNoGravity(!definition.gravity());
        }
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
