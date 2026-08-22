package vn.svframe.lively.integration.cobblemon;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.CombatAdapter;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

/** Cobblemon registration split into process-scoped combat and server-session body bindings. */
public final class CobblemonIntegrationBootstrap {
    private static final CombatAdapter COMBAT = new CobblemonCombatBridge();

    private CobblemonIntegrationBootstrap() {}

    /**
     * Installs the mandatory Cobblemon side of the extension.
     * Fabric metadata already requires Cobblemon 1.7.x, so silently degrading here would hide a broken deployment.
     */
    public static void install() {
        LivelyApi.registerCombatAdapter(COMBAT);
        if (LivelyApi.npcs() != null) registerNpcBodyProvider();
    }

    public static boolean installNpcBodyProvider(MinecraftServer server) {
        if (LivelyApi.npcs() == null || server == null) return false;
        registerNpcBodyProvider();
        LivelyApi.npcs().restoreSpawned(server);
        return true;
    }

    private static void registerNpcBodyProvider() {
        LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL, definition -> {
            String key = definition.bodyKey() == null ? "" : definition.bodyKey().trim();
            return key.startsWith("npc:")
                    ? new CobblemonTrainerBody(definition.id())
                    : new CobblemonPokemonBody(definition.id());
        });
    }
}
