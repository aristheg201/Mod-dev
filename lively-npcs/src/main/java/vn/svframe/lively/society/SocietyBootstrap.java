package vn.svframe.lively.society;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import vn.svframe.lively.LivelyNpcs;
import vn.svframe.lively.economy.EconomyRoutingConfig;
import vn.svframe.lively.economy.PlayerCommerceService;
import vn.svframe.lively.law.LawConfig;
import vn.svframe.lively.law.LawEnforcementService;
import vn.svframe.lively.persistence.SocietyStateStore;
import vn.svframe.lively.simulation.SocietySimulationService;
import vn.svframe.lively.social.HouseholdSimulationService;
import vn.svframe.lively.social.SocialEncounterService;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** World-session bootstrap for commerce, debt, gambling, justice and causal society behaviour. */
public final class SocietyBootstrap implements ModInitializer {
    private volatile MinecraftServer activeServer;
    private volatile SocietyStateStore store;
    private volatile SocietySimulationService simulation;
    private volatile LawEnforcementService lawService;
    private volatile SocialEncounterService socialEncounters;
    private volatile HouseholdSimulationService households;
    private volatile CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private synchronized void start(MinecraftServer server) {
        if (activeServer == server) return;
        activeServer = server;
        SocietyApi.resetWorldState();
        SocietyApi.economies().clearRoutes();
        Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs");
        EconomyRoutingConfig.load(configRoot.resolve("economy.properties")).forEach(SocietyApi.economies()::route);
        LawConfig lawConfig = LawConfig.load(configRoot.resolve("law.properties"));

        Path stateFile = server.getSavePath(WorldSavePath.ROOT).resolve("livelynpcs").resolve("state").resolve("society.json");
        store = new SocietyStateStore(stateFile);
        store.load().ifPresent(bundle -> {
            SocietyApi.debts().restore(bundle.debts());
            SocietyApi.gambling().restore(bundle.gambling());
            SocietyApi.law().restore(bundle.law());
        });
        SocietyApi.installCommerce(new PlayerCommerceService(server, SocietyApi.economies()));
        simulation = new SocietySimulationService(server);
        SocietyApi.installSimulation(simulation);
        lawService = new LawEnforcementService(server, lawConfig, SocietyApi.law());
        SocietyApi.installLawService(lawService);
        socialEncounters = new SocialEncounterService(server);
        SocietyApi.installSocialEncounters(socialEncounters);
        households = new HouseholdSimulationService(server);
        SocietyApi.installHouseholds(households);
        pendingSave = CompletableFuture.completedFuture(null);
        LivelyNpcs.LOGGER.info("Lively society session ready: economy routes={}, debtContracts={}, gamblingBets={}, warrants={}, custody={}, courtCases={}",
                SocietyApi.economies().routes(), SocietyApi.debts().snapshot().contracts().size(), SocietyApi.gambling().snapshot().bets().size(),
                SocietyApi.law().snapshot().warrants().size(), SocietyApi.law().snapshot().custody().size(), SocietyApi.law().snapshot().courtCases().size());
    }

    private void tick(MinecraftServer server) {
        if (activeServer != server || simulation == null) return;
        long tick = server.getTicks();
        simulation.tick(tick);
        LawEnforcementService law = lawService;
        if (law != null) law.tick(tick);
        SocialEncounterService encounters = socialEncounters;
        if (encounters != null) encounters.tick(tick);
        HouseholdSimulationService household = households;
        if (household != null) household.tick(tick);
        if (tick % 6000L != 0L || !pendingSave.isDone() || store == null) return;
        pendingSave = store.saveAsync(capture()).whenComplete((ignored, error) -> {
            if (error != null) LivelyNpcs.LOGGER.error("Lively society autosave failed", error);
        });
    }

    private synchronized void stop(MinecraftServer server) {
        if (activeServer != server) return;
        try {
            if (store != null) CompletableFuture.allOf(pendingSave, store.saveAsync(capture())).orTimeout(10L, TimeUnit.SECONDS).join();
        } catch (RuntimeException error) {
            LivelyNpcs.LOGGER.error("Lively society final save failed", error);
        } finally {
            if (store != null) store.close();
            store = null;
            simulation = null;
            lawService = null;
            socialEncounters = null;
            households = null;
            pendingSave = CompletableFuture.completedFuture(null);
            activeServer = null;
            SocietyApi.resetWorldState();
        }
    }

    private static SocietyStateStore.Bundle capture() {
        return new SocietyStateStore.Bundle(SocietyApi.debts().snapshot(), SocietyApi.gambling().snapshot(), SocietyApi.law().snapshot());
    }
}
