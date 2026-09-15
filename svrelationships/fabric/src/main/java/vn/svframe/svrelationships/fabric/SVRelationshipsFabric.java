package vn.svframe.svrelationships.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.svrelationships.fabric.command.SVRelationshipCommands;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.RelationshipRuleService;
import vn.svframe.svrelationships.fabric.gui.GuiDefinitionService;
import vn.svframe.svrelationships.fabric.household.HouseholdRepository;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.integration.FabricIntegrationBootstrap;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.persistence.DaycareRepository;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.persistence.RelationshipRepository;
import vn.svframe.svrelationships.fabric.persistence.RewardClaimRepository;
import vn.svframe.svrelationships.fabric.runtime.RuntimeCoordinator;
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
        var gameplay = new GameplayDefinitionService(root);
        gameplay.initialize();
        var rules = new RelationshipRuleService(root);
        rules.initialize();
        var guiDefinitions = new GuiDefinitionService(root);
        var guiReload = guiDefinitions.reload();
        if (!guiReload.success()) throw new IllegalStateException("Unable to load GUI definitions: " + guiReload.detail());
        var messages = new MessageService(config);

        var householdRepository = new HouseholdRepository(root.resolve("state/households.json"));
        householdRepository.load();
        var households = new HouseholdService(householdRepository, config);

        var relationshipRepository = new RelationshipRepository(root.resolve("state/relationships.json"));
        relationshipRepository.load();
        var daycareRepository = new DaycareRepository(root.resolve("state/daycare.json"));
        daycareRepository.load();
        var lineageRepository = new LineageRepository(root.resolve("state/lineage.json"));
        lineageRepository.load();
        var rewardClaims = new RewardClaimRepository(root.resolve("state/reward_claims.json"));
        rewardClaims.load();

        var integrations = new IntegrationRegistry();
        var providers = new ProviderHub();
        var runtime = new RuntimeCoordinator(
                config, gameplay, rules, guiDefinitions, messages, households, providers,
                relationshipRepository, daycareRepository, lineageRepository, rewardClaims
        );

        SVRelationshipCommands.register(config, gameplay, messages, households, integrations, providers, runtime);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            new FabricIntegrationBootstrap(server, households, integrations, providers).start();
            runtime.start(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> runtime.tick(System.currentTimeMillis()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            runtime.close();
            householdRepository.close();
        });
    }
}
