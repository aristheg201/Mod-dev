package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.npc.NPCPreset;
import com.cobblemon.mod.common.api.npc.NPCPresets;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Native Cobblemon trainer body. It follows Cobblemon's NPCClass construction path and can layer one NPCPreset over
 * a private copy of the class before entity initialization. Registry classes are never mutated globally.
 * Body key format: {@code npc:<namespace:id>;preset=<namespace:id>;level=<n>;skill=<1..5>;native_interaction=<bool>}.
 */
public final class CobblemonTrainerBody implements NpcBody {
    private final UUID npcId;
    private NPCEntity entity;

    public CobblemonTrainerBody(UUID npcId) { this.npcId = npcId; }

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
        if (world == null) throw new IllegalArgumentException("unknown world " + definition.world());
        Spec spec = Spec.parse(definition.bodyKey());

        NPCClass registered = NPCClasses.getByIdentifier(spec.npcClass());
        if (registered == null) throw new IllegalArgumentException("unknown Cobblemon NPC class " + spec.npcClass());
        NPCClass npcClass = copyClass(registered);
        if (spec.preset() != null) {
            NPCPreset preset = NPCPresets.getPreset(spec.preset());
            if (preset == null) throw new IllegalArgumentException("unknown Cobblemon NPC preset " + spec.preset());
            preset.applyTo(npcClass);
        }

        NPCEntity created = new NPCEntity(world);
        created.setUuid(npcId);
        created.refreshPositionAndAngles(definition.x(), definition.y(), definition.z(), definition.yaw(), definition.pitch());
        created.setNpc(npcClass);
        created.initialize(spec.level());
        created.setSkill(spec.skill());
        created.setMovable(false);
        created.setInvulnerable(definition.invulnerable());
        created.setCustomName(Text.literal(definition.name()));
        created.setCustomNameVisible(definition.nameVisible());
        created.setSilent(definition.silent());
        created.setNoGravity(!definition.gravity());
        created.addCommandTag("lively");
        created.addCommandTag("lively_body");
        created.addCommandTag("lively_combat");
        if (spec.nativeInteraction()) created.addCommandTag("lively_native_interaction");
        created.setPersistent();
        if (!world.spawnEntity(created)) throw new IllegalStateException("Cobblemon world rejected trainer NPC body");
        entity = created;
    }

    private static NPCClass copyClass(NPCClass source) {
        NPCClass copy = new NPCClass();
        copy.setId(source.getId());
        copy.setResourceIdentifier(source.getResourceIdentifier());
        copy.setNames(new ArrayList<>(source.getNames()));
        copy.setAspects(new HashSet<>(source.getAspects()));
        copy.setHitbox(source.getHitbox());
        copy.setModelScale(source.getModelScale());
        copy.setBattleConfiguration(source.getBattleConfiguration());
        copy.setInteraction(source.getInteraction());
        copy.setCanDespawn(source.getCanDespawn());
        copy.setVariations(new HashMap<>(source.getVariations()));
        copy.setConfig(new ArrayList<>(source.getConfig()));
        copy.setVariables(new HashMap<>(source.getVariables()));
        copy.setParty(source.getParty());
        copy.setSkill(source.getSkill());
        copy.setAutoHealParty(source.getAutoHealParty());
        copy.setRandomizePartyOrder(source.getRandomizePartyOrder());
        copy.setBattleTheme(source.getBattleTheme());
        copy.setBehaviours(new ArrayList<>(source.getBehaviours()));
        copy.setMovable(source.isMovable());
        copy.setInvulnerable(source.isInvulnerable());
        copy.setLeashable(source.isLeashable());
        copy.setAllowProjectileHits(source.getAllowProjectileHits());
        copy.setHideNameTag(source.getHideNameTag());
        return copy;
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
            NPCEntity replacement = (NPCEntity) target.getEntity(npcId);
            if (replacement != null) entity = replacement;
        } else entity.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
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
        entity.setYaw(yaw); entity.setPitch(pitch); entity.setHeadYaw(yaw);
    }

    @Override
    public AnimationResult animate(MinecraftServer server, AnimationRequest request) {
        if (!spawned()) return AnimationResult.unsupported(request.name(), "Cobblemon trainer body is not spawned");
        String requested = request.name();
        String nativeName = switch (requested) {
            case "attack", "punch", "punch_right" -> "punch_right";
            case "punch_left" -> "punch_left";
            case "win", "victory" -> "win";
            case "lose", "defeat" -> "lose";
            case "sendout", "send_out" -> "send_out";
            case "return", "recall" -> "recall";
            default -> requested.startsWith("native:") ? requested.substring("native:".length()) : requested;
        };
        nativeName = nativeName.toLowerCase(Locale.ROOT);
        if (nativeName.isBlank() || !nativeName.matches("[a-z0-9_./:-]{1,96}")) {
            return AnimationResult.unsupported(requested, "invalid Cobblemon trainer animation name");
        }
        entity.playAnimation(nativeName, List.of());
        return AnimationResult.played(requested, "Cobblemon trainer native animation: " + nativeName);
    }

    @Override
    public void tick(MinecraftServer server, NpcDefinition definition) {
        if (!spawned()) return;
        entity.setCustomName(Text.literal(definition.name()));
        entity.setCustomNameVisible(definition.nameVisible());
        entity.setInvulnerable(definition.invulnerable());
        entity.setSilent(definition.silent());
        entity.setNoGravity(!definition.gravity());
        entity.setMovable(false);
    }

    private record Spec(Identifier npcClass, Identifier preset, int level, int skill, boolean nativeInteraction) {
        static Spec parse(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (!value.startsWith("npc:")) throw new IllegalArgumentException("trainer body must start with npc:");
            String[] parts = value.substring(4).split(";");
            Identifier id = Identifier.tryParse(parts[0].trim());
            if (id == null) throw new IllegalArgumentException("invalid Cobblemon NPC class identifier");
            Map<String, String> options = new HashMap<>();
            for (int i = 1; i < parts.length; i++) {
                int split = parts[i].indexOf('=');
                if (split > 0) options.put(parts[i].substring(0, split).trim(), parts[i].substring(split + 1).trim());
            }
            Identifier preset = null;
            String rawPreset = options.get("preset");
            if (rawPreset != null && !rawPreset.isBlank()) {
                preset = Identifier.tryParse(rawPreset);
                if (preset == null) throw new IllegalArgumentException("invalid Cobblemon NPC preset identifier");
            }
            int level = boundedInt(options.get("level"), 1, 1, 1000, "level");
            int skill = boundedInt(options.get("skill"), 3, 1, 5, "skill");
            boolean nativeInteraction = Boolean.parseBoolean(options.getOrDefault("native_interaction", "true"));
            return new Spec(id, preset, level, skill, nativeInteraction);
        }

        private static int boundedInt(String raw, int fallback, int min, int max, String name) {
            if (raw == null || raw.isBlank()) return fallback;
            try {
                int value = Integer.parseInt(raw);
                if (value < min || value > max) throw new IllegalArgumentException(name + " out of range");
                return value;
            } catch (NumberFormatException error) { throw new IllegalArgumentException("invalid " + name, error); }
        }
    }

    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }
}
