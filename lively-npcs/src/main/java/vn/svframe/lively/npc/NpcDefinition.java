package vn.svframe.lively.npc;

import net.minecraft.util.Identifier;

import java.util.HashMap;
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
        Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(role); Objects.requireNonNull(bodyType);
        bodyKey = bodyKey == null ? "" : bodyKey.trim(); skinName = skinName == null ? "" : skinName.trim();
        Objects.requireNonNull(world); metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("invalid npc name");
        if (role.isBlank() || role.length() > 64) throw new IllegalArgumentException("invalid npc role");
        if (bodyType == BodyType.VANILLA && Identifier.tryParse(bodyKey) == null) throw new IllegalArgumentException("invalid vanilla entity identifier");
        if (bodyType == BodyType.EXTERNAL && bodyKey.isBlank()) throw new IllegalArgumentException("external body key required");
        if (skinName.length() > 8192) throw new IllegalArgumentException("skin source too long");
        if (Math.abs(x) > 30_000_000D || Math.abs(z) > 30_000_000D || y < -4096D || y > 4096D) throw new IllegalArgumentException("invalid npc position");
        if (metadata.size() > 256) throw new IllegalArgumentException("npc metadata too large");
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
    public NpcDefinition withSkin(String source) {
        return new NpcDefinition(id, name, role, bodyType, bodyKey, source, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }
    public NpcDefinition withNameRole(String nextName, String nextRole) {
        return new NpcDefinition(id, nextName, nextRole, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, metadata);
    }
    public NpcDefinition withFlags(boolean nextAi, boolean nextInvulnerable, boolean nextGravity, boolean nextSilent, boolean nextNameVisible) {
        return new NpcDefinition(id, name, role, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                spawned, nextAi, nextInvulnerable, nextGravity, nextSilent, nextNameVisible, metadata);
    }
    public NpcDefinition withMetadata(String key, String value) {
        String normalized = normalizeMetadataKey(key); HashMap<String, String> next = new HashMap<>(metadata);
        if (value == null) next.remove(normalized); else {
            if (value.length() > 4096) throw new IllegalArgumentException("metadata value too long");
            next.put(normalized, value);
        }
        return new NpcDefinition(id, name, role, bodyType, bodyKey, skinName, world, x, y, z, yaw, pitch,
                spawned, aiEnabled, invulnerable, gravity, silent, nameVisible, next);
    }
    private static String normalizeMetadataKey(String key) {
        if (key == null) throw new IllegalArgumentException("metadata key required");
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.:-]", "_");
        if (normalized.isBlank() || normalized.length() > 96) throw new IllegalArgumentException("invalid metadata key");
        return normalized;
    }
}
