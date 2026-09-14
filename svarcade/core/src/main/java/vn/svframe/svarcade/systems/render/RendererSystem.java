package vn.svframe.svarcade.systems.render;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Durable logical render objects. Platform adapters consume bounded events and own actual entities/packets. */
public final class RendererSystem implements SessionSystem, RendererAccess {
    public static final Id ID = Id.of("svarcade:renderer");
    record Config(int capacity, int eventCapacity, Set<Id> profiles) {
        static Config parse(Node n) {
            n.only("capacity", "event_capacity", "profiles"); Set<Id> profiles = new LinkedHashSet<>(); n.strings("profiles").forEach(raw -> profiles.add(Id.of(raw)));
            if (profiles.isEmpty() || profiles.size() > 10000) throw new ConfigException("Renderer profiles");
            return new Config((int)n.integer("capacity", 1, 100000), (int)n.integer("event_capacity", 1, 100000), Set.copyOf(profiles));
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            RendererSystem system = new RendererSystem(Config.parse(config), session); session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final GenericSession session;
    private final NavigableMap<UUID,ObjectView> objects = new TreeMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long revision;
    private boolean active, closed;
    RendererSystem(Config config, GenericSession session) { this.config = config; this.session = session; }
    private void check() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Renderer inactive"); }
    @Override public void start() { session.thread().check(); if (active || closed) throw new IllegalStateException("Renderer initialized"); active = true; }
    @Override public StateChange prepareUpsert(UUID id, Id profile, Transform transform, Map<String,Object> data) {
        check(); Objects.requireNonNull(id); Objects.requireNonNull(profile); Objects.requireNonNull(transform); Map<String,Object> frozen = Values.map(data);
        if (!config.profiles().contains(profile)) throw new IllegalArgumentException("Unknown renderer profile"); ObjectView before = objects.get(id);
        if (before == null && objects.size() >= config.capacity() || events.size() >= config.eventCapacity() || revision == Long.MAX_VALUE) throw new IllegalStateException("Renderer capacity");
        long expected = revision, version = before == null ? 0 : Math.addExact(before.version(), 1); ObjectView after = new ObjectView(id, profile, transform, frozen, version);
        return change(expected, before, after, EventType.UPSERT);
    }
    @Override public StateChange prepareRemove(UUID id) {
        check(); ObjectView before = objects.get(Objects.requireNonNull(id)); if (before == null) throw new IllegalArgumentException("Unknown render object");
        if (events.size() >= config.eventCapacity() || revision == Long.MAX_VALUE) throw new IllegalStateException("Renderer event capacity"); return change(revision, before, null, EventType.REMOVE);
    }
    private StateChange change(long expected, ObjectView before, ObjectView after, EventType type) {
        UUID id = before != null ? before.id() : after.id(); ObjectView eventView = after != null ? after : before;
        return new StateChange() {
            int used;
            @Override public void apply() {
                check(); if (used != 0 || revision != expected || !Objects.equals(objects.get(id), before)) throw new IllegalStateException("Stale render change");
                if (after == null) objects.remove(id); else objects.put(id, after); events.addLast(new Event(type, eventView)); revision++; used = 1;
            }
            @Override public void rollback() {
                check(); Event last = events.peekLast(); if (used != 1 || revision != expected + 1 || last == null || !last.object().id().equals(id)) throw new IllegalStateException("Render rollback conflict");
                events.removeLast(); if (before == null) objects.remove(id); else objects.put(id, before); revision = expected; used = 2;
            }
        };
    }
    @Override public Optional<ObjectView> object(UUID id) { check(); return Optional.ofNullable(objects.get(id)); }
    @Override public List<ObjectView> objects(int maximum) { check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Render list limit"); return objects.values().stream().limit(maximum).toList(); }
    @Override public List<Event> drainEvents(int maximum) { check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Render drain limit"); List<Event> out = new ArrayList<>(); while (!events.isEmpty() && out.size() < maximum) out.add(events.removeFirst()); return List.copyOf(out); }
    @Override public long revision() { check(); return revision; }
    @Override public void tick(long tick) { check(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        check(); List<Object> rows = new ArrayList<>(); for (ObjectView object : objects.values()) rows.add(Map.of("id", object.id().toString(), "profile", object.profile().toString(),
                "x", object.transform().x(), "y", object.transform().y(), "z", object.transform().z(), "yaw", object.transform().yaw(), "pitch", object.transform().pitch(), "data", object.data(), "version", object.version()));
        return Values.map(Map.of("revision", revision, "objects", rows));
    }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid renderer restore"); Node n = new Node(state, "renderer-state"); n.only("revision", "objects");
        revision = n.integer("revision", 0, Long.MAX_VALUE - 1); List<Node> rows = n.nodes("objects"); if (rows.size() > config.capacity()) throw new ConfigException("Renderer capacity");
        for (Node row : rows) {
            row.only("id", "profile", "x", "y", "z", "yaw", "pitch", "data", "version"); UUID id = UUID.fromString(row.string("id")); Id profile = Id.of(row.string("profile"));
            if (!config.profiles().contains(profile)) throw new ConfigException("Unknown restored renderer profile"); Transform transform = new Transform(Numbers.decimal(row, "x", -30_000_000, 30_000_000), Numbers.decimal(row, "y", -30_000_000, 30_000_000), Numbers.decimal(row, "z", -30_000_000, 30_000_000), (float)Numbers.decimal(row, "yaw", -360, 360), (float)Numbers.decimal(row, "pitch", -360, 360));
            ObjectView object = new ObjectView(id, profile, transform, row.node("data").values(), row.integer("version", 0, Long.MAX_VALUE - 1)); if (objects.putIfAbsent(id, object) != null) throw new ConfigException("Duplicate render object");
        }
        active = true;
    }
    @Override public void close() { session.thread().check(); active = false; closed = true; objects.clear(); events.clear(); }
}
