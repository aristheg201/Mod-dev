package vn.svframe.lively.npc;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persisted command-created NPC definition. Natural spawning is deliberately unsupported. */
public record NpcDefinition(
        UUID id,
        String name,
        String role,
        BodyType bodyType,
        String bodyKey,
        String skinName,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean spawned,
        boolean aiEnabled,
        boolean invulnerable,
        boolean gravity,
        boolean silent,
        boolean nameVisible,
        Map<String, String> metadata
) {
    public enum BodyType { PLAYER, VANILLA, EXTERNAL }

    public NpcDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(role);
        Objects.requireNonNull(bodyType);
        bodyKey = bodyKey == null ? "" : bodyKey.trim();
        skinName = skinName == null ? "" : skinName.trim();
        Objects.requireNonNull(world);
        metadata = Map.copyOf(metadata);
        if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("invalid npc name");
        if (role.isBlank() || role.length() > 64) throw new IllegalArgumentException("invalid npc role");
        if (bodyType == BodyType.VANILLA) Identifier.tryParse(bodyKey);
        if (Math.abs(x) > 30_000_000D || Math.abs(z) > 30_000_000D || y < -4096D || y > 4096D) throw new IllegalArgumentException("invalid npc position");
    }

    public NpcDefinition withPosition(String world, double x, double y, double z, float yaw, float pitch) {
        return new NpcDefinition(id, name, role, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }

    public NpcDefinition withSpawned(boolean value) {
        return new NpcDefinition(id, name, role, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                value, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }

    public NpcDefinition withBody(BodyType type, String key, String skin) {
        return new NpcDefinition(id, name, role, type, key, skin, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }

    public NpcDefinition withNameRole(String nextName, String nextRole) {
        return new NpcDefinition(id, nextName, nextRole, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }
}
