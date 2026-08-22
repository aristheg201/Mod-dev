package vn.svframe.lively.simulation;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.world.SemanticStructureRegistry;

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

/** Bounded autonomous business lifecycle: discovery, staffing, hours, payroll, hidden markets and stock bootstrap. */
public final class BusinessSimulationService {
    private static final long PULSE_TICKS = 1200L;
    private static final long UNDERWORLD_RUMOR_TICKS = 24_000L;
    private static final int MAX_BUSINESSES_PER_PULSE = 128;
    private final ConcurrentHashMap<UUID, Long> lastUnderworldRumor = new ConcurrentHashMap<>();
    private long lastPulse;

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS || LivelyApi.npcs() == null) return;
        lastPulse = tick;
        Map<UUID, NpcDefinition> npcSnapshot = LivelyApi.npcs().snapshot();
        discoverBusinesses(npcSnapshot);
        staffBusinesses(npcSnapshot);
        operateBusinesses(tick);
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
        copyFact(metadata, facts, "currency");
        copyFact(metadata, facts, "meal_price");
        copyFact(metadata, facts, "drink_price");
        copyFact(metadata, facts, "max_bet");
        copyFact(metadata, facts, "house_edge");
        copyFact(metadata, facts, "game");
        copyFact(metadata, facts, "loan_amount");
        copyFact(metadata, facts, "interest_bps");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!entry.getKey().startsWith("business.fact.")) continue;
            String key = entry.getKey().substring("business.fact.".length()).trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_.:-]", "_");
            String value = entry.getValue();
            if (!key.isBlank() && key.length() <= 64 && value != null && value.length() <= 256 && facts.size() < 64) facts.put(key, value);
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

    /** One NPC pass builds workplace candidates; businesses then consume those buckets without nested full scans. */
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
                    || business.locationId() == null) continue;
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

    private void operateBusinesses(long tick) {
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        for (EconomyEngine.Business business : economy.businesses().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_BUSINESSES_PER_PULSE).toList()) {
            if (business.locationId() != null) {
                boolean shouldOpen = LivelyApi.structures().get(business.locationId())
                        .map(SemanticStructureRegistry.Structure::state)
                        .map(state -> state == SemanticStructureRegistry.OperationalState.OPEN
                                || state == SemanticStructureRegistry.OperationalState.FESTIVAL)
                        .orElse(true);
                if (shouldOpen != business.open()) LivelyApi.economy().setOpen(business.id(), shouldOpen);
            }
            long wage = longValue(business.facts().get("wage"), 0L, 0L, 1_000_000_000L);
            if (business.open() && wage > 0L) LivelyApi.economy().payroll(business.id(), wage);
            if (business.open() && Boolean.parseBoolean(business.facts().getOrDefault("illegal", "false"))) {
                emitUnderworldRumor(business, tick);
            }
        }
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

    private static long longValue(String raw, long fallback, long min, long max) {
        try { return Math.max(min, Math.min(max, Long.parseLong(raw == null ? Long.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double doubleValue(String raw, double fallback, double min, double max) {
        try { return Math.max(min, Math.min(max, Double.parseDouble(raw == null ? Double.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
