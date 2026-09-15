package vn.svframe.svrelationships.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svrelationships.fabric.command.SVRelationshipCommands;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.household.HouseholdRepository;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.integration.FabricIntegrationBootstrap;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.integration.IntegrationRegistry;
import vn.svframe.svrelationships.integration.ProviderHub;

public final class SVRelationshipsFabric implements ModInitializer {
    public static final String MOD_ID = "svrelationships";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        var root = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        var config = new ConfigService(root);
        config.initialize();
        var messages = new MessageService(config);

        var householdRepository = new HouseholdRepository(root.resolve("state/households.json"));
        householdRepository.load();
        var households = new HouseholdService(householdRepository, config);

        var integrations = new IntegrationRegistry();
        var providers = new ProviderHub();

        SVRelationshipCommands.register(config, messages, households, integrations, providers);

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                new FabricIntegrationBootstrap(server, households, integrations, providers).start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> householdRepository.close());
    }
}
