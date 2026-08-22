package vn.svframe.lively.simulation;

import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.economy.GamblingEngine;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.society.SocietyApi;
import vn.svframe.lively.social.RomanceEngine;
import vn.svframe.lively.social.SocialEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded society pulse tying routine, leisure, romance, gambling, debt, crime and police response together.
 * Physical world mutation is deliberately absent; navigation uses Lively's validated path service.
 */
public final class SocietySimulationService {
    public enum Activity { WORK, HOME, SLEEP, EAT, DRINK, GAMBLE, AFFECTION, PATROL, DEBT_COLLECTION, CRIME, IDLE }

    private static final long PULSE_TICKS = 200L;
    private static final int MAX_NPCS_PER_PULSE = 96;
    private static final long LEISURE_COOLDOWN = 1200L;
    private static final long CRIME_COOLDOWN = 6000L;
    private static final long COLLECTION_COOLDOWN = 1200L;

    private final MinecraftServer server;
    private final ConcurrentHashMap<UUID, Activity> activities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastLeisure = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastCrime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastCollection = new ConcurrentHashMap<>();
    private long lastPulse;
    private int cursor;

    public SocietySimulationService(MinecraftServer server) { this.server = server; }
    public Map<UUID, Activity> activities() { return Map.copyOf(activities); }

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS || LivelyApi.npcs() == null || LivelyApi.states() == null) return;
        lastPulse = tick;
        Instant now = Instant.now();
        SocietyApi.debts().accrue(now, Duration.ofMinutes(20));

        Map<UUID, NpcDefinition> definitions = LivelyApi.npcs().snapshot();
        if (definitions.isEmpty()) return;
        List<UUID> ids = definitions.keySet().stream().sorted().toList();
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        Map<String, List<EconomyEngine.Business>> byKind = indexBusinesses(economy);
        Map<ActorId, RomanceEngine.Bond> partners = indexPartners();
        List<CrimeEngine.Crime> activeCrimes = LivelyApi.crime().snapshot().crimes().values().stream()
                .filter(crime -> crime.status() == CrimeEngine.Status.OPEN || crime.status() == CrimeEngine.Status.INVESTIGATING)
                .sorted(Comparator.comparing(CrimeEngine.Crime::occurredAt).reversed()).limit(64).toList();

        int count = Math.min(MAX_NPCS_PER_PULSE, ids.size());
        for (int n = 0; n < count; n++) {
            UUID id = ids.get((cursor + n) % ids.size());
            NpcDefinition definition = definitions.get(id);
            NpcState state = LivelyApi.states().get(id).orElse(null);
            if (definition != null && state != null) process(tick, definition, state, economy, byKind, partners, activeCrimes);
        }
        cursor = (cursor + count) % ids.size();
    }

    private void process(long tick, NpcDefinition definition, NpcState state, EconomyEngine.Snapshot economy,
                         Map<String, List<EconomyEngine.Business>> byKind, Map<ActorId, RomanceEngine.Bond> partners,
                         List<CrimeEngine.Crime> activeCrimes) {
        ActorId actor = new ActorId(definition.id(), ActorId.Kind.NPC);
        NpcSnapshot snapshot = state.snapshot(8);
        long dayTime = Math.floorMod(server.getOverworld().getTimeOfDay(), 24_000L);
        boolean police = bool(definition, "society.police") || role(definition, "police", "officer", "cảnh sát", "sheriff");
        boolean collector = bool(definition, "society.debt_collector") || role(definition, "collector", "đòi nợ", "loan shark", "cho vay");

        if (police) {
            patrol(definition, state, activeCrimes);
            setActivity(state, definition.id(), Activity.PATROL, Map.of());
            return;
        }
        if (collector && collectDebt(tick, definition, state, actor)) return;
        if (payOwnDebt(definition, state, actor, economy)) return;

        double hunger = snapshot.need("hunger");
        if ((hunger > .72D || (dayTime >= 5_500L && dayTime <= 6_500L)) && eat(tick, definition, state, actor, economy, byKind)) return;

        if (dayTime >= 18_000L || dayTime < 1_000L) {
            go(definition, definition.metadata().get("home.structure"));
            state.setNeed("fatigue", Math.max(0D, snapshot.need("fatigue") - .18D));
            setActivity(state, definition.id(), Activity.SLEEP, Map.of());
            return;
        }
        if (dayTime >= 1_000L && dayTime < 9_000L && definition.metadata().containsKey("work.structure")) {
            go(definition, definition.metadata().get("work.structure"));
            setActivity(state, definition.id(), Activity.WORK, Map.of("structure", definition.metadata().get("work.structure")));
            return;
        }

        SplittableRandom random = new SplittableRandom(definition.id().getMostSignificantBits() ^ definition.id().getLeastSignificantBits() ^ (tick / PULSE_TICKS));
        double gambling = Math.max(snapshot.trait("gambling_affinity"), number(definition.metadata().get("society.gambling_affinity"), .35D));
        GamblingEngine.Habit habit = SocietyApi.gambling().habit(actor);
        gambling = Math.min(1D, gambling * .72D + habit.compulsion() * .45D);
        double alcohol = Math.max(snapshot.trait("alcohol_affinity"), number(definition.metadata().get("society.alcohol_affinity"), .30D));
        RomanceEngine.Bond bond = partners.get(actor);

        if (dayTime >= 9_000L && dayTime < 17_500L && tick - lastLeisure.getOrDefault(definition.id(), Long.MIN_VALUE / 2L) >= LEISURE_COOLDOWN) {
            double roll = random.nextDouble();
            if (gambling > .45D && roll < gambling * .42D && gamble(tick, definition, state, actor, economy, byKind, random)) return;
            if (alcohol > .40D && roll < Math.min(.85D, gambling * .42D + alcohol * .48D) && drink(tick, definition, state, actor, economy, byKind)) return;
            if (bond != null && bond.stage() != RomanceEngine.Stage.ENDED && bond.stage() != RomanceEngine.Stage.SEPARATED
                    && affection(tick, definition, state, actor, bond)) return;
        }

        double criminality = Math.max(snapshot.trait("criminality"), number(definition.metadata().get("society.criminality"), .20D));
        double moneyPressure = Math.max(snapshot.need("money"), snapshot.need("financial_stress"));
        if (criminality > .62D && moneyPressure > .58D && tick - lastCrime.getOrDefault(definition.id(), Long.MIN_VALUE / 2L) >= CRIME_COOLDOWN
                && random.nextDouble() < criminality * moneyPressure * .28D && commitEconomicCrime(tick, definition, state, actor, economy, random)) return;

        String home = definition.metadata().get("home.structure");
        if (home != null) go(definition, home);
        setActivity(state, definition.id(), home == null ? Activity.IDLE : Activity.HOME, Map.of());
    }

    private boolean eat(long tick, NpcDefinition definition, NpcState state, ActorId actor, EconomyEngine.Snapshot economy,
                        Map<String, List<EconomyEngine.Business>> byKind) {
        EconomyEngine.Business venue = firstBusiness(byKind, "restaurant", "food", "tavern", "bar");
        if (venue == null) return false;
        long price = longFact(venue, "meal_price", 20L, 1L, 100_000L);
        if (!servicePurchase(actor, venue, price, "meal:" + definition.id() + ":" + tick)) return false;
        go(definition, venue.locationId());
        state.setNeed("hunger", .08D);
        state.setNeed("stress", Math.max(0D, state.snapshot(1).need("stress") - .05D));
        lastLeisure.put(definition.id(), tick);
        setActivity(state, definition.id(), Activity.EAT, Map.of("business", venue.id().toString()));
        return true;
    }

    private boolean drink(long tick, NpcDefinition definition, NpcState state, ActorId actor, EconomyEngine.Snapshot economy,
                          Map<String, List<EconomyEngine.Business>> byKind) {
        EconomyEngine.Business venue = firstBusiness(byKind, "tavern", "bar", "pub");
        if (venue == null) return false;
        long price = longFact(venue, "drink_price", 15L, 1L, 100_000L);
        if (!servicePurchase(actor, venue, price, "drink:" + definition.id() + ":" + tick)) return false;
        go(definition, venue.locationId());
        NpcSnapshot snapshot = state.snapshot(1);
        state.setNeed("stress", Math.max(0D, snapshot.need("stress") - .12D));
        state.setNeed("entertainment", Math.max(0D, snapshot.need("entertainment") - .16D));
        state.remember("went_drinking", Map.of("business", venue.id().toString()), .28D, 1D);
        lastLeisure.put(definition.id(), tick);
        setActivity(state, definition.id(), Activity.DRINK, Map.of("business", venue.id().toString()));
        return true;
    }

    private boolean gamble(long tick, NpcDefinition definition, NpcState state, ActorId actor, EconomyEngine.Snapshot economy,
                           Map<String, List<EconomyEngine.Business>> byKind, SplittableRandom random) {
        EconomyEngine.Business house = firstBusiness(byKind, "casino", "gambling", "gambling_den", "taixiu", "tai_xiu");
        if (house == null || house.owner().equals(actor)) return false;
        long balance = wallet(economy, actor);
        if (balance <= 0L && !borrowForGambling(definition, state, actor, byKind, tick)) return false;
        balance = wallet(LivelyApi.economy().snapshot(), actor);
        long cap = longFact(house, "max_bet", 1_000L, 1L, 1_000_000_000L);
        long stake = Math.max(1L, Math.min(cap, Math.max(1L, balance / 10L)));
        if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.TRANSFER, actor, house.owner(), stake,
                "gamble:stake:" + definition.id() + ":" + tick).isEmpty()) return false;

        double houseEdge = number(house.facts().get("house_edge"), .04D);
        boolean win = random.nextDouble() < Math.max(.05D, Math.min(.95D, .50D - houseEdge / 2D));
        long payout = 0L;
        GamblingEngine.Result result;
        if (win) {
            long requested = Math.min(2_000_000_000_000L, stake * 2L);
            if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.TRANSFER, house.owner(), actor, requested,
                    "gamble:payout:" + definition.id() + ":" + tick).isPresent()) {
                payout = requested; result = GamblingEngine.Result.WIN;
            } else result = GamblingEngine.Result.UNPAID;
        } else result = GamblingEngine.Result.LOSS;

        SocietyApi.gambling().record(actor, house.id(), house.facts().getOrDefault("game", "tai_xiu"),
                house.facts().getOrDefault("currency", "internal"), stake, payout, result,
                Map.of("house", house.name(), "semantic", "true"));
        go(definition, house.locationId());
        lastLeisure.put(definition.id(), tick);
        NpcSnapshot snapshot = state.snapshot(1);
        state.setNeed("entertainment", Math.max(0D, snapshot.need("entertainment") - .20D));
        state.setNeed("financial_stress", result == GamblingEngine.Result.LOSS ? Math.min(1D, snapshot.need("financial_stress") + .12D)
                : Math.max(0D, snapshot.need("financial_stress") - .08D));
        state.remember("gambling_result", Map.of("business", house.id().toString(), "result", result.name(),
                "stake", Long.toString(stake), "payout", Long.toString(payout)), .42D, 1D);
        setActivity(state, definition.id(), Activity.GAMBLE, Map.of("business", house.id().toString(), "result", result.name()));
        return true;
    }

    private boolean borrowForGambling(NpcDefinition definition, NpcState state, ActorId debtor,
                                      Map<String, List<EconomyEngine.Business>> byKind, long tick) {
        EconomyEngine.Business lenderBusiness = firstBusiness(byKind, "loan_shark", "money_lender");
        if (lenderBusiness == null || lenderBusiness.owner().equals(debtor)) return false;
        long principal = longFact(lenderBusiness, "loan_amount", 500L, 1L, 1_000_000_000L);
        if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.TRANSFER, lenderBusiness.owner(), debtor, principal,
                "loan:" + debtor.uuid() + ":" + tick).isEmpty()) return false;
        int interest = (int) longFact(lenderBusiness, "interest_bps", 1_500L, 0L, 5_000L);
        DebtEngine.Contract debt = SocietyApi.debts().issue(lenderBusiness.owner(), debtor, principal, interest,
                Instant.now().plus(Duration.ofMinutes(20)), false,
                Map.of("source", "gambling", "business", lenderBusiness.id().toString()));
        state.remember("borrowed_money", Map.of("debt", debt.id().toString(), "principal", Long.toString(principal)), .68D, 1D);
        state.setNeed("financial_stress", Math.min(1D, state.snapshot(1).need("financial_stress") + .18D));
        return true;
    }

    private boolean payOwnDebt(NpcDefinition definition, NpcState state, ActorId actor, EconomyEngine.Snapshot economy) {
        DebtEngine.Contract debt = SocietyApi.debts().forDebtor(actor).stream()
                .filter(contract -> contract.status() == DebtEngine.Status.DELINQUENT || contract.status() == DebtEngine.Status.COLLECTION)
                .findFirst().orElse(null);
        if (debt == null) return false;
        long balance = wallet(economy, actor);
        if (balance <= 0L) { state.setNeed("financial_stress", Math.min(1D, state.snapshot(1).need("financial_stress") + .05D)); return false; }
        long payment = Math.min(debt.outstanding(), Math.max(1L, balance / 4L));
        if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.TRANSFER, actor, debt.creditor(), payment,
                "debt-payment:" + debt.id() + ":" + System.nanoTime()).isEmpty()) return false;
        SocietyApi.debts().pay(debt.id(), payment);
        state.remember("debt_payment", Map.of("debt", debt.id().toString(), "amount", Long.toString(payment)), .52D, 1D);
        state.setNeed("financial_stress", Math.max(0D, state.snapshot(1).need("financial_stress") - .08D));
        return false;
    }

    private boolean collectDebt(long tick, NpcDefinition definition, NpcState state, ActorId creditor) {
        DebtEngine.Contract debt = SocietyApi.debts().forCreditor(creditor).stream()
                .filter(contract -> contract.status() == DebtEngine.Status.DELINQUENT || contract.status() == DebtEngine.Status.COLLECTION)
                .findFirst().orElse(null);
        if (debt == null || tick - lastCollection.getOrDefault(definition.id(), Long.MIN_VALUE / 2L) < COLLECTION_COOLDOWN) return false;
        SocietyApi.debts().startCollection(debt.id(), "collector_dispatched");
        lastCollection.put(definition.id(), tick);
        if (debt.debtor().kind() == ActorId.Kind.NPC && LivelyApi.npcs().get(debt.debtor().uuid()).isPresent()) {
            LivelyApi.worldNavigation().follow(definition.id(), debt.debtor().uuid());
        }
        state.remember("debt_collection", Map.of("debt", debt.id().toString(), "debtor", debt.debtor().uuid().toString()), .58D, 1D);
        if (!debt.legal() && state.snapshot(1).trait("aggression") > .62D) {
            CrimeEngine.Crime crime = LivelyApi.crime().create(CrimeEngine.Type.ASSAULT, debt.debtor(), creditor,
                    definition.metadata().get("work.structure"), "debt_extortion", Set.of(),
                    Map.of("kind", "extortion", "debt", debt.id().toString(), "semantic_only", "true"));
            LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.RECORD, creditor, creditor, .82D, .76D, false,
                    Map.of("debt", debt.id().toString()));
            LivelyApi.social().apply(creditor, debt.debtor(), new SocialEngine.SocialDelta(-.02D, -.04D, .08D, .16D, 0D, 0D, .05D,
                    "debt_extortion", Map.of("debt", debt.id().toString())));
        }
        setActivity(state, definition.id(), Activity.DEBT_COLLECTION, Map.of("debt", debt.id().toString()));
        return true;
    }

    private void patrol(NpcDefinition definition, NpcState state, List<CrimeEngine.Crime> crimes) {
        CrimeEngine.Crime crime = crimes.isEmpty() ? null : crimes.get(0);
        if (crime != null) {
            LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
            if (crime.perpetrator() != null && crime.perpetrator().kind() == ActorId.Kind.NPC
                    && LivelyApi.npcs().get(crime.perpetrator().uuid()).isPresent()) {
                LivelyApi.worldNavigation().follow(definition.id(), crime.perpetrator().uuid());
            } else go(definition, crime.locationId());
            state.remember("police_dispatch", Map.of("crime", crime.id().toString(), "type", crime.type().name()), .48D, 1D);
            return;
        }
        go(definition, definition.metadata().getOrDefault("routine.patrol", definition.metadata().get("work.structure")));
    }

    private boolean affection(long tick, NpcDefinition definition, NpcState state, ActorId actor, RomanceEngine.Bond bond) {
        ActorId partner = bond.a().equals(actor) ? bond.b() : bond.a();
        if (partner.kind() != ActorId.Kind.NPC || LivelyApi.npcs().get(partner.uuid()).isEmpty()) return false;
        LivelyApi.worldNavigation().follow(definition.id(), partner.uuid());
        LivelyApi.romance().recordSharedMemory(actor, partner, "spent_private_time_together", .16D);
        LivelyApi.social().apply(actor, partner, new SocialEngine.SocialDelta(.02D, .035D, 0D, 0D, .015D, .01D, .025D,
                "affectionate_time", Map.of()));
        state.remember("affectionate_time", Map.of("partner", partner.uuid().toString(), "stage", bond.stage().name()), .36D, 1D);
        lastLeisure.put(definition.id(), tick);
        setActivity(state, definition.id(), Activity.AFFECTION, Map.of("partner", partner.uuid().toString()));
        return true;
    }

    private boolean commitEconomicCrime(long tick, NpcDefinition definition, NpcState state, ActorId actor,
                                        EconomyEngine.Snapshot economy, SplittableRandom random) {
        EconomyEngine.Business target = economy.businesses().values().stream().filter(business -> !business.owner().equals(actor))
                .sorted(Comparator.comparing(business -> business.id().toString())).findFirst().orElse(null);
        if (target == null) return false;
        long victimBalance = wallet(economy, target.owner());
        if (victimBalance <= 10L) return false;
        long amount = Math.max(1L, Math.min(5_000L, Math.round(victimBalance * (.02D + random.nextDouble() * .04D))));
        if (LivelyApi.economy().transfer(EconomyEngine.TransactionType.TRANSFER, target.owner(), actor, amount,
                "semantic-theft:" + actor.uuid() + ":" + tick).isEmpty()) return false;
        CrimeEngine.Crime crime = LivelyApi.crime().create(CrimeEngine.Type.THEFT, target.owner(), actor, target.locationId(),
                "financial_pressure", Set.of(), Map.of("kind", "robbery", "business", target.id().toString(),
                        "amount", Long.toString(amount), "semantic_only", "true"));
        LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.RECORD, target.owner(), actor, .60D, .64D, false,
                Map.of("transaction", "virtual_business_loss"));
        state.remember("committed_crime", Map.of("crime", crime.id().toString(), "amount", Long.toString(amount)), .78D, 1D);
        state.setNeed("financial_stress", Math.max(0D, state.snapshot(1).need("financial_stress") - .18D));
        lastCrime.put(definition.id(), tick);
        setActivity(state, definition.id(), Activity.CRIME, Map.of("crime", crime.id().toString()));
        return true;
    }

    private boolean servicePurchase(ActorId buyer, EconomyEngine.Business venue, long amount, String reference) {
        if (!venue.open() || amount <= 0L) return false;
        return LivelyApi.economy().transfer(EconomyEngine.TransactionType.SERVICE, buyer, venue.owner(), amount, reference).isPresent();
    }

    private void setActivity(NpcState state, UUID npcId, Activity activity, Map<String, String> facts) {
        Activity previous = activities.put(npcId, activity);
        if (previous == activity) return;
        HashMap<String, String> memory = new HashMap<>(facts);
        memory.put("activity", activity.name());
        if (previous != null) memory.put("previous", previous.name());
        state.remember("activity_changed", memory, .18D, 1D);
    }

    private void go(NpcDefinition definition, String structure) {
        if (definition.spawned() && structure != null && !structure.isBlank() && LivelyApi.worldNavigation() != null) {
            LivelyApi.worldNavigation().goToStructure(definition.id(), structure);
        }
    }

    private static Map<String, List<EconomyEngine.Business>> indexBusinesses(EconomyEngine.Snapshot economy) {
        HashMap<String, List<EconomyEngine.Business>> result = new HashMap<>();
        for (EconomyEngine.Business business : economy.businesses().values()) {
            String kind = business.facts().getOrDefault("kind", "shop").toLowerCase(Locale.ROOT);
            result.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(business);
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(business -> business.id().toString())));
        return result;
    }

    private static Map<ActorId, RomanceEngine.Bond> indexPartners() {
        HashMap<ActorId, RomanceEngine.Bond> result = new HashMap<>();
        for (RomanceEngine.Bond bond : LivelyApi.romance().snapshot().values()) {
            if (bond.stage() == RomanceEngine.Stage.ENDED) continue;
            result.merge(bond.a(), bond, SocietySimulationService::preferBond);
            result.merge(bond.b(), bond, SocietySimulationService::preferBond);
        }
        return result;
    }

    private static RomanceEngine.Bond preferBond(RomanceEngine.Bond a, RomanceEngine.Bond b) {
        int stageA = a.stage().ordinal(), stageB = b.stage().ordinal();
        if (stageA != stageB) return stageA > stageB ? a : b;
        return a.stability() >= b.stability() ? a : b;
    }

    private static EconomyEngine.Business firstBusiness(Map<String, List<EconomyEngine.Business>> byKind, String... kinds) {
        for (String kind : kinds) {
            List<EconomyEngine.Business> values = byKind.get(kind);
            if (values != null) for (EconomyEngine.Business business : values) if (business.open()) return business;
        }
        return null;
    }

    private static long wallet(EconomyEngine.Snapshot economy, ActorId actor) {
        EconomyEngine.Wallet wallet = economy.wallets().get(actor);
        return wallet == null ? 0L : wallet.balance();
    }

    private static boolean bool(NpcDefinition definition, String key) {
        return Boolean.parseBoolean(definition.metadata().getOrDefault(key, "false"));
    }

    private static boolean role(NpcDefinition definition, String... tokens) {
        String role = definition.role().toLowerCase(Locale.ROOT);
        for (String token : tokens) if (role.contains(token)) return true;
        return false;
    }

    private static double number(String value, double fallback) {
        try { return Math.max(0D, Math.min(1D, Double.parseDouble(value == null ? Double.toString(fallback) : value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static long longFact(EconomyEngine.Business business, String key, long fallback, long min, long max) {
        try { return Math.max(min, Math.min(max, Long.parseLong(business.facts().getOrDefault(key, Long.toString(fallback))))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
