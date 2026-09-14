package vn.svframe.svarcade.systems.presence;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

public final class PresenceSystem implements SessionSystem, PresenceAccess {
    public static final Id ID = Id.of("svarcade:presence");

    record Rule(long grace, TimeoutPolicy policy) { }
    record Config(Rule player, Rule bot, int eventCapacity) {
        static Config parse(Node n) {
            n.only("player", "bot", "event_capacity");
            return new Config(rule(n.node("player")), rule(n.node("bot")), (int)n.integer("event_capacity", 1, 100000));
        }
        private static Rule rule(Node n) {
            n.only("grace_ticks", "timeout_policy");
            TimeoutPolicy policy;
            try { policy = TimeoutPolicy.valueOf(n.string("timeout_policy").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw n.error("timeout_policy", "Unknown timeout policy"); }
            return new Rule(n.integer("grace_ticks", 0, Long.MAX_VALUE - 1), policy);
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            PresenceSystem system = new PresenceSystem(Config.parse(config), session);
            session.services().provide(ACCESS, system); return system;
        }
    }

    record Entry(boolean connected, long disconnectedAt, long deadline, TimeoutPolicy policy, boolean emitted) { }
    private final Config config;
    private final GenericSession session;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long revision, lastTick = -1;
    private boolean active, closed;

    PresenceSystem(Config config, GenericSession session) { this.config = config; this.session = session; }
    private void check() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Presence inactive"); }
    private Rule rule(Participant participant) { return participant.kind() == Participant.Kind.BOT ? config.bot() : config.player(); }

    @Override public void start() {
        session.thread().check(); if (active || closed) throw new IllegalStateException("Presence initialized");
        session.participants().values().stream().filter(p -> p.kind() != Participant.Kind.SPECTATOR)
                .forEach(p -> entries.put(p.id(), new Entry(true, -1, -1, rule(p).policy(), false)));
        active = true;
    }
    @Override public void disconnect(UUID participant, long tick) {
        check(); if (tick < 0) throw new IllegalArgumentException("Presence tick");
        Participant p = session.participants().get(participant); if (p == null || p.kind() == Participant.Kind.SPECTATOR) throw new IllegalArgumentException("Unknown participant");
        Entry before = entries.get(participant); if (before == null || !before.connected()) return;
        Rule rule = rule(p); long deadline = Math.addExact(tick, rule.grace());
        entries.put(participant, new Entry(false, tick, deadline, rule.policy(), false)); revision = Math.incrementExact(revision); session.changed();
    }
    @Override public void reconnect(UUID participant, long tick) {
        check(); if (tick < 0) throw new IllegalArgumentException("Presence tick");
        Entry before = entries.get(participant); if (before == null) throw new IllegalArgumentException("Unknown participant");
        if (before.connected()) return;
        entries.put(participant, new Entry(true, -1, -1, before.policy(), false)); revision = Math.incrementExact(revision); session.changed();
    }
    @Override public Status status(UUID participant) {
        check(); Entry entry = entries.get(participant); if (entry == null) throw new IllegalArgumentException("Unknown participant");
        return new Status(participant, entry.connected(), entry.disconnectedAt(), entry.deadline(), entry.policy());
    }
    @Override public List<Event> drainTimeouts(int maximum) {
        check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Presence drain limit");
        List<Event> result = new ArrayList<>(); while (!events.isEmpty() && result.size() < maximum) result.add(events.removeFirst()); return List.copyOf(result);
    }
    @Override public long revision() { check(); return revision; }
    @Override public void tick(long tick) {
        check(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing presence tick"); lastTick = tick;
        boolean changed = false;
        for (var row : new ArrayList<>(entries.entrySet())) {
            Entry entry = row.getValue();
            if (entry.connected() || entry.emitted() || tick < entry.deadline()) continue;
            if (events.size() >= config.eventCapacity()) throw new IllegalStateException("Presence event backpressure");
            events.addLast(new Event(row.getKey(), entry.policy()));
            entries.put(row.getKey(), new Entry(false, entry.disconnectedAt(), entry.deadline(), entry.policy(), true)); changed = true;
        }
        if (changed) { revision = Math.incrementExact(revision); session.markDirty(); }
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        check(); List<Object> rows = new ArrayList<>();
        for (var row : entries.entrySet()) {
            Entry e = row.getValue(); rows.add(Map.of("participant", row.getKey().toString(), "connected", e.connected(), "disconnected_at", e.disconnectedAt(),
                    "deadline", e.deadline(), "policy", e.policy().name(), "emitted", e.emitted()));
        }
        return Values.map(Map.of("revision", revision, "entries", rows));
    }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid presence restore");
        Node n = new Node(state, "presence-state"); n.only("revision", "entries"); revision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        for (Node row : n.nodes("entries")) {
            row.only("participant", "connected", "disconnected_at", "deadline", "policy", "emitted"); UUID id = UUID.fromString(row.string("participant"));
            Participant p = session.participants().get(id); if (p == null || p.kind() == Participant.Kind.SPECTATOR) throw new ConfigException("Presence participant mismatch");
            TimeoutPolicy policy = TimeoutPolicy.valueOf(row.string("policy")); Rule expected = rule(p); if (policy != expected.policy()) throw new ConfigException("Presence policy mismatch");
            if (entries.putIfAbsent(id, new Entry(row.bool("connected", false), row.integer("disconnected_at", -1, Long.MAX_VALUE - 1),
                    row.integer("deadline", -1, Long.MAX_VALUE - 1), policy, row.bool("emitted", false))) != null) throw new ConfigException("Duplicate presence participant");
        }
        long expectedCount = session.participants().values().stream().filter(p -> p.kind() != Participant.Kind.SPECTATOR).count();
        if (entries.size() != expectedCount) throw new ConfigException("Presence roster mismatch"); active = true;
    }
    @Override public void close() { session.thread().check(); active = false; closed = true; entries.clear(); events.clear(); }
}
