package vn.svframe.svarcade.systems.interaction;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.*;

/** Durable interaction bindings. Platform adapters derive click facts and route intents through ActionAccess. */
public final class InteractionSystem implements SessionSystem, InteractionAccess {
    public static final Id ID = Id.of("svarcade:interactions");
    record Config(int capacity, double coordinateLimit, double maxRange) {
        static Config parse(Node n) {
            n.only("capacity", "coordinate_limit", "max_range");
            return new Config((int)n.integer("capacity", 1, 100000), Numbers.decimal(n, "coordinate_limit", 1, 30000000), Numbers.decimal(n, "max_range", 0, 1000000));
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(ActionSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(ActionSystem.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            InteractionSystem value = new InteractionSystem(Config.parse(config), session.services().require(ActionSystem.ACCESS), session);
            session.services().provide(ACCESS, value); return value;
        }
    }
    private final Config config;
    private final ActionAccess actions;
    private final GenericSession session;
    private final NavigableMap<UUID,Binding> bindings = new TreeMap<>();
    private long revision;
    private boolean active, closed;
    InteractionSystem(Config config, ActionAccess actions, GenericSession session) { this.config = config; this.actions = actions; this.session = session; }
    private void check() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Interactions inactive"); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Interactions initialized"); active = true; }
    private void validate(Point point, double range) {
        if (Math.abs(point.x()) > config.coordinateLimit() || Math.abs(point.y()) > config.coordinateLimit() || Math.abs(point.z()) > config.coordinateLimit() || range > config.maxRange())
            throw new IllegalArgumentException("Interaction bounds");
    }
    @Override public StateChange prepareBind(UUID object, Id action, UUID owner, Point position, double range, Set<Id> tags) {
        check(); Objects.requireNonNull(object); Objects.requireNonNull(action); Objects.requireNonNull(position); Objects.requireNonNull(tags); validate(position, range);
        if (!actions.actions().contains(action)) throw new IllegalArgumentException("Unknown interaction action"); Binding before = bindings.get(object);
        if (before == null && bindings.size() >= config.capacity() || revision == Long.MAX_VALUE) throw new IllegalStateException("Interaction capacity");
        long expected = revision, version = before == null ? 0 : Math.addExact(before.version(), 1); Binding after = new Binding(object, action, owner, position, range, tags, version);
        return replacement(expected, before, after);
    }
    @Override public StateChange prepareRemove(UUID object) {
        check(); Binding before = bindings.get(Objects.requireNonNull(object)); if (before == null) throw new IllegalArgumentException("Unknown interaction"); return replacement(revision, before, null);
    }
    private StateChange replacement(long expected, Binding before, Binding after) {
        UUID id = before != null ? before.object() : after.object();
        return new StateChange() {
            int used;
            @Override public void apply() {
                check(); if (used != 0 || revision != expected || !Objects.equals(bindings.get(id), before)) throw new IllegalStateException("Stale interaction change");
                if (after == null) bindings.remove(id); else bindings.put(id, after); revision++; used = 1;
            }
            @Override public void rollback() {
                check(); if (used != 1 || revision != expected + 1 || !Objects.equals(bindings.get(id), after)) throw new IllegalStateException("Interaction rollback conflict");
                if (before == null) bindings.remove(id); else bindings.put(id, before); revision = expected; used = 2;
            }
        };
    }
    @Override public Optional<Binding> binding(UUID object) { check(); return Optional.ofNullable(bindings.get(object)); }
    @Override public List<Binding> bindings(int maximum) { check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Interaction list limit"); return bindings.values().stream().limit(maximum).toList(); }
    @Override public long revision() { check(); return revision; }
    @Override public void tick(long tick) { check(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        check(); List<Object> rows = new ArrayList<>(); for (Binding b : bindings.values()) { Map<String,Object> row = new LinkedHashMap<>(); row.put("object", b.object().toString()); row.put("action", b.action().toString());
            if (b.owner() != null) row.put("owner", b.owner().toString()); row.put("x", b.position().x()); row.put("y", b.position().y()); row.put("z", b.position().z()); row.put("range", b.range()); row.put("tags", b.tags().stream().map(Id::toString).toList()); row.put("version", b.version()); rows.add(row); }
        return Values.map(Map.of("revision", revision, "bindings", rows));
    }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid interaction restore"); Node n = new Node(state, "interaction-state"); n.only("revision", "bindings"); revision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        List<Node> rows = n.nodes("bindings"); if (rows.size() > config.capacity()) throw new ConfigException("Interaction capacity");
        for (Node row : rows) {
            row.only("object", "action", "owner", "x", "y", "z", "range", "tags", "version"); UUID object = UUID.fromString(row.string("object")); Id action = Id.of(row.string("action")); if (!actions.actions().contains(action)) throw new ConfigException("Unknown restored interaction action");
            Point position = new Point(Numbers.decimal(row, "x", -config.coordinateLimit(), config.coordinateLimit()), Numbers.decimal(row, "y", -config.coordinateLimit(), config.coordinateLimit()), Numbers.decimal(row, "z", -config.coordinateLimit(), config.coordinateLimit())); double range = Numbers.decimal(row, "range", 0, config.maxRange());
            Set<Id> tags = new LinkedHashSet<>(); row.strings("tags").forEach(raw -> tags.add(Id.of(raw))); UUID owner = row.has("owner") ? UUID.fromString(row.string("owner")) : null; Binding b = new Binding(object, action, owner, position, range, tags, row.integer("version", 0, Long.MAX_VALUE - 1));
            if (bindings.putIfAbsent(object, b) != null) throw new ConfigException("Duplicate restored interaction");
        }
        active = true;
    }
    @Override public void close() { session.thread().check(); active = false; closed = true; bindings.clear(); }
}
