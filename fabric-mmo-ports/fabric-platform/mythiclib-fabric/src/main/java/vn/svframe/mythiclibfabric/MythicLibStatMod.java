package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.StatProviderRegistry;

/** Connects the native MythicLib stat runtime to Fabric player lifecycle and Minecraft attributes. */
public final class MythicLibStatMod implements ModInitializer {
    private static final NativeStatEngine ENGINE = new NativeStatEngine();
    private static volatile AutoCloseable providerRegistration;

    @Override
    public void onInitialize() {
        registerDefaultHandlers();
        registerProvider();
        ServerTickEvents.END_SERVER_TICK.register(server -> ENGINE.tick(MythicLibFabricMod.currentTick()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ENGINE.onSessionOpen(handler.player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ENGINE.onSessionClose(handler.player.getUuid());
            ENGINE.clear(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ENGINE.clear());
    }

    public static NativeStatEngine engine() {
        return ENGINE;
    }

    private static void registerDefaultHandlers() {
        register("ARMOR", EntityAttributes.GENERIC_ARMOR, 0.0d);
        register("ARMOR_TOUGHNESS", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.0d);
        register("ATTACK_DAMAGE", EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0d);
        register("ATTACK_SPEED", EntityAttributes.GENERIC_ATTACK_SPEED, 4.0d);
        register("KNOCKBACK_RESISTANCE", EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.0d);
        register("LUCK", EntityAttributes.GENERIC_LUCK, 0.0d);
        register("MAX_HEALTH", EntityAttributes.GENERIC_MAX_HEALTH, 20.0d);
        ENGINE.registerHandler(new FabricMovementSpeedStatHandler(ENGINE));
        register("MAX_ABSORPTION", EntityAttributes.GENERIC_MAX_ABSORPTION, 0.0d);

        register("BLOCK_BREAK_SPEED", EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, 1.0d);
        register("BLOCK_INTERACTION_RANGE", EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, 4.5d);
        register("ENTITY_INTERACTION_RANGE", EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, 3.0d);
        register("FALL_DAMAGE_MULTIPLIER", EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 1.0d);
        register("GRAVITY", EntityAttributes.GENERIC_GRAVITY, 0.08d);
        register("JUMP_STRENGTH", EntityAttributes.GENERIC_JUMP_STRENGTH, 0.42d);
        register("SAFE_FALL_DISTANCE", EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0d);
        register("SCALE", EntityAttributes.GENERIC_SCALE, 1.0d);
        register("STEP_HEIGHT", EntityAttributes.GENERIC_STEP_HEIGHT, 0.6d);

        register("BURNING_TIME", EntityAttributes.GENERIC_BURNING_TIME, 1.0d);
        register("EXPLOSION_KNOCKBACK_RESISTANCE", EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE, 0.0d);
        register("MINING_EFFICIENCY", EntityAttributes.PLAYER_MINING_EFFICIENCY, 0.0d);
        register("MOVEMENT_EFFICIENCY", EntityAttributes.GENERIC_MOVEMENT_EFFICIENCY, 0.0d);
        register("OXYGEN_BONUS", EntityAttributes.GENERIC_OXYGEN_BONUS, 0.0d);
        register("SNEAKING_SPEED", EntityAttributes.PLAYER_SNEAKING_SPEED, 0.3d);
        register("SUBMERGED_MINING_SPEED", EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, 0.2d);
        register("SWEEPING_DAMAGE_RATIO", EntityAttributes.PLAYER_SWEEPING_DAMAGE_RATIO, 0.0d);
        register("WATER_MOVEMENT_EFFICIENCY", EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY, 0.0d);
    }

    private static void register(String stat,
                                 net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                                 double playerDefaultBase) {
        ENGINE.registerHandler(new FabricAttributeStatHandler(stat, attribute, playerDefaultBase));
    }

    private static void registerProvider() {
        if (providerRegistration != null) return;
        synchronized (MythicLibStatMod.class) {
            if (providerRegistration == null) providerRegistration = StatProviderRegistry.register(ENGINE::stat);
        }
    }
}
