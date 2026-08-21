package vn.svframe.lively.integration.cobblemon;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

public final class CobblemonIntegrationBootstrap {
    private CobblemonIntegrationBootstrap() {}

    public static boolean installIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return false;
        LivelyApi.registerCombatAdapter(new CobblemonCombatBridge());
        if (LivelyApi.npcs() != null) {
            LivelyApi.npcs().registerProvider(NpcDefinition.BodyType.EXTERNAL,
                    definition -> new CobblemonPokemonBody(definition.id()));
        }
        return true;
    }
}
