package vn.svframe.mythiclibfabric;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native packet-level equivalent of Bukkit Player#setHealthScaled/setHealthScale. */
public final class MythicLibHealthScale {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Double> OVERRIDES = new ConcurrentHashMap<>();

    private MythicLibHealthScale() {}

    public static void onJoin(ServerPlayerEntity player) {
        if (player == null) return;
        ACTIVE.remove(player.getUuid());
        MythicLibGeneralSettings settings = MythicLibFabricMod.settings();
        Double override = OVERRIDES.get(player.getUuid());
        if (override == null && (settings == null || !settings.healthScale().enabled())) return;
        int delay = override != null ? 0 : Math.max(0, settings.healthScale().delayTicks());
        MythicLibFabricMod.schedule(delay, () -> {
            if (player.isDisconnected()) return;
            MythicLibGeneralSettings current = MythicLibFabricMod.settings();
            Double playerScale = OVERRIDES.get(player.getUuid());
            if (playerScale == null && (current == null || !current.healthScale().enabled())) return;
            ACTIVE.add(player.getUuid());
            sendScaledMaxHealth(player, playerScale != null ? playerScale : current.healthScale().scale());
            player.markHealthDirty();
        });
    }

    public static void onDisconnect(UUID playerId) {
        if (playerId != null) ACTIVE.remove(playerId);
    }

    public static void onReload(Collection<ServerPlayerEntity> players) {
        MythicLibGeneralSettings current = MythicLibFabricMod.settings();
        boolean enabled = current != null && current.healthScale().enabled();
        for (ServerPlayerEntity player : players) {
            Double override = OVERRIDES.get(player.getUuid());
            if (!enabled && override == null) {
                if (ACTIVE.remove(player.getUuid())) {
                    sendRealMaxHealth(player);
                    player.markHealthDirty();
                }
            } else {
                ACTIVE.add(player.getUuid());
                sendScaledMaxHealth(player, override != null ? override : current.healthScale().scale());
                player.markHealthDirty();
            }
        }
    }

    public static boolean active(UUID playerId) {
        return playerId != null && ACTIVE.contains(playerId);
    }

    /** Per-player equivalent of Bukkit Player#setHealthScale/setHealthScaled(true). */
    public static void setScale(ServerPlayerEntity player, double scale) {
        if (player == null) throw new IllegalArgumentException("player");
        if (!Double.isFinite(scale) || scale <= 0.0d) throw new IllegalArgumentException("scale must be finite and > 0");
        OVERRIDES.put(player.getUuid(), scale);
        ACTIVE.add(player.getUuid());
        sendScaledMaxHealth(player, scale);
        player.markHealthDirty();
    }

    /** Clears the explicit player scale and falls back to global config behavior. */
    public static void resetScale(ServerPlayerEntity player) {
        if (player == null) return;
        OVERRIDES.remove(player.getUuid());
        MythicLibGeneralSettings settings = MythicLibFabricMod.settings();
        if (settings != null && settings.healthScale().enabled()) {
            ACTIVE.add(player.getUuid());
            sendScaledMaxHealth(player, settings.healthScale().scale());
        } else {
            ACTIVE.remove(player.getUuid());
            sendRealMaxHealth(player);
        }
        player.markHealthDirty();
    }

    public static double currentScale(ServerPlayerEntity player) {
        if (player == null) return 0.0d;
        Double override = OVERRIDES.get(player.getUuid());
        if (override != null) return override;
        MythicLibGeneralSettings settings = MythicLibFabricMod.settings();
        return settings != null && settings.healthScale().enabled() ? settings.healthScale().scale() : player.getMaxHealth();
    }

    public static Packet<?> transform(ServerPlayerEntity player, Packet<?> packet) {
        if (player == null || packet == null || !active(player.getUuid())) return packet;
        MythicLibGeneralSettings settings = MythicLibFabricMod.settings();
        Double override = OVERRIDES.get(player.getUuid());
        if (override == null && (settings == null || !settings.healthScale().enabled())) return packet;
        double scale = Math.max(1.0d, override != null ? override : settings.healthScale().scale());

        if (packet instanceof HealthUpdateS2CPacket health) {
            float realMax = player.getMaxHealth();
            float scaled = realMax <= 0.0f ? health.getHealth()
                    : (float) (health.getHealth() / realMax * scale);
            scaled = (float) Math.max(0.0d, Math.min(scale, scaled));
            return new HealthUpdateS2CPacket(scaled, health.getFood(), health.getSaturation());
        }

        if (packet instanceof EntityAttributesS2CPacket attributes
                && attributes.getEntityId() == player.getId()) {
            return scaledAttributePacket(attributes, scale);
        }
        return packet;
    }

    private static EntityAttributesS2CPacket scaledAttributePacket(EntityAttributesS2CPacket original, double scale) {
        List<EntityAttributeInstance> instances = new ArrayList<>(original.getEntries().size());
        for (EntityAttributesS2CPacket.Entry entry : original.getEntries()) {
            EntityAttributeInstance instance = new EntityAttributeInstance(entry.attribute(), ignored -> {});
            if (entry.attribute().equals(EntityAttributes.GENERIC_MAX_HEALTH)) {
                instance.setBaseValue(scale);
            } else {
                instance.setBaseValue(entry.base());
                entry.modifiers().forEach(instance::addTemporaryModifier);
            }
            instances.add(instance);
        }
        return new EntityAttributesS2CPacket(original.getEntityId(), instances);
    }

    private static void sendScaledMaxHealth(ServerPlayerEntity player, double scale) {
        EntityAttributeInstance synthetic = new EntityAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH, ignored -> {});
        synthetic.setBaseValue(Math.max(1.0d, scale));
        player.networkHandler.sendPacket(new EntityAttributesS2CPacket(player.getId(), List.of(synthetic)));
    }

    private static void sendRealMaxHealth(ServerPlayerEntity player) {
        EntityAttributeInstance real = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (real != null) player.networkHandler.sendPacket(new EntityAttributesS2CPacket(player.getId(), List.of(real)));
    }
}
