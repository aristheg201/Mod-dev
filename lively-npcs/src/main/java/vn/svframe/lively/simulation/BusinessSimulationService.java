package vn.svframe.lively.simulation;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
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
        discoverBusinesses();
        staffBusinesses();
        operateBusinesses(tick);
    }

    private void discoverBusinesses() {
        Set<ActorId> owners = new HashSet<>();
        LivelyApi.economy().snapshot().businesses().values().forEach(b -> owners.add(b.owner()));
        int created = 0;
        for (NpcDefinition npc : LivelyApi.npcs().snapshot().values().stream().sorted(Comparator.comparing(n -> n.id().toString())).toList()) {
            if (created >= MAX_BUSINESSES_PER_PULSE) break;
            String name = npc.metadata().get("business.name");
            if (name == null || name.isBlank()) continue;
            ActorId owner = new ActorId(npc.id(), ActorId.Kind.NPC);
            if (owners.contains(owner)) continue;
            String kind = npc.metadata().getOrDefault("business.kind", "shop").trim().toLowerCase(java.util.Locale.ROOT);
            boolean blackMarket = kind.equals("black_market") || kind.equals("underworld") || Boolean.parseBoolean(npc.metadata().getOrDefault("business.illegal", "false"));
            String location = npc.metadata().getOrDefault("business.location", npc.metadata().get("work.structure"));
            EconomyEngine.Business business = LivelyApi.economy().createBusiness(owner, name, location,
                    Map.of("kind", kind,
                            "wage", npc.metadata().getOrDefault("business.wage", "0"),
                            "auto_hire", npc.metadata().getOrDefault("business.auto_hire", blackMarket ? "false" : "true"),
                            "hidden", npc.metadata().getOrDefault("business.hidden", Boolean.toString(blackMarket)),
                            "access_trust", npc.metadata().getOrDefault("business.access_trust", blackMarket ? "0.35" : "0"),
                            "risk", npc.metadata().getOrDefault("business.risk", blackMarket ? "0.65" : "0"),
                            "illegal", Boolean.toString(blackMarket)));
            long initial = longValue(npc.metadata().get("business.initial_balance"), 0L, 0L, 10_000_000_000_000L);
            LivelyApi.economy().ensureWallet(owner, initial);
            bootstrapStock(business, npc.metadata());
            owners.add(owner); created++;
        }
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
                double demand = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.5D;
                double supply = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : 0.5D;
                LivelyApi.economy().setStock(business.id(), item, quantity, target, price, demand, supply);
            } catch (NumberFormatException ignored) { }
        }
    }

    private void staffBusinesses() {
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        for (EconomyEngine.Business business : economy.businesses().values().stream().limit(MAX_BUSINESSES_PER_PULSE).toList()) {
            if (!Boolean.parseBoolean(business.facts().getOrDefault("auto_hire", "true")) || business.employees().size() >= 16) continue;
            for (NpcDefinition candidate : LivelyApi.npcs().snapshot().values()) {
                ActorId actor = new ActorId(candidate.id(), ActorId.Kind.NPC);
                if (actor.equals(business.owner()) || business.employees().contains(actor)) continue;
                String workplace = candidate.metadata().get("work.structure");
                if (workplace == null || !workplace.equals(business.locationId())) continue;
                boolean employedElsewhere = economy.businesses().values().stream().anyMatch(b -> b.employees().contains(actor));
                if (employedElsewhere) continue;
                LivelyApi.economy().assignEmployee(business.id(), actor);
                if (LivelyApi.economy().business(business.id()).map(b -> b.employees().size() >= 16).orElse(true)) break;
            }
        }
    }

    private void operateBusinesses(long tick) {
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        for (EconomyEngine.Business business : economy.businesses().values().stream().limit(MAX_BUSINESSES_PER_PULSE).toList()) {
            if (business.locationId() != null) {
                boolean shouldOpen = LivelyApi.structures().get(business.locationId())
                        .map(SemanticStructureRegistry.Structure::state)
                        .map(state -> state == SemanticStructureRegistry.OperationalState.OPEN || state == SemanticStructureRegistry.OperationalState.FESTIVAL)
                        .orElse(true);
                if (shouldOpen != business.open()) LivelyApi.economy().setOpen(business.id(), shouldOpen);
            }
            long wage = longValue(business.facts().get("wage"), 0L, 0L, 1_000_000_000L);
            if (business.open() && wage > 0L) LivelyApi.economy().payroll(business.id(), wage);
            if (business.open() && Boolean.parseBoolean(business.facts().getOrDefault("illegal", "false"))) emitUnderworldRumor(business, tick);
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
