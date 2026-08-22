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
        // Core may have already run its SERVER_STARTED restore before Integration receives the event.
        // NpcRuntime.restoreSpawned() is idempotent for bodies that are already alive, so re-running it
        // here restores only Cobblemon bodies whose provider was not bound during the first pass.
        LivelyApi.npcs().restoreSpawned(server);
        return true;
    }

    private static void registerNpcBodyProvider() {
        LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL,
                definition -> new CobblemonPokemonBody(definition.id()));
    }
}
