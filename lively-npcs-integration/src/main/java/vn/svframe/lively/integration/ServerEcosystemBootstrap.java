package vn.svframe.lively.integration;

import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.lively.api.LivelyApi;

/** Installs optional server ecosystem adapters without leaking them into Lively Core. */
public final class ServerEcosystemBootstrap {
    private ServerEcosystemBootstrap() {}

    public static void install() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("luckperms")) LivelyApi.installPermissionBridge(LuckPermsBridge.create());
        if (loader.isModLoaded("beconomy")) LivelyApi.installEconomyBridge(BEconomyBridge.create());
        if (loader.isModLoaded("holodisplays")) LivelyApi.installHologramBridge(HoloDisplaysBridge.create());
        if (loader.isModLoaded("flan")) LivelyApi.installClaimBridge(FlanClaimBridge.create());
    }
}
