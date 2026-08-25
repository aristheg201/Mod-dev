package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.StatProviderRegistry;

/** Connects the native MythicLib stat runtime to the shared Fabric stat provider surface. */
public final class MythicLibStatMod implements ModInitializer {
    private static final NativeStatEngine ENGINE = new NativeStatEngine();
    private static volatile AutoCloseable providerRegistration;

    @Override
    public void onInitialize() {
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

    private static void registerProvider() {
        if (providerRegistration != null) return;
        synchronized (MythicLibStatMod.class) {
            if (providerRegistration == null) providerRegistration = StatProviderRegistry.register(ENGINE::stat);
        }
    }
}
