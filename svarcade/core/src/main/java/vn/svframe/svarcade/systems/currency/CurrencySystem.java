package vn.svframe.svarcade.systems.currency;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Exact integral balances and sparse all-or-nothing transactions over authored currencies. */
public final class CurrencySystem implements SessionSystem, CurrencyAccess {
    public static final Id ID = Id.of("svarcade:currency");
    public static final SessionServices.Key<CurrencyAccess> ACCESS = new SessionServices.Key<>(ID, CurrencyAccess.class);
    public enum Ownership { PARTICIPANT, TEAM }
    public record Unit(long initial, long maximum) {
        public Unit { if (initial < 0 || maximum < initial) throw new ConfigException("Invalid currency limits"); }
    }
    public record Config(Ownership ownership, Map<Id, Unit> units) {
        public Config {
            Objects.requireNonNull(ownership); units = Map.copyOf(units);
            if (units.isEmpty() || units.size() > 16) throw new ConfigException("Expected 1..16 currencies");
        }
        public static Config parse(Node n) {
            n.only("ownership", "currencies"); Map<Id, Unit> units = new LinkedHashMap<>(); Node currencies = n.node("currencies");
            for (String name : currencies.values().keySet()) {
                Node unit = currencies.node(name); unit.only("initial", "maximum");
                units.put(Id.of(name), new Unit(unit.integer("initial", 0, Long.MAX_VALUE), unit.integer("maximum", 0, Long.MAX_VALUE)));
            }
            return new Config(Ownership.valueOf(n.string("ownership")), units);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            CurrencySystem system = new CurrencySystem(Config.parse(config), session.participants().values(), session.thread());
            session.services().provide(ACCESS, system); return system;
        }
    }
    private record Key(String account, Id currency) implements Comparable<Key> {
        @Override public int compareTo(Key other) { int c = account.compareTo(other.account); return c != 0 ? c : currency.compareTo(other.currency); }
    }
    private final Config config;
    private final ThreadGuard thread;
    private final Map<UUID, String> accounts;
    private final NavigableMap<Key, Long> balances = new TreeMap<>();
    private long revision;
    private boolean active, closed;
    public CurrencySystem(Config config, Collection<Participant> participants, ThreadGuard thread) {
        this.config = Objects.requireNonNull(config); this.thread = Objects.requireNonNull(thread);
        if (participants.size() > 4096) throw new IllegalArgumentException("Currency participant capacity");
        Map<UUID, String> accounts = new LinkedHashMap<>();
        for (Participant p : participants) {
            if (p.kind() == Participant.Kind.SPECTATOR) continue;
            if (p.team().isBlank() || p.team().length() > 160) throw new IllegalArgumentException("Invalid team identifier");
            String account = config.ownership() == Ownership.PARTICIPANT ? "participant/" + p.id() : "team/" + p.team();
            if (accounts.putIfAbsent(p.id(), account) != null) throw new IllegalArgumentException("Duplicate currency participant");
        }
        if (new HashSet<>(accounts.values()).size() * config.units().size() > 16_384) throw new IllegalArgumentException("Currency account capacity");
        this.accounts = Map.copyOf(accounts);
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Currency system inactive"); }
    private Key key(UUID actor, Id currency) {
        String account = accounts.get(actor);
        if (account == null || !config.units().containsKey(currency)) throw new IllegalArgumentException("Unknown currency or account owner");
        return new Key(account, currency);
    }
    private NavigableMap<Key, Long> initial() {
        NavigableMap<Key, Long> state = new TreeMap<>();
        for (String account : new TreeSet<>(accounts.values())) config.units().forEach((currency, unit) -> state.put(new Key(account, currency), unit.initial()));
        return state;
    }
    @Override public void start() {
        thread.check(); if (active || closed) throw new IllegalStateException("Currency system already started/closed");
        balances.putAll(initial()); active = true;
    }
    @Override public long balance(UUID actor, Id currency) { requireActive(); return balances.get(key(actor, currency)); }
    @Override public long revision() { requireActive(); return revision; }
    @Override public Change prepare(List<Delta> deltas) {
        requireActive(); if (deltas.isEmpty() || deltas.size() > 256) throw new IllegalArgumentException("Expected 1..256 currency deltas");
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Currency revision exhausted");
        Map<Key, Long> net = new TreeMap<>();
        for (Delta delta : deltas) net.merge(key(delta.actor(), delta.currency()), delta.amount(), Math::addExact);
        Map<Key, Long> before = new TreeMap<>(), after = new TreeMap<>();
        for (var change : net.entrySet()) {
            Key key = change.getKey(); long old = balances.get(key); long next = Math.addExact(old, change.getValue());
            if (next < 0 || next > config.units().get(key.currency()).maximum()) throw new IllegalArgumentException("Currency funds or balance limit");
            before.put(key, old); after.put(key, next);
        }
        return new Prepared(revision, Map.copyOf(before), Map.copyOf(after));
    }
    /** The dispatcher publishes session revision/effects only after all owned changes succeed. */
    private final class Prepared implements Change {
        private final long expected;
        private final Map<Key, Long> before, after;
        private int state;
        private Prepared(long expected, Map<Key, Long> before, Map<Key, Long> after) { this.expected = expected; this.before = before; this.after = after; }
        @Override public void apply() {
            requireActive(); if (state != 0 || revision != expected) throw new IllegalStateException("Stale or reused currency transaction");
            after.forEach(balances::put); revision = expected + 1; state = 1;
        }
        @Override public void rollback() {
            requireActive(); if (state != 1 || revision != expected + 1) throw new IllegalStateException("Currency rollback conflicts with another transaction");
            before.forEach(balances::put); revision = expected; state = 2;
        }
    }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); Map<String, Object> state = new TreeMap<>();
        for (String account : new TreeSet<>(accounts.values())) {
            Map<String, Object> values = new TreeMap<>();
            config.units().keySet().forEach(currency -> values.put(currency.toString(), balances.get(new Key(account, currency))));
            state.put(account, values);
        }
        return Values.map(Map.of("revision", revision, "balances", state));
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid currency restore");
        Node n = new Node(saved, "currency-state"); n.only("revision", "balances"); long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        Node values = n.node("balances");
        if (!values.values().keySet().equals(new HashSet<>(accounts.values()))) throw new ConfigException("Persisted currency account mismatch");
        Set<String> currencyIds = new HashSet<>(); config.units().keySet().forEach(id -> currencyIds.add(id.toString()));
        NavigableMap<Key, Long> restored = new TreeMap<>();
        for (String account : values.values().keySet()) {
            Node row = values.node(account);
            if (!row.values().keySet().equals(currencyIds)) throw new ConfigException("Persisted currency set mismatch");
            config.units().forEach((id, unit) -> restored.put(new Key(account, id), row.integer(id.toString(), 0, unit.maximum())));
        }
        balances.clear(); balances.putAll(restored); revision = restoredRevision; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; balances.clear(); }
}
