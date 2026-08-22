package vn.svframe.lively.social;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.economy.GamblingEngine;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.society.SocietyApi;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Relationship consequences without lifestyle animation: homes, debt support, gambling strain and incarceration stress.
 * All monetary support uses the internal authoritative ledger and stable day-scoped references.
 */
public final class HouseholdSimulationService {
    private static final long PULSE_TICKS = 1200L;
    private static final int MAX_BONDS = 96;
    private final MinecraftServer server;
    private long lastPulse;

    public HouseholdSimulationService(MinecraftServer server) { this.server = server; }

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS || LivelyApi.npcs() == null || LivelyApi.states() == null) return;
        lastPulse = tick;
        long day = Math.floorDiv(server.getOverworld().getTimeOfDay(), 24_000L);
        List<RomanceEngine.Bond> bonds = LivelyApi.romance().snapshot().values().stream()
                .filter(this::householdStage)
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_BONDS).toList();
        for (RomanceEngine.Bond bond : bonds) process(bond, day);
    }

    private void process(RomanceEngine.Bond bond, long day) {
        if (bond.a().kind() != ActorId.Kind.NPC || bond.b().kind() != ActorId.Kind.NPC) return;
        NpcDefinition aDef = LivelyApi.npcs().get(bond.a().uuid()).orElse(null);
        NpcDefinition bDef = LivelyApi.npcs().get(bond.b().uuid()).orElse(null);
        NpcState aState = LivelyApi.states().get(bond.a().uuid()).orElse(null);
        NpcState bState = LivelyApi.states().get(bond.b().uuid()).orElse(null);
        if (aDef == null || bDef == null || aState == null || bState == null) return;

        synchronizeHouseholdHome(aDef, bDef, aState, bState);
        boolean aCustody = SocietyApi.law().activeCustody(bond.a()).isPresent();
        boolean bCustody = SocietyApi.law().activeCustody(bond.b()).isPresent();
        if (aCustody || bCustody) applyCustodyStrain(bond, aState, bState, aCustody, bCustody, day);

        applyGamblingStrain(bond, aState, bState, SocietyApi.gambling().habit(bond.a()), SocietyApi.gambling().habit(bond.b()), day);
        supportDebt(bond, aState, bState, day);
        supportDebt(reverse(bond), bState, aState, day);
    }

    private void synchronizeHouseholdHome(NpcDefinition a, NpcDefinition b, NpcState aState, NpcState bState) {
        String aHome = a.metadata().get("home.structure");
        String bHome = b.metadata().get("home.structure");
        if ((aHome == null || aHome.isBlank()) && bHome != null && !bHome.isBlank()) {
            LivelyApi.npcs().setMetadata(a.id(), "home.structure", bHome);
            LivelyApi.npcs().setMetadata(a.id(), "household.partner", b.id().toString());
            aState.remember("joined_partner_household", Map.of("partner", b.id().toString(), "home", bHome), .44D, 1D);
            bState.remember("partner_joined_household", Map.of("partner", a.id().toString(), "home", bHome), .34D, 1D);
        } else if ((bHome == null || bHome.isBlank()) && aHome != null && !aHome.isBlank()) {
            LivelyApi.npcs().setMetadata(b.id(), "home.structure", aHome);
            LivelyApi.npcs().setMetadata(b.id(), "household.partner", a.id().toString());
            bState.remember("joined_partner_household", Map.of("partner", a.id().toString(), "home", aHome), .44D, 1D);
            aState.remember("partner_joined_household", Map.of("partner", b.id().toString(), "home", aHome), .34D, 1D);
        }
    }

    private void applyCustodyStrain(RomanceEngine.Bond bond, NpcState a, NpcState b,
                                    boolean aCustody, boolean bCustody, long day) {
        ActorId detained = aCustody ? bond.a() : bond.b();
        ActorId partner = aCustody ? bond.b() : bond.a();
        LivelyApi.social().apply(detained, partner, new SocialEngine.SocialDelta(
                -.004D, -.006D, -.002D, .004D, -.004D, 0D, .002D,
                "partner_in_custody", Map.of("day", Long.toString(day))));
        NpcState free = aCustody ? b : a;
        free.setNeed("stress", Math.min(1D, free.snapshot(1).need("stress") + .05D));
        free.remember("partner_in_custody", Map.of("partner", detained.uuid().toString()), .52D, 1D);
    }

    private void applyGamblingStrain(RomanceEngine.Bond bond, NpcState a, NpcState b,
                                     GamblingEngine.Habit aHabit, GamblingEngine.Habit bHabit, long day) {
        double compulsion = Math.max(aHabit.compulsion(), bHabit.compulsion());
        if (compulsion < .55D) return;
        double severity = Math.min(.08D, (compulsion - .50D) * .12D);
        LivelyApi.social().apply(bond.a(), bond.b(), new SocialEngine.SocialDelta(
                -severity * .65D, -severity, 0D, severity * .30D, -severity * .55D, 0D, .004D,
                "household_gambling_strain", Map.of("day", Long.toString(day), "compulsion", Double.toString(compulsion))));
        LivelyApi.romance().applyJealousy(bond.a(), bond.b(), severity * .25D, "financial_instability");
        a.setNeed("financial_stress", Math.min(1D, a.snapshot(1).need("financial_stress") + severity));
        b.setNeed("financial_stress", Math.min(1D, b.snapshot(1).need("financial_stress") + severity));
    }

    /** In this orientation bond.a is debtor and bond.b is potential supporter. */
    private void supportDebt(RomanceEngine.Bond bond, NpcState debtorState, NpcState partnerState, long day) {
        DebtEngine.Contract debt = SocietyApi.debts().forDebtor(bond.a()).stream()
                .filter(value -> value.status() == DebtEngine.Status.DELINQUENT || value.status() == DebtEngine.Status.COLLECTION)
                .sorted(Comparator.comparingLong(DebtEngine.Contract::outstanding).reversed()).findFirst().orElse(null);
        if (debt == null) return;
        SocialEngine.Relationship relation = LivelyApi.social().relationship(bond.a(), bond.b());
        if (bond.stability() < .58D || relation.trust() < .35D || relation.loyalty() < .25D) {
            partnerState.setNeed("stress", Math.min(1D, partnerState.snapshot(1).need("stress") + .025D));
            return;
        }

        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        long partnerBalance = wallet(economy, bond.b());
        if (partnerBalance <= 20L) return;
        long help = Math.min(debt.outstanding(), Math.max(1L, partnerBalance / 10L));
        String reference = "household-support:" + debt.id() + ":" + day + ":" + bond.b().uuid();
        if (LivelyApi.economy().transferOnce(EconomyEngine.TransactionType.GIFT, bond.b(), bond.a(), help, reference).isEmpty()) return;
        debtorState.remember("partner_financial_support", Map.of("partner", bond.b().uuid().toString(), "amount", Long.toString(help)), .50D, 1D);
        partnerState.remember("supported_partner_debt", Map.of("partner", bond.a().uuid().toString(), "amount", Long.toString(help)), .42D, 1D);
        LivelyApi.social().apply(bond.a(), bond.b(), new SocialEngine.SocialDelta(.012D, .018D, .008D, 0D, .015D, 0D, .006D,
                "household_financial_support", Map.of("debt", debt.id().toString(), "amount", Long.toString(help))));
    }

    private static RomanceEngine.Bond reverse(RomanceEngine.Bond bond) {
        return new RomanceEngine.Bond(bond.id(), bond.b(), bond.a(), bond.stage(), bond.stability(), bond.jealousy(),
                bond.since(), bond.updatedAt(), bond.sharedMemories(), bond.facts());
    }

    private boolean householdStage(RomanceEngine.Bond bond) {
        return bond.stage() == RomanceEngine.Stage.PARTNERED || bond.stage() == RomanceEngine.Stage.ENGAGED
                || bond.stage() == RomanceEngine.Stage.MARRIED;
    }

    private static long wallet(EconomyEngine.Snapshot snapshot, ActorId actor) {
        EconomyEngine.Wallet wallet = snapshot.wallets().get(actor);
        return wallet == null ? 0L : wallet.balance();
    }
}
