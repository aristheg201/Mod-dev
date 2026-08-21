package vn.svframe.lively.integration.cobblemon;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.lively.api.LivelyApi;

public final class CobblemonIntegrationBootstrap {
    private CobblemonIntegrationBootstrap() {}

    public static boolean installIfPresent() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return false;
        LivelyApi.registerCombatAdapter(new CobblemonCombatBridge());
        return true;
    }
}
