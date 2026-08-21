package vn.svframe.fabricmmo.smoke;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class ServerSmokeMod implements ModInitializer {
    private static final List<String> REQUIRED = List.of(
            "mythiclibfabric",
            "mmocorefabric",
            "mmoitemsfabric",
            "mythicmobsfabric");

    @Override
    public void onInitialize() {
        for (String id : REQUIRED) {
            if (!FabricLoader.getInstance().isModLoaded(id)) {
                throw new IllegalStateException("Missing Fabric MMO mod: " + id);
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("FABRIC_MMO_DEDICATED_SERVER_BOOT=PASS");
            server.stop(false);
        });
    }
}
