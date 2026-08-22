package vn.svframe.lively.integration;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.society.SocietyApi;

/** Installs optional server ecosystem adapters without leaking them into Lively Core. */
public final class ServerEcosystemBootstrap {
    private ServerEcosystemBootstrap() {}

    public static void install() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("luckperms")) LivelyApi.installPermissionBridge(LuckPermsBridge.create());
        if (loader.isModLoaded("beconomy")) LivelyApi.installEconomyBridge(BEconomyBridge.create());
        if (loader.isModLoaded("holodisplays")) LivelyApi.installHologramBridge(HoloDisplaysBridge.create());
        if (loader.isModLoaded("flan")) LivelyApi.installClaimBridge(FlanClaimBridge.create());
        if (loader.isModLoaded("svfwaypoints")) LivelyApi.installWaypointBridge(SvfWaypointsBridge.create());
        if (loader.isModLoaded("svf_all_in_one")) SocietyApi.registerGamblingBridge(SvfAllInOneGamblingBridge.create());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (loader.isModLoaded("beconomy")) SocietyApi.economies().register(BEconomyProvider.create());
            if (loader.isModLoaded("cobbledollars")) SocietyApi.economies().register(CobbleDollarsEconomyProvider.create(server));
            if (loader.isModLoaded("impactor")) SocietyApi.economies().register(ImpactorEconomyProvider.create());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (loader.isModLoaded("beconomy")) SocietyApi.economies().unregister("beconomy");
            if (loader.isModLoaded("cobbledollars")) SocietyApi.economies().unregister("cobbledollars");
            if (loader.isModLoaded("impactor")) SocietyApi.economies().unregister("impactor");
        });
    }
}
