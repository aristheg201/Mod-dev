package vn.svframe.svrelationships.fabric.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.integration.IntegrationDescriptor;
import vn.svframe.svrelationships.integration.IntegrationRegistry;
import vn.svframe.svrelationships.integration.IntegrationState;
import vn.svframe.svrelationships.integration.ProviderHub;

public final class FabricIntegrationBootstrap {
    private final MinecraftServer server;
    private final HouseholdService households;
    private final RelationshipService relationships;
    private final IntegrationRegistry registry;
    private final ProviderHub providers;

    public FabricIntegrationBootstrap(MinecraftServer server, HouseholdService households, RelationshipService relationships, IntegrationRegistry registry, ProviderHub providers) {
        this.server = server; this.households = households; this.relationships = relationships; this.registry = registry; this.providers = providers;
    }

    public void start() { bindCobblemon(); bindLuckPerms(); bindBEconomy(); bindCobbleDollars(); bindPlaceholders(); }
    private void bindCobblemon() { bind("cobblemon", "pokemon", "cobblemon", () -> providers.setPokemonProvider(new CobblemonPokemonProvider(server))); }
    private void bindLuckPerms() { bind("luckperms", "permission", "luckperms", () -> providers.setPermissionProvider(new LuckPermsPermissionProvider())); }
    private void bindBEconomy() { bind("beconomy", "economy", "beconomy", () -> providers.registerEconomy(new BEconomyProvider())); }
    private void bindCobbleDollars() { bind("cobbledollars", "economy", "cobbledollars", () -> providers.registerEconomy(new CobbleDollarsProvider(server))); }
    private void bindPlaceholders() { bind("placeholder_api", "placeholder", "placeholder-api", () -> TextPlaceholderBridge.register(households, relationships)); }

    private void bind(String id, String kind, String modId, Runnable action) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) { registry.put(new IntegrationDescriptor(id, kind, modId, IntegrationState.MISSING, "integration.state.missing")); return; }
        try { action.run(); registry.put(new IntegrationDescriptor(id, kind, modId, IntegrationState.ACTIVE, "integration.state.active")); }
        catch (Throwable throwable) { registry.put(new IntegrationDescriptor(id, kind, modId, IntegrationState.ERROR, "integration.state.error")); }
    }
}
