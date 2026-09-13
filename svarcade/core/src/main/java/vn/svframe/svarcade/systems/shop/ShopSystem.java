package vn.svframe.svarcade.systems.shop;

import java.util.*;
import java.util.function.BooleanSupplier;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.shop.ShopAccess.*;

/** Bounded match-local shop with phase gating, stock, caps and expiring typed grants. */
public final class ShopSystem implements SessionSystem, ShopAccess {
    public static final Id ID = Id.of("svarcade:shop");
    public record Config(Id currency, Set<String> allowedStates, Map<Id, Item> items, int maxActiveGrants) {
        public Config {
            Objects.requireNonNull(currency); allowedStates = Set.copyOf(allowedStates); items = Map.copyOf(items);
            if (items.isEmpty() || items.size() > 4096 || maxActiveGrants < 1 || maxActiveGrants > 100_000) throw new ConfigException("Shop definition limits");
        }
        public static Config parse(Node n) {
            n.only("currency", "allowed_states", "max_active_grants", "items"); Set<String> states = n.has("allowed_states") ? n.strings("allowed_states") : Set.of();
            for (String state : states) if (!state.matches("[A-Za-z0-9_-]{1,80}")) throw n.error("allowed_states", "Invalid state identifier");
            Map<Id, Item> items = new LinkedHashMap<>(); Node values = n.node("items");
            for (String raw : values.values().keySet()) {
                Node i = values.node(raw); i.only("cost", "stock", "per_actor", "duration_ticks", "effect", "payload"); Id id = Id.of(raw);
                Item item = new Item(id, i.integer("cost", 0, Long.MAX_VALUE), (int) i.integer("stock", 0, 1_000_000),
                        (int) i.integer("per_actor", 0, 1_000_000), i.integer("duration_ticks", 0, Long.MAX_VALUE - 1), Id.of(i.string("effect")),
                        i.has("payload") ? i.node("payload").values() : Map.of());
                if (items.putIfAbsent(id, item) != null) throw new ConfigException("Duplicate shop item: " + id);
            }
            return new Config(Id.of(n.string("currency")), states, items, (int) n.integer("max_active_grants", 1, 100_000));
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies(Node config) {
            Config parsed = Config.parse(config); return parsed.allowedStates().isEmpty() ? Set.of(CurrencySystem.ID) : Set.of(CurrencySystem.ID, StateMachineSystem.ID);
        }
        @Override public Set<SessionServices.Key<?>> requires(Node config) {
            Config parsed = Config.parse(config); return parsed.allowedStates().isEmpty() ? Set.of(CurrencySystem.ACCESS) : Set.of(CurrencySystem.ACCESS, StateMachineAccess.ACCESS);
        }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            Config parsed = Config.parse(config); BooleanSupplier phase = parsed.allowedStates().isEmpty() ? () -> true : () -> parsed.allowedStates().contains(session.services().require(StateMachineAccess.ACCESS).state());
            ShopSystem system = new ShopSystem(parsed, session.services().require(CurrencySystem.ACCESS), session, phase);
            session.services().provide(ACCESS, system); return system;
        }
    }
    private record StoredGrant(long id, UUID actor, Id item, Id effect, Map<String, Object> payload, long expiry) {
        private StoredGrant { payload = Map.copyOf(payload); }
    }
    private record ActorItem(UUID actor, Id item) { }
    private final Config config;
    private final CurrencyAccess currency;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final BooleanSupplier phase;
    private final NavigableMap<Long, StoredGrant> grants = new TreeMap<>();
    private final Map<Id, Integer> sold = new HashMap<>();
    private final Map<ActorItem, Integer> bought = new HashMap<>();
    private long nextId = 1, elapsed, lastTick = -1, revision;
    private boolean active, closed;
    private ShopSystem(Config config, CurrencyAccess currency, GenericSession session, BooleanSupplier phase) {
        this.config = config; this.currency = currency; this.session = session; thread = session.thread(); this.phase = phase;
        for (Participant p : session.participants().values()) if (p.kind() != Participant.Kind.SPECTATOR) currency.balance(p.id(), config.currency());
    }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Shop inactive"); }
    private void requireActor(UUID actor) {
        Participant participant = session.participants().get(actor);
        if (participant == null || participant.kind() == Participant.Kind.SPECTATOR) throw new IllegalArgumentException("Invalid shop actor");
    }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Shop already initialized"); active = true; }
    @Override public Map<Id, Item> items() { requireActive(); return config.items(); }
    @Override public int sold(Id item) { requireActive(); if (!config.items().containsKey(item)) throw new IllegalArgumentException("Unknown shop item"); return sold.getOrDefault(item, 0); }
    @Override public List<Grant> grants(UUID actor) {
        requireActive(); requireActor(actor); List<Grant> result = new ArrayList<>();
        for (StoredGrant grant : grants.values()) if (grant.actor().equals(actor)) result.add(view(grant)); return List.copyOf(result);
    }
    private Grant view(StoredGrant grant) {
        long remaining = grant.expiry() < 0 ? -1 : Math.max(0, grant.expiry() - elapsed);
        return new Grant(grant.id(), grant.actor(), grant.item(), grant.effect(), grant.payload(), remaining);
    }
    @Override public long revision() { requireActive(); return revision; }
    @Override public StateChange preparePurchase(UUID actor, Id itemId) {
        requireActive(); requireActor(actor); if (!phase.getAsBoolean()) throw new IllegalStateException("Shop unavailable in current state");
        Item item = config.items().get(itemId); if (item == null) throw new IllegalArgumentException("Unknown shop item");
        int soldBefore = sold.getOrDefault(itemId, 0), boughtBefore = bought.getOrDefault(new ActorItem(actor, itemId), 0);
        if (item.stock() > 0 && soldBefore >= item.stock() || item.perActor() > 0 && boughtBefore >= item.perActor() || grants.size() >= config.maxActiveGrants()
                || nextId == Long.MAX_VALUE || revision == Long.MAX_VALUE) throw new IllegalStateException("Shop purchase cap reached");
        CurrencyAccess.Change payment = currency.prepare(List.of(new CurrencyAccess.Delta(actor, config.currency(), -item.cost())));
        long id = nextId, expected = revision, expiry = item.durationTicks() == 0 ? -1 : Math.addExact(elapsed, item.durationTicks()); ActorItem actorItem = new ActorItem(actor, itemId);
        StateChange local = new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || nextId != id || !phase.getAsBoolean()
                        || sold.getOrDefault(itemId, 0) != soldBefore || bought.getOrDefault(actorItem, 0) != boughtBefore) throw new IllegalStateException("Stale shop purchase");
                grants.put(id, new StoredGrant(id, actor, itemId, item.effect(), item.payload(), expiry)); sold.put(itemId, soldBefore + 1); bought.put(actorItem, boughtBefore + 1);
                nextId++; revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || grants.get(id) == null) throw new IllegalStateException("Shop rollback conflict");
                grants.remove(id); restoreCount(sold, itemId, soldBefore); restoreCount(bought, actorItem, boughtBefore); nextId = id; revision = expected; state = 2;
            }
        };
        return new CompositeChange(thread, List.of(payment, local));
    }
    private static <K> void restoreCount(Map<K, Integer> map, K key, int value) { if (value == 0) map.remove(key); else map.put(key, value); }
    @Override public void tick(long tick) {
        requireActive(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing shop tick");
        if (lastTick >= 0) elapsed = Math.addExact(elapsed, tick - lastTick); lastTick = tick;
        boolean removed = grants.values().removeIf(grant -> grant.expiry() >= 0 && grant.expiry() <= elapsed);
        if (removed) { revision = Math.incrementExact(revision); session.markDirty(); }
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> activeGrants = new ArrayList<>();
        for (StoredGrant grant : grants.values()) activeGrants.add(Map.of("id", grant.id(), "actor", grant.actor().toString(), "item", grant.item().toString(),
                "remaining", grant.expiry() < 0 ? -1L : Math.max(0, grant.expiry() - elapsed)));
        Map<String, Object> soldState = new TreeMap<>(); sold.forEach((id, count) -> soldState.put(id.toString(), count));
        List<Object> boughtState = new ArrayList<>(); bought.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().actor().toString() + e.getKey().item())).forEach(entry ->
                boughtState.add(Map.of("actor", entry.getKey().actor().toString(), "item", entry.getKey().item().toString(), "count", entry.getValue())));
        return Values.map(Map.of("revision", revision, "next_id", nextId, "sold", soldState, "bought", boughtState, "grants", activeGrants));
    }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid shop restore"); Node n = new Node(state, "shop-state");
        n.only("revision", "next_id", "sold", "bought", "grants"); long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1), restoredNext = n.integer("next_id", 1, Long.MAX_VALUE);
        Map<Id, Integer> restoredSold = new HashMap<>(); Node soldNode = n.node("sold");
        for (String raw : soldNode.values().keySet()) { Id id = Id.of(raw); Item item = config.items().get(id); if (item == null) throw new ConfigException("Unknown restored shop item"); int count = (int) soldNode.integer(raw, 0, 1_000_000); if (item.stock() > 0 && count > item.stock()) throw new ConfigException("Restored stock exceeded"); restoredSold.put(id, count); }
        Map<ActorItem, Integer> restoredBought = new HashMap<>();
        for (Node row : n.nodes("bought")) {
            row.only("actor", "item", "count"); UUID actor = UUID.fromString(row.string("actor")); requireActor(actor); Id id = Id.of(row.string("item")); Item item = config.items().get(id); if (item == null) throw new ConfigException("Unknown restored shop item");
            int count = (int) row.integer("count", 1, 1_000_000); if (item.perActor() > 0 && count > item.perActor() || restoredBought.putIfAbsent(new ActorItem(actor, id), count) != null) throw new ConfigException("Invalid restored purchase cap");
        }
        NavigableMap<Long, StoredGrant> restoredGrants = new TreeMap<>();
        for (Node row : n.nodes("grants")) {
            row.only("id", "actor", "item", "remaining"); long id = row.integer("id", 1, Long.MAX_VALUE - 1); UUID actor = UUID.fromString(row.string("actor")); requireActor(actor);
            Id itemId = Id.of(row.string("item")); Item item = config.items().get(itemId); if (item == null) throw new ConfigException("Unknown restored grant item"); long remaining = row.integer("remaining", -1, Long.MAX_VALUE - 1);
            if (remaining == 0 || item.durationTicks() == 0 != (remaining == -1)) throw new ConfigException("Invalid restored grant duration"); long expiry = remaining < 0 ? -1 : remaining;
            if (restoredGrants.putIfAbsent(id, new StoredGrant(id, actor, itemId, item.effect(), item.payload(), expiry)) != null) throw new ConfigException("Duplicate restored grant");
        }
        if (restoredGrants.size() > config.maxActiveGrants() || !restoredGrants.isEmpty() && restoredGrants.lastKey() >= restoredNext) throw new ConfigException("Invalid restored grant bounds");
        sold.putAll(restoredSold); bought.putAll(restoredBought); grants.putAll(restoredGrants); nextId = restoredNext; revision = restoredRevision; elapsed = 0; lastTick = -1; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; grants.clear(); sold.clear(); bought.clear(); }
}
