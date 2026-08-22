package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.animation.AnimationRequest;
import vn.svframe.lively.animation.AnimationResult;
import vn.svframe.lively.npc.NpcBody;
import vn.svframe.lively.npc.NpcDefinition;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Command-created Cobblemon body. It is never registered in Cobblemon natural spawning or gameplay capture/battle loops. */
public final class CobblemonPokemonBody implements NpcBody {
    private final UUID npcId;
    private PokemonEntity entity;

    public CobblemonPokemonBody(UUID id) { npcId = id; }

    @Override public UUID npcId() { return npcId; }
    @Override public NpcDefinition.BodyType type() { return NpcDefinition.BodyType.EXTERNAL; }
    @Override public boolean spawned() { return entity != null && !entity.isRemoved(); }
    @Override public Optional<UUID> entityUuid() { return spawned() ? Optional.of(entity.getUuid()) : Optional.empty(); }
    @Override public Optional<Vec3d> position() { return spawned() ? Optional.of(entity.getPos()) : Optional.empty(); }
    @Override public Optional<String> worldKey() { return spawned() ? Optional.of(entity.getWorld().getRegistryKey().getValue().toString()) : Optional.empty(); }

    @Override
    public void spawn(MinecraftServer server, NpcDefinition definition) {
        if (spawned()) return;
        ServerWorld world = world(server, definition.world());
        if (world == null) throw new IllegalArgumentException("unknown world");
        String properties = definition.bodyKey();
        if (properties.startsWith("cobblemon:")) properties = properties.substring("cobblemon:".length());
        PokemonProperties parsed = PokemonProperties.Companion.parse(properties);
        PokemonEntity created = parsed.createEntity(world);
        created.setUuid(npcId);
        created.refreshPositionAndAngles(definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch());
        created.setCustomName(Text.literal(definition.name()));
        created.setCustomNameVisible(definition.nameVisible());
        created.setInvulnerable(definition.invulnerable());
        created.setNoGravity(!definition.gravity());
        created.setSilent(definition.silent());
        created.addCommandTag("lively");
        created.addCommandTag("lively_body");
        created.setPersistent();
        created.setAiDisabled(true);
        UncatchableProperty.INSTANCE.uncatchable().apply(created);
        created.getDataTracker().set(PokemonEntity.getUNBATTLEABLE(), true);
        if (!world.spawnEntity(created)) throw new IllegalStateException("Cobblemon world rejected Pokemon NPC body");
        entity = created;
    }

    @Override public void despawn(MinecraftServer server) { if (entity != null && !entity.isRemoved()) entity.discard(); entity = null; }

    @Override
    public void teleport(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        if (!spawned()) return;
        ServerWorld target = world(server, worldKey);
        if (target == null) return;
        if (entity.getWorld() != target) {
            boolean ok = entity.teleport(target, position.x, position.y, position.z, Set.of(), yaw, pitch);
            if (!ok) { despawn(server); return; }
            PokemonEntity replacement = (PokemonEntity) target.getEntity(npcId);
            if (replacement != null) entity = replacement;
        } else entity.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        harden(entity);
    }

    @Override
    public void moveStep(MinecraftServer server, String worldKey, Vec3d position, float yaw, float pitch) {
        if (!spawned() || !worldKey().orElse("").equals(worldKey)) return;
        entity.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        entity.setHeadYaw(yaw);
    }

    @Override
    public void lookAt(MinecraftServer server, Vec3d target) {
        if (!spawned()) return;
        Vec3d delta = target.subtract(entity.getPos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        entity.setYaw(yaw);
        entity.setPitch(pitch);
        entity.setHeadYaw(yaw);
    }

    @Override
    public AnimationResult animate(MinecraftServer server, AnimationRequest request) {
        if (!spawned()) return AnimationResult.unsupported(request.name(), "Cobblemon Pokemon body is not spawned");
        String name = request.name();
        switch (name) {
            case "idle", "stand", "standing", "reset" -> {
                setPose(PoseType.STAND, false);
                return AnimationResult.played(name, "Cobblemon STAND pose");
            }
            case "walk", "walking" -> {
                entity.setSprinting(false);
                setPose(PoseType.WALK, false);
                return AnimationResult.played(name, "Cobblemon WALK pose");
            }
            case "run", "running", "sprint" -> {
                entity.setSprinting(true);
                setPose(PoseType.WALK, false);
                return AnimationResult.played(name, "Cobblemon WALK pose with sprint state");
            }
            case "sleep" -> {
                setPose(PoseType.SLEEP, false);
                return AnimationResult.played(name, "Cobblemon SLEEP pose");
            }
            case "hover" -> {
                setPose(PoseType.HOVER, false);
                return AnimationResult.played(name, "Cobblemon HOVER pose");
            }
            case "fly", "flying" -> {
                setPose(PoseType.FLY, false);
                return AnimationResult.played(name, "Cobblemon FLY pose");
            }
            case "float" -> {
                setPose(PoseType.FLOAT, false);
                return AnimationResult.played(name, "Cobblemon FLOAT pose");
            }
            case "swim", "swimming" -> {
                setPose(PoseType.SWIM, false);
                return AnimationResult.played(name, "Cobblemon SWIM pose");
            }
            case "glide", "gliding" -> {
                setPose(PoseType.GLIDE, false);
                return AnimationResult.played(name, "Cobblemon GLIDE pose");
            }
            case "cry" -> {
                entity.cry();
                return AnimationResult.played(name, "Cobblemon native cry animation");
            }
            case "attack", "physical", "physical_attack" -> {
                return nativeAnimation("physical", name);
            }
            case "special", "special_attack" -> {
                return nativeAnimation("special", name);
            }
            case "status", "status_attack" -> {
                return nativeAnimation("status", name);
            }
            case "hurt", "recoil", "damage" -> {
                return nativeAnimation("recoil", name);
            }
            default -> {
                String nativeName = name.startsWith("native:") ? name.substring("native:".length()) : name;
                if (nativeName.isBlank() || !nativeName.matches("[a-z0-9_./:-]{1,96}")) {
                    return AnimationResult.unsupported(name, "invalid Cobblemon animation name");
                }
                return nativeAnimation(nativeName.toLowerCase(Locale.ROOT), name);
            }
        }
    }

    private AnimationResult nativeAnimation(String nativeName, String requestedName) {
        entity.playAnimation(nativeName, List.of());
        return AnimationResult.played(requestedName, "Cobblemon native animation: " + nativeName);
    }

    private void setPose(PoseType pose, boolean allowRecalculation) {
        entity.setEnablePoseTypeRecalculation(allowRecalculation);
        entity.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), pose);
    }

    @Override
    public void tick(MinecraftServer server, NpcDefinition definition) {
        if (!spawned()) return;
        entity.setCustomName(Text.literal(definition.name()));
        entity.setCustomNameVisible(definition.nameVisible());
        entity.setInvulnerable(definition.invulnerable());
        entity.setNoGravity(!definition.gravity());
        entity.setSilent(definition.silent());
        harden(entity);
    }

    private static void harden(PokemonEntity value) {
        value.setAiDisabled(true);
        UncatchableProperty.INSTANCE.uncatchable().apply(value);
        value.getDataTracker().set(PokemonEntity.getUNBATTLEABLE(), true);
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
