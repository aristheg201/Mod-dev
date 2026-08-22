package vn.svframe.lively.society;

import vn.svframe.lively.api.GamblingBridge;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.EconomyRouter;
import vn.svframe.lively.economy.GamblingEngine;
import vn.svframe.lively.economy.PlayerCommerceService;
import vn.svframe.lively.law.LawEnforcementEngine;
import vn.svframe.lively.law.LawEnforcementService;
import vn.svframe.lively.simulation.SocietySimulationService;
import vn.svframe.lively.social.HouseholdSimulationService;
import vn.svframe.lively.social.SocialEncounterService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Public society/economy/law surface. Provider registrations survive world-session resets. */
public final class SocietyApi {
    private static final EconomyRouter ECONOMIES = new EconomyRouter();
    private static final DebtEngine DEBTS = new DebtEngine();
    private static final GamblingEngine GAMBLING = new GamblingEngine();
    private static final LawEnforcementEngine LAW = new LawEnforcementEngine();
    private static final CopyOnWriteArrayList<GamblingBridge> GAMBLING_BRIDGES = new CopyOnWriteArrayList<>();
    private static volatile PlayerCommerceService commerce;
    private static volatile SocietySimulationService simulation;
    private static volatile LawEnforcementService lawService;
    private static volatile SocialEncounterService socialEncounters;
    private static volatile HouseholdSimulationService households;

    private SocietyApi() {}

    public static EconomyRouter economies() { return ECONOMIES; }
    public static DebtEngine debts() { return DEBTS; }
    public static GamblingEngine gambling() { return GAMBLING; }
    public static LawEnforcementEngine law() { return LAW; }
    public static PlayerCommerceService commerce() { return commerce; }
    public static SocietySimulationService simulation() { return simulation; }
    public static LawEnforcementService lawService() { return lawService; }
    public static SocialEncounterService socialEncounters() { return socialEncounters; }
    public static HouseholdSimulationService households() { return households; }
    public static void installCommerce(PlayerCommerceService service) { commerce = service; }
    public static void installSimulation(SocietySimulationService service) { simulation = service; }
    public static void installLawService(LawEnforcementService service) { lawService = service; }
    public static void installSocialEncounters(SocialEncounterService service) { socialEncounters = service; }
    public static void installHouseholds(HouseholdSimulationService service) { households = service; }

    public static void registerGamblingBridge(GamblingBridge bridge) {
        if (bridge != null && GAMBLING_BRIDGES.stream().noneMatch(existing -> existing.id().equalsIgnoreCase(bridge.id()))) {
            GAMBLING_BRIDGES.add(bridge);
        }
    }

    public static List<GamblingBridge> gamblingBridges() { return List.copyOf(GAMBLING_BRIDGES); }

    public static void resetWorldState() {
        DEBTS.restore(new DebtEngine.Snapshot(0L, Map.of()));
        GAMBLING.restore(new GamblingEngine.Snapshot(0L, Map.of(), Map.of()));
        LAW.restore(new LawEnforcementEngine.Snapshot(0L, Map.of(), Map.of(), Map.of(), Map.of()));
        commerce = null;
        simulation = null;
        lawService = null;
        socialEncounters = null;
        households = null;
    }
}
