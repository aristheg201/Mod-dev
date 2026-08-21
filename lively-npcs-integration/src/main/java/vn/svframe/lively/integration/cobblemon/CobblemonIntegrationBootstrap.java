package vn.svframe.lively.integration.cobblemon;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.CombatAdapter;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

/** Cobblemon registration split into process-scoped combat and server-session body bindings. */
public final class CobblemonIntegrationBootstrap {
    private static final CombatAdapter COMBAT = new CobblemonCombatBridge();

    private CobblemonIntegrationBootstrap() {}

    public static boolean installIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return false;
        LivelyApi.registerCombatAdapter(COMBAT);
        if (LivelyApi.npcs() != null) registerNpcBodyProvider();
        return true;
    }

    public static boolean installNpcBodyProvider(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon") || LivelyApi.npcs() == null || server == null) return false;
        registerNpcBodyProvider();
        // Core may have already run its SERVER_STARTED restore before Integration receives the event.
        // NpcRuntime.restoreSpawned() is idempotent for bodies that are already alive, so re-running it
        // here restores only bodies whose provider was unavailable during the first pass.
        LivelyApi.npcs().restoreSpawned(server);
        return true;
    }

    private static void registerNpcBodyProvider() {
        LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL,
                definition -> new CobblemonPokemonBody(definition.id()));
    }
}
