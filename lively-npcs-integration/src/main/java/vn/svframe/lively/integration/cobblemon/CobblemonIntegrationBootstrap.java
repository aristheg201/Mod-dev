package vn.svframe.lively.integration.cobblemon;

import net.fabricmc.loader.api.FabricLoader;
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
        installNpcBodyProvider();
        return true;
    }

    public static boolean installNpcBodyProvider() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon") || LivelyApi.npcs() == null) return false;
        LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL,
                definition -> new CobblemonPokemonBody(definition.id()));
        return true;
    }
}
