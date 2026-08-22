package vn.svframe.lively.simulation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.social.SocialEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded autonomous business lifecycle: discovery, staffing, hours, payroll, rent/tax, paid restock,
 * employee turnover, bankruptcy/recovery and hidden markets. No inventory is conjured for free.
 */
public final class BusinessSimulationService {
    private static final long PULSE_TICKS = 1200L;
    private static final long UNDERWORLD_RUMOR_TICKS = 24_000L;
    private static final int MAX_BUSINESSES_PER_PULSE = 128;
    private static final int MAX_STOCKS_PER_PULSE = 256;
    private static final ActorId SUPPLY_SINK = new ActorId(UUID.nameUUIDFromBytes("lively:external_supply".getBytes(StandardCharsets.UTF_8)), ActorId.Kind.SYSTEM);

    private final MinecraftServer server;
    private final ConcurrentHashMap<UUID, Long> lastUnderworldRumor = new ConcurrentHashMap<>();
    private long lastPulse;

    public BusinessSimulationService() { this(null); }
    public BusinessSimulationService(MinecraftServer server) { this.server = server; }

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS || LivelyApi.npcs() == null) return;
        lastPulse = tick;
        Map<UUID, NpcDefinition> npcSnapshot = LivelyApi.npcs().snapshot();
        discoverBusinesses(npcSnapshot);
        staffBusinesses(npcSnapshot);
        operateBusinesses(tick, npcSnapshot);
        restockBusinesses(tick);
    }

    private void discoverBusinesses(Map<UUID, NpcDefinition> npcs) {
        Set<ActorId> owners = new HashSet<>();
        LivelyApi.economy().snapshot().businesses().values().forEach(business -> owners.add(business.owner()));
        int created = 0;
        for (NpcDefinition npc : npcs.values().stream().sorted(Comparator.comparing(value -> value.id().toString())).toList()) {
            if (created >= MAX_BUSINESSES_PER_PULSE) break;
            String name = npc.metadata().get("business.name");
            if (name == null || name.isBlank()) continue;
            ActorId owner = new ActorId(npc.id(), ActorId.Kind.NPC);
            if (owners.contains(owner)) continue;
            String kind = npc.metadata().getOrDefault("business.kind", "shop").trim().toLowerCase(java.util.Locale.ROOT);
            boolean blackMarket = kind.equals("black_market") || kind.equals("underworld")
                    || Boolean.parseBoolean(npc.metadata().getOrDefault("business.illegal", "false"));
            String location = npc.metadata().getOrDefault("business.location", npc.metadata().get("work.structure"));
            EconomyEngine.Business business = LivelyApi.economy().createBusiness(owner, name, location,
                    businessFacts(npc.metadata(), kind, blackMarket));
            long initial = longValue(npc.metadata().get("business.initial_balance"), 0L, 0L, 10_000_000_000_000L);
            LivelyApi.economy().ensureWallet(owner, initial);
            bootstrapStock(business, npc.metadata());
            owners.add(owner);
            created++;
        }
    }

    private static Map<String, String> businessFacts(Map<String, String> metadata, String kind, boolean blackMarket) {
        HashMap<String, String> facts = new HashMap<>();
        facts.put("kind", kind);
        facts.put("wage", metadata.getOrDefault("business.wage", "0"));
        facts.put("auto_hire", metadata.getOrDefault("business.auto_hire", blackMarket ? "false" : "true"));
        facts.put("hidden", metadata.getOrDefault("business.hidden", Boolean.toString(blackMarket)));
        facts.put("access_trust", metadata.getOrDefault("business.access_trust", blackMarket ? "0.35" : "0"));
        facts.put("risk", metadata.getOrDefault("business.risk", blackMarket ? "0.65" : "0"));
        facts.put("illegal", Boolean.toString(blackMarket));
        facts.put("open_minute", metadata.getOrDefault("business.open_minute", "0"));
        facts.put("close_minute", metadata.getOrDefault("business.close_minute", "1440"));
        facts.put("minimum_staff", metadata.getOrDefault("business.minimum_staff", "1"));
        facts.put("virtual_staff", metadata.getOrDefault("business.virtual_staff", "false"));
        facts.put("rent", metadata.getOrDefault("business.rent", "0"));
        facts.put("tax_bps", metadata.getOrDefault("business.tax_bps", "0"));
        facts.put("max_missed_payroll", metadata.getOrDefault("business.max_missed_payroll", "3"));
        facts.put("restock_cost_ratio", metadata.getOrDefault("business.restock_cost_ratio", "0.50"));
        facts.put("restock_batch_ratio", metadata.getOrDefault("business.restock_batch_ratio", "0.20"));
        facts.put("bankruptcy_balance", metadata.getOrDefault("business.bankruptcy_balance", "0"));
        facts.put("recovery_balance", metadata.getOrDefault("business.recovery_balance", "100"));
        copyFact(metadata, facts, "currency");
        copyFact(metadata, facts, "meal_price");
        copyFact(metadata, facts, "drink_price");
        copyFact(metadata, facts, "max_bet");
        copyFact(metadata, facts, "house_edge");
        copyFact(metadata, facts, "game");
        copyFact(metadata, facts, "loan_amount");
        copyFact(metadata, facts, "interest_bps");
        copyFact(metadata, facts, "supplier_business");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!entry.getKey().startsWith("business.fact.")) continue;
            String key = entry.getKey().substring("business.fact.".length()).trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_.:-]", "_");
            String value = entry.getValue();
            if (!key.isBlank() && key.length() <= 64 && value != null && value.length() <= 256 && facts.size() < 96) facts.put(key, value);
        }
        return Map.copyOf(facts);
    }

    private static void copyFact(Map<String, String> metadata, Map<String, String> facts, String key) {
        String value = metadata.get("business." + key);
        if (value != null && !value.isBlank() && value.length() <= 256) facts.put(key, value);
    }

    private void bootstrapStock(EconomyEngine.Business business, Map<String, String> metadata) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!entry.getKey().startsWith("business.stock.")) continue;
            String item = entry.getKey().substring("business.stock.".length());
            String[] parts = entry.getValue().split(",");
            if (item.isBlank() || parts.length < 3) continue;
            try {
                long quantity = Long.parseLong(parts[0].trim());
                long target = Long.parseLong(parts[1].trim());
                long price = Long.parseLong(parts[2].trim());
                double demand = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : .5D;
                double supply = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : .5D;
                LivelyApi.economy().setStock(business.id(), item, quantity, target, price, demand, supply);
            } catch (NumberFormatException ignored) { }
        }
    }

    /** One NPC pass builds workplace candidates; businesses consume those buckets without nested full scans. */
    private void staffBusinesses(Map<UUID, NpcDefinition> npcs) {
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        Map<String, List<ActorId>> byWorkplace = new HashMap<>();
        for (NpcDefinition npc : npcs.values()) {
            String workplace = npc.metadata().get("work.structure");
            if (workplace == null || workplace.isBlank()) continue;
            byWorkplace.computeIfAbsent(workplace, ignored -> new ArrayList<>())
                    .add(new ActorId(npc.id(), ActorId.Kind.NPC));
        }
        byWorkplace.values().forEach(list -> list.sort(Comparator.comparing(actor -> actor.uuid().toString())));

        Set<ActorId> employed = new HashSet<>();
        for (EconomyEngine.Business business : economy.businesses().values()) employed.addAll(business.employees());

        for (EconomyEngine.Business business : economy.businesses().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_BUSINESSES_PER_PULSE).toList()) {
            if (!Boolean.parseBoolean(business.facts().getOrDefault("auto_hire", "true")) || business.employees().size() >= 16
                    || business.locationId() == null || Boolean.parseBoolean(business.facts().getOrDefault("bankrupt", "false"))) continue;
            int slots = 16 - business.employees().size();
            for (ActorId candidate : byWorkplace.getOrDefault(business.locationId(), List.of())) {
                if (slots <= 0) break;
                if (candidate.equals(business.owner()) || employed.contains(candidate)) continue;
                if (LivelyApi.economy().assignEmployee(business.id(), candidate).isPresent()) {
                    employed.add(candidate);
                    slots--;
                }
            }
        }
    }

    private void operateBusinesses(long tick, Map<UUID, NpcDefinition> npcs) {
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        long worldTime = worldTime(tick);
        long day = Math.floorDiv(worldTime, 24_000L);
        int minute = (int) Math.floorMod(worldTime, 24_000L) * 1440 / 24_000;

        for (EconomyEngine.Business business : economy.businesses().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_BUSINESSES_PER_PULSE).toList()) {
            boolean bankrupt = Boolean.parseBoolean(business.facts().getOrDefault("bankrupt", "false"));
            long balance = wallet(economy, business.owner());
            long recovery = longValue(business.facts().get("recovery_balance"), 100L, 0L, 10_000_000_000_000L);
            if (bankrupt && balance >= recovery) {
                LivelyApi.economy().updateFacts(business.id(), Map.of("bankrupt", "false", "missed_payroll", "0", "recovered_day", Long.toString(day)));
                bankrupt = false;
            }

            boolean structureOpen = structureAllowsOpen(business);
            boolean withinHours = withinHours(minute,
                    (int) longValue(business.facts().get("open_minute"), 0L, 0L, 1439L),
                    (int) longValue(business.facts().get("close_minute"), 1440L, 1L, 1440L));
            int minimumStaff = (int) longValue(business.facts().get("minimum_staff"), 1L, 0L, 128L);
            boolean virtualStaff = Boolean.parseBoolean(business.facts().getOrDefault("virtual_staff", "false"));
            int present = virtualStaff ? minimumStaff : staffPresent(business, npcs);
            boolean shouldOpen = !bankrupt && structureOpen && withinHours && present >= minimumStaff;
            if (shouldOpen != business.open()) LivelyApi.economy().setOpen(business.id(), shouldOpen);

            if (lastObligationDay(business) < day) settleDailyObligations(business, day);
            EconomyEngine.Business refreshed = LivelyApi.economy().business(business.id()).orElse(business);
            if (refreshed.open() && Boolean.parseBoolean(refreshed.facts().getOrDefault("illegal", "false"))) emitUnderworldRumor(refreshed, tick);
        }
    }

    private void settleDailyObligations(EconomyEngine.Business business, long day) {
        long wage = longValue(business.facts().get("wage"), 0L, 0L, 1_000_000_000L);
        int paid = wage <= 0L ? business.employees().size() : LivelyApi.economy().payroll(business.id(), wage);
        int missed = integer(business.facts().get("missed_payroll"), 0);
        if (paid < business.employees().size()) {
            missed++;
            for (ActorId employee : business.employees().subList(Math.min(paid, business.employees().size()), business.employees().size())) {
                LivelyApi.social().apply(business.owner(), employee, new SocialEngine.SocialDelta(-.05D, -.04D, -.03D, 0D,
                        -.05D, 0D, .02D, "unpaid_wage", Map.of("business", business.id().toString())));
            }
        } else missed = Math.max(0, missed - 1);

        long rent = longValue(business.facts().get("rent"), 0L, 0L, 1_000_000_000_000L);
        int taxBps = (int) longValue(business.facts().get("tax_bps"), 0L, 0L, 10_000L);
        ActorId treasury = treasury(business);
        LivelyApi.economy().ensureWallet(treasury, 0L);
        if (rent > 0L) LivelyApi.economy().transferOnce(EconomyEngine.TransactionType.RENT, business.owner(), treasury, rent,
                "business-rent:" + business.id() + ":" + day);
        long balance = LivelyApi.economy().snapshot().wallets().getOrDefault(business.owner(), new EconomyEngine.Wallet(business.owner(), 0L, 0L)).balance();
        long tax = taxBps <= 0 ? 0L : Math.min(balance, Math.max(0L, balance * taxBps / 10_000L));
        if (tax > 0L) LivelyApi.economy().transferOnce(EconomyEngine.TransactionType.TAX, business.owner(), treasury, tax,
                "business-tax:" + business.id() + ":" + day);

        int maxMissed = (int) longValue(business.facts().get("max_missed_payroll"), 3L, 1L, 30L);
        long bankruptcyBalance = longValue(business.facts().get("bankruptcy_balance"), 0L, 0L, 10_000_000_000_000L);
        long after = LivelyApi.economy().snapshot().wallets().getOrDefault(business.owner(), new EconomyEngine.Wallet(business.owner(), 0L, 0L)).balance();
        boolean bankrupt = missed >= maxMissed && after <= bankruptcyBalance;
        HashMap<String, String> changes = new HashMap<>();
        changes.put("last_obligation_day", Long.toString(day));
        changes.put("missed_payroll", Integer.toString(missed));
        if (bankrupt) { changes.put("bankrupt", "true"); changes.put("bankrupt_day", Long.toString(day)); }
        LivelyApi.economy().updateFacts(business.id(), changes);
        if (bankrupt) LivelyApi.economy().setOpen(business.id(), false);

        if (missed >= maxMissed) {
            for (ActorId employee : List.copyOf(business.employees())) {
                SocialEngine.Relationship relation = LivelyApi.social().findRelationship(business.owner(), employee).orElse(null);
                if (relation == null || relation.trust() < -.10D || relation.loyalty() < -.10D) {
                    LivelyApi.economy().removeEmployee(business.id(), employee);
                    if (employee.kind() == ActorId.Kind.NPC && LivelyApi.states() != null) {
                        LivelyApi.states().get(employee.uuid()).ifPresent(state -> state.remember("quit_unpaid_job",
                                Map.of("business", business.id().toString()), .68D, 1D));
                    }
                }
            }
        }
    }

    private void restockBusinesses(long tick) {
        EconomyEngine.Snapshot snapshot = LivelyApi.economy().snapshot();
        int touched = 0;
        for (EconomyEngine.Stock stock : snapshot.stocks().values().stream()
                .sorted(Comparator.comparing(value -> value.key().businessId().toString() + ":" + value.key().itemId())).toList()) {
            if (touched++ >= MAX_STOCKS_PER_PULSE) break;
            EconomyEngine.Business business = snapshot.businesses().get(stock.key().businessId());
            if (business == null || Boolean.parseBoolean(business.facts().getOrDefault("bankrupt", "false"))) continue;
            if (stock.quantity() >= stock.targetQuantity() || stock.supply() <= .05D) continue;
            long missing = stock.targetQuantity() - stock.quantity();
            double batchRatio = doubleValue(business.facts().get("restock_batch_ratio"), .20D, .01D, 1D);
            long quantity = Math.max(1L, Math.min(missing, Math.round(stock.targetQuantity() * batchRatio * Math.max(.15D, stock.supply()))));
            if (quantity <= 0L) continue;
            if (!purchaseSupply(business, stock, quantity, tick)) continue;
            LivelyApi.economy().setStock(stock.key().businessId(), stock.key().itemId(), stock.quantity() + quantity,
                    stock.targetQuantity(), stock.basePrice(), clamp01(stock.demand() - .01D), clamp01(stock.supply() - .025D));
        }
    }

    private boolean purchaseSupply(EconomyEngine.Business business, EconomyEngine.Stock stock, long quantity, long tick) {
        UUID supplierId = uuid(business.facts().get("supplier_business"));
        if (supplierId != null && !supplierId.equals(business.id())) {
            EconomyEngine.Business supplier = LivelyApi.economy().business(supplierId).orElse(null);
            if (supplier != null && supplier.open()) {
                return LivelyApi.economy().buy(supplierId, business.owner(), stock.key().itemId(), quantity).isPresent();
            }
        }
        double costRatio = doubleValue(business.facts().get("restock_cost_ratio"), .50D, .01D, 1D);
        long unitCost = Math.max(1L, Math.round(stock.basePrice() * costRatio));
        long cost;
        try { cost = Math.multiplyExact(unitCost, quantity); }
        catch (ArithmeticException overflow) { return false; }
        LivelyApi.economy().ensureWallet(SUPPLY_SINK, 0L);
        return LivelyApi.economy().transfer(EconomyEngine.TransactionType.BUY, business.owner(), SUPPLY_SINK, cost,
                "external-supply:" + business.id() + ":" + stock.key().itemId() + ":" + tick).isPresent();
    }

    private int staffPresent(EconomyEngine.Business business, Map<UUID, NpcDefinition> npcs) {
        if (business.locationId() == null) return 0;
        SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(business.locationId()).orElse(null);
        if (structure == null) return 0;
        int present = 0;
        ArrayList<ActorId> staff = new ArrayList<>();
        staff.add(business.owner()); staff.addAll(business.employees());
        for (ActorId actor : staff) {
            if (actor.kind() != ActorId.Kind.NPC) continue;
            NpcDefinition definition = npcs.get(actor.uuid());
            if (definition == null || !definition.spawned()) continue;
            Vec3d pos = LivelyApi.npcs().position(actor.uuid()).orElse(null);
            String world = LivelyApi.npcs().worldKey(actor.uuid()).orElse(definition.world());
            if (pos != null && structure.bounds().contains(world, pos.x, pos.y, pos.z)) present++;
        }
        return present;
    }

    private boolean structureAllowsOpen(EconomyEngine.Business business) {
        if (business.locationId() == null) return true;
        return LivelyApi.structures().get(business.locationId())
                .map(SemanticStructureRegistry.Structure::state)
                .map(state -> state == SemanticStructureRegistry.OperationalState.OPEN
                        || state == SemanticStructureRegistry.OperationalState.FESTIVAL
                        || state == SemanticStructureRegistry.OperationalState.CONTROLLED)
                .orElse(true);
    }

    private void emitUnderworldRumor(EconomyEngine.Business business, long tick) {
        long previous = lastUnderworldRumor.getOrDefault(business.id(), Long.MIN_VALUE / 2L);
        if (tick - previous < UNDERWORLD_RUMOR_TICKS) return;
        lastUnderworldRumor.put(business.id(), tick);
        double risk = doubleValue(business.facts().get("risk"), .65D, 0D, 1D);
        String location = business.locationId() == null ? "một chỗ kín" : business.locationId();
        try {
            LivelyApi.social().createRumor("criminal_underworld", business.owner(), business.owner(),
                    "Có người đang giao dịch hàng khó giải thích quanh " + location + ".",
                    .28D + risk * .24D, .40D + risk * .35D, Duration.ofDays(2));
        } catch (IllegalArgumentException ignored) { }
    }

    private long worldTime(long fallbackTick) { return server == null ? fallbackTick : server.getOverworld().getTimeOfDay(); }
    private static boolean withinHours(int minute, int open, int close) {
        if (open == 0 && close == 1440) return true;
        if (open < close) return minute >= open && minute < close;
        return minute >= open || minute < close;
    }
    private static long lastObligationDay(EconomyEngine.Business business) {
        return longValue(business.facts().get("last_obligation_day"), Long.MIN_VALUE / 4L, Long.MIN_VALUE / 4L, Long.MAX_VALUE / 4L);
    }
    private static long wallet(EconomyEngine.Snapshot snapshot, ActorId actor) {
        EconomyEngine.Wallet wallet = snapshot.wallets().get(actor); return wallet == null ? 0L : wallet.balance();
    }
    private static ActorId treasury(EconomyEngine.Business business) {
        String town = business.locationId() == null ? "global" : LivelyApi.structures().get(business.locationId())
                .map(SemanticStructureRegistry.Structure::townId).orElse("global");
        if (town == null || town.isBlank()) town = "global";
        return new ActorId(UUID.nameUUIDFromBytes(("lively:treasury:" + town).getBytes(StandardCharsets.UTF_8)), ActorId.Kind.SYSTEM);
    }
    private static UUID uuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static int integer(String raw, int fallback) {
        try { return raw == null ? fallback : Integer.parseInt(raw.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static long longValue(String raw, long fallback, long min, long max) {
        try { return Math.max(min, Math.min(max, Long.parseLong(raw == null ? Long.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static double doubleValue(String raw, double fallback, double min, double max) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(raw == null ? Double.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
