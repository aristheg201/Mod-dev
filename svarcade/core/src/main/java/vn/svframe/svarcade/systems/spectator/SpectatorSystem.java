package vn.svframe.svarcade.systems.spectator;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

public final class SpectatorSystem implements SessionSystem, SpectatorAccess {
    public static final Id ID = Id.of("svarcade:spectators");

    record Config(boolean enabled, int max, String team, int events) {
        static Config parse(Node n) {
            n.only("enabled", "max_spectators", "team", "event_capacity");
            String team = n.string("team", "spectator");
            if (team.isBlank() || team.length() > 80) throw new ConfigException("Invalid spectator team");
            return new Config(n.bool("enabled", true), (int)n.integer("max_spectators", 0, 100000), team,
                    (int)n.integer("event_capacity", 1, 100000));
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            SpectatorSystem value = new SpectatorSystem(Config.parse(config), session);
            session.services().provide(ACCESS, value);
            return value;
        }
    }

    private final Config config;
    private final GenericSession session;
    private final NavigableSet<UUID> members = new TreeSet<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long revision;
    private boolean active, closed;

    SpectatorSystem(Config config, GenericSession session) { this.config = config; this.session = session; }
    private void check() { session.thread().check(); if (!active || closed) throw new IllegalStateException("Spectators inactive"); }

    @Override public void start() {
        session.thread().check(); if (active || closed) throw new IllegalStateException("Spectators initialized");
        session.participants().values().stream().filter(p -> p.kind() == Participant.Kind.SPECTATOR).forEach(p -> members.add(p.id()));
        if ((!config.enabled() && !members.isEmpty()) || members.size() > config.max()) throw new ConfigException("Spectator capacity");
        active = true;
    }
    @Override public boolean enabled() { check(); return config.enabled(); }
    @Override public int capacity() { check(); return config.max(); }
    @Override public int size() { check(); return members.size(); }
    @Override public boolean contains(UUID player) { check(); return members.contains(player); }
    @Override public List<View> spectators() { check(); return members.stream().map(id -> new View(id, config.team())).toList(); }
    @Override public long revision() { check(); return revision; }

    @Override public StateChange prepareJoin(UUID player) {
        check(); Objects.requireNonNull(player); long expected = revision;
        if (!config.enabled() || members.size() >= config.max() || members.contains(player) || session.participants().containsKey(player))
            throw new IllegalStateException("Spectator join unavailable");
        return change(player, true, expected);
    }
    @Override public StateChange prepareLeave(UUID player) {
        check(); Objects.requireNonNull(player); long expected = revision;
        if (!members.contains(player)) throw new IllegalArgumentException("Unknown spectator");
        return change(player, false, expected);
    }
    private StateChange change(UUID player, boolean joining, long expected) {
        return new StateChange() {
            int used;
            @Override public void apply() {
                check(); if (used != 0 || revision != expected || events.size() >= config.events()) throw new IllegalStateException("Stale spectator change");
                if (joining) { session.addSpectator(player, config.team()); members.add(player); }
                else { session.removeSpectator(player); members.remove(player); }
                events.addLast(new Event(joining ? EventType.JOIN : EventType.LEAVE, player)); revision++; used = 1;
            }
            @Override public void rollback() {
                check(); Event last = events.peekLast();
                if (used != 1 || revision != expected + 1 || last == null || !last.player().equals(player)) throw new IllegalStateException("Spectator rollback conflict");
                events.removeLast();
                if (joining) { members.remove(player); session.removeSpectator(player); }
                else { session.addSpectator(player, config.team()); members.add(player); }
                revision = expected; used = 2;
            }
        };
    }
    @Override public List<Event> drainEvents(int maximum) {
        check(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Drain limit");
        List<Event> out = new ArrayList<>(); while (!events.isEmpty() && out.size() < maximum) out.add(events.removeFirst()); return List.copyOf(out);
    }
    @Override public void tick(long tick) { check(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String,Object> snapshot() {
        check(); return Values.map(Map.of("revision", revision, "members", members.stream().map(UUID::toString).toList()));
    }
    @Override public void restore(int schema, Map<String,Object> state) {
        session.thread().check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid spectator restore");
        Node n = new Node(state, "spectator-state"); n.only("revision", "members");
        revision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        List<String> raw = n.strings("members"); if (raw.size() > config.max()) throw new ConfigException("Spectator capacity");
        for (String value : raw) {
            UUID id = UUID.fromString(value); Participant existing = session.participants().get(id);
            if (existing != null && existing.kind() != Participant.Kind.SPECTATOR) throw new ConfigException("Spectator roster conflict");
            if (!members.add(id)) throw new ConfigException("Duplicate spectator");
            if (existing == null) session.addSpectator(id, config.team());
        }
        active = true;
    }
    @Override public void close() { session.thread().check(); active = false; closed = true; members.clear(); events.clear(); }
}
