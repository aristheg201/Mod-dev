package io.github.aristheg201.cobblemonworld;

import io.github.aristheg201.cobblemonworld.cobblemon.LevelCapHooks;
import io.github.aristheg201.cobblemonworld.command.CWorldCommands;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.item.ModItems;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementStore;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.ContentRegistry;
import io.github.aristheg201.cobblemonworld.faction.IslandWarService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonWorldMod implements ModInitializer {
    public static final String MOD_ID = "cobblemonworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobblemonWorld");

    @Override
    public void onInitialize() {
        CWorldConfig.load();
        ModItems.register();
        CWorldNetworking.register();
        CWorldCommands.register();
        LevelCapHooks.register();
        ContentRegistry.INSTANCE.loadBuiltIns();
        IslandWarService.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProgressionStore.INSTANCE.load(server);
            NpcPlacementStore.INSTANCE.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ProgressionStore.INSTANCE.save(server);
            NpcPlacementStore.INSTANCE.save(server);
        });

        LOGGER.info("Cobblemon World initialized. Default level cap: {}", CWorldConfig.INSTANCE.defaultLevelCap);
    }
}
