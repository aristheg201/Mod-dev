package io.github.aristheg201.cobblemonworld;

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration;
import io.github.aristheg201.cobblemonworld.boss.ModBossEntities;
import io.github.aristheg201.cobblemonworld.boss.TobaEncounterService;
import io.github.aristheg201.cobblemonworld.cobblemon.LevelCapHooks;
import io.github.aristheg201.cobblemonworld.command.CWorldCommands;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.faction.IslandWarService;
import io.github.aristheg201.cobblemonworld.faction.NativeFactionService;
import io.github.aristheg201.cobblemonworld.item.ModItems;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.npc.CWorldNpcInteraction;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementStore;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementRecoveryService;
import io.github.aristheg201.cobblemonworld.npc.TrainerBattleService;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.ContentRegistry;
import io.github.aristheg201.cobblemonworld.story.PhoneBootstrapService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonWorldMod implements ModInitializer {
    public static final String MOD_ID = "cobblemonworld";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobblemonWorld");

    @Override
    public void onInitialize() {
        CWorldConfig.load();
        ModItems.register();
        ModBossEntities.register();
        CWorldNetworking.register();
        TobaEncounterService.register();

        NPCInteractConfiguration.Companion.register(
                CWorldNpcInteraction.TYPE,
                Component.literal("Cobblemon World"),
                CWorldNpcInteraction.class
        );

        ContentRegistry.INSTANCE.loadBuiltIns();
        NpcDefinitionRegistry.INSTANCE.loadBuiltIns();

        CWorldCommands.register();
        PhoneBootstrapService.register();
        LevelCapHooks.register();
        TrainerBattleService.register();
        NpcPlacementRecoveryService.register();
        NativeFactionService.register();
        IslandWarService.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProgressionStore.INSTANCE.load(server);
            NpcPlacementStore.INSTANCE.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ProgressionStore.INSTANCE.save(server);
            NpcPlacementStore.INSTANCE.save(server);
        });

        if (Boolean.getBoolean("cworld.qa.server")) {
            io.github.aristheg201.cobblemonworld.qa.CWorldQaServerHarness.register();
        }

        LOGGER.info("Cobblemon World initialized. Default level cap: {}", CWorldConfig.INSTANCE.defaultLevelCap);
    }
}
