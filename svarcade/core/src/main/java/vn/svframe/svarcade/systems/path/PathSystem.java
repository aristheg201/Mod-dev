package vn.svframe.svarcade.systems.path;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Deterministic movement over authored routes; no world scan or vanilla pathfinder. */
public final class PathSystem implements SessionSystem, PathAccess {
    public static final Id ID = Id.of("svarcade:path");
    public static final SessionServices.Key<PathAccess> ACCESS = new SessionServices.Key<>(ID, PathAccess.class);
    public record Config(int capacity, double maxSpeed, long maxAdvanceTicks, Map<Id, Polyline> paths) {
        public Config {
            if (capacity < 1 || capacity > 16_384 || !Double.isFinite(maxSpeed) || maxSpeed <= 0 || maxSpeed > 1_000_000
                    || maxAdvanceTicks < 1 || maxAdvanceTicks > 1_000_000 || paths.isEmpty() || paths.size() > 256) throw new ConfigException("Path system limits");
            paths = Map.copyOf(paths);
        }
        public static Config parse(Node n) {
            n.only("capacity", "max_speed", "max_advance_ticks", "paths");
            Map<Id, Polyline> paths = new LinkedHashMap<>(); Node definitions = n.node("paths");
            for (String key : definitions.values().keySet()) {
                Node path = definitions.node(key); path.only("points");
                paths.put(Id.of(key), new Polyline(path.list("points").stream().map(Vec3::parse).toList()));
            }
            return new Config((int) n.integer("capacity", 1, 16_384), Numbers.decimal(n, "max_speed", Double.MIN_NORMAL, 1_000_000),
                    n.integer("max_advance_ticks", 1, 1_000_000), paths);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            PathSystem system = new PathSystem(Config.parse(config), session.thread(), session::changed);
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final ThreadGuard thread;
    private final Runnable dirty;
    private final NavigableMap<UUID, Agent> agents = new TreeMap<>();
    private final LinkedHashSet<UUID> arrivals = new LinkedHashSet<>();
    private long elapsed, lastTick = -1;
    private boolean active, closed;
    public PathSystem(Config config, ThreadGuard thread, Runnable dirty) {
        this.config = Objects.requireNonNull(config); this.thread = Objects.requireNonNull(thread); this.dirty = Objects.requireNonNull(dirty);
    }
    private void requireActive() { thread.check(); if (!active) throw new IllegalStateException("Path system inactive"); }
    private double checkedSpeed(double value) {
        if (!Double.isFinite(value) || value < 0 || value > config.maxSpeed()) throw new IllegalArgumentException("Invalid path speed");
        return value;
    }
    private Polyline path(Id id) { Polyline p = config.paths().get(id); if (p == null) throw new IllegalArgumentException("Unknown path: " + id); return p; }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Already started or closed"); active = true; }
    @Override public void spawn(UUID id, Id route, double speed) {
        requireActive(); Objects.requireNonNull(id); path(route); checkedSpeed(speed);
        if (agents.size() >= config.capacity() || agents.containsKey(id)) throw new IllegalStateException("Path agent capacity or duplicate identity");
        agents.put(id, new Agent(id, route, 0, speed)); dirty.run();
    }
    @Override public void speed(UUID id, double speed) {
        requireActive(); checkedSpeed(speed); Agent previous = agents.get(id);
        if (previous == null) throw new IllegalArgumentException("Unknown path agent");
        if (previous.speed() == speed) return;
        agents.put(id, new Agent(id, previous.path(), previous.distance(), speed)); dirty.run();
    }
    @Override public boolean remove(UUID id) {
        requireActive(); if (agents.remove(id) == null) return false;
        arrivals.remove(id); dirty.run(); return true;
    }
    @Override public Optional<Agent> agent(UUID id) { requireActive(); return Optional.ofNullable(agents.get(id)); }
    @Override public Vec3 position(UUID id) {
        requireActive(); Agent a = agents.get(id); if (a == null) throw new IllegalArgumentException("Unknown path agent");
        return path(a.path()).at(a.distance());
    }
    @Override public double progress(UUID id) {
        requireActive(); Agent a = agents.get(id); if (a == null) throw new IllegalArgumentException("Unknown path agent");
        double length = path(a.path()).length(); return length == 0 ? 1 : a.distance() / length;
    }
    @Override public List<UUID> drainArrivals(int maximum) {
        requireActive(); if (maximum < 1) throw new IllegalArgumentException("Drain limit");
        List<UUID> result = new ArrayList<>(); Iterator<UUID> iterator = arrivals.iterator();
        while (iterator.hasNext() && result.size() < maximum) { result.add(iterator.next()); iterator.remove(); }
        if (!result.isEmpty()) dirty.run(); return List.copyOf(result);
    }
    @Override public int size() { requireActive(); return agents.size(); }
    @Override public void tick(long tick) {
        requireActive(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing path tick");
        long delta = lastTick < 0 ? 0 : tick - lastTick;
        if (delta > config.maxAdvanceTicks()) throw new IllegalArgumentException("Path advance exceeds configured limit");
        long nextElapsed = Math.addExact(elapsed, delta);
        boolean changed = false;
        if (delta > 0) for (var entry : agents.entrySet()) {
            Agent a = entry.getValue(); double length = path(a.path()).length();
            if (a.speed() == 0 || a.distance() == length) continue;
            double nextDistance = Math.min(length, a.distance() + a.speed() * delta);
            entry.setValue(new Agent(a.id(), a.path(), nextDistance, a.speed()));
            if (nextDistance == length) arrivals.add(a.id());
            changed = true;
        }
        elapsed = nextElapsed; lastTick = tick;
        if (changed || delta > 0) dirty.run();
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> state = new ArrayList<>();
        agents.values().forEach(a -> state.add(Map.of("id", a.id().toString(), "path", a.path().toString(), "distance", a.distance(), "speed", a.speed())));
        return Values.map(Map.of("elapsed", elapsed, "agents", state, "arrivals", arrivals.stream().map(UUID::toString).toList()));
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid path restore");
        Node n = new Node(saved, "path-state"); n.only("elapsed", "agents", "arrivals"); long time = n.integer("elapsed", 0, Long.MAX_VALUE);
        if (n.list("agents").size() > config.capacity()) throw new ConfigException("Persisted agent capacity");
        NavigableMap<UUID, Agent> restored = new TreeMap<>();
        for (Node row : n.nodes("agents")) {
            row.only("id", "path", "distance", "speed"); UUID id = UUID.fromString(row.string("id")); Id route = Id.of(row.string("path"));
            Agent a = new Agent(id, route, Numbers.decimal(row, "distance", 0, path(route).length()), Numbers.decimal(row, "speed", 0, config.maxSpeed()));
            if (restored.putIfAbsent(id, a) != null) throw new ConfigException("Duplicate persisted path identity");
        }
        LinkedHashSet<UUID> pending = new LinkedHashSet<>();
        for (String value : n.strings("arrivals")) {
            UUID id = UUID.fromString(value); Agent a = restored.get(id);
            if (a == null || a.distance() != path(a.path()).length() || !pending.add(id)) throw new ConfigException("Invalid persisted arrival");
        }
        agents.clear(); agents.putAll(restored); arrivals.clear(); arrivals.addAll(pending); elapsed = time; lastTick = -1; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; agents.clear(); arrivals.clear(); }
}
