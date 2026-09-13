package vn.svframe.svarcade.systems.wave;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.wave.WaveAccess.*;

/** Bounded deterministic wave scheduler with durable spawn/clear acknowledgement. */
public final class WaveSystem implements SessionSystem, WaveAccess {
    public static final Id ID = Id.of("svarcade:waves");
    public record Config(List<Wave> waves, int repeatFrom, int maxSpawnsPerTick, int maxPendingSpawns) {
        public Config {
            waves = List.copyOf(waves);
            if (waves.isEmpty() || waves.size() > 10_000 || repeatFrom < -1 || repeatFrom >= waves.size()
                    || maxSpawnsPerTick < 1 || maxSpawnsPerTick > 100_000 || maxPendingSpawns < 1 || maxPendingSpawns > 1_000_000)
                throw new ConfigException("Wave definition limits");
            Set<Id> ids = new HashSet<>();
            for (Wave wave : waves) if (!ids.add(wave.id())) throw new ConfigException("Duplicate wave id: " + wave.id());
        }
        public static Config parse(Node n) {
            n.only("repeat_from", "max_spawns_per_tick", "max_pending_spawns", "waves"); List<Wave> waves = new ArrayList<>();
            for (Node w : n.nodes("waves")) {
                w.only("id", "groups", "clear_reward", "shop"); List<Group> groups = new ArrayList<>();
                for (Node g : w.nodes("groups")) {
                    g.only("enemy", "count", "interval_ticks", "lane", "modifiers", "tags"); Set<Id> modifiers = ids(g, "modifiers"), tags = ids(g, "tags");
                    groups.add(new Group(Id.of(g.string("enemy")), (int) g.integer("count", 1, 1_000_000), g.integer("interval_ticks", 1, 1_000_000),
                            Id.of(g.string("lane")), modifiers, tags));
                }
                if (groups.isEmpty() || groups.size() > 1024) throw w.error("groups", "Expected 1..1024 wave groups");
                waves.add(new Wave(Id.of(w.string("id")), groups, w.has("clear_reward") ? w.node("clear_reward").values() : Map.of(),
                        w.has("shop") ? w.node("shop").values() : Map.of()));
            }
            int repeat = (int) n.integer("repeat_from", -1, waves.isEmpty() ? -1 : waves.size() - 1);
            return new Config(waves, repeat, (int) n.integer("max_spawns_per_tick", 1, 100_000), (int) n.integer("max_pending_spawns", 1, 1_000_000));
        }
        private static Set<Id> ids(Node n, String field) {
            if (!n.has(field)) return Set.of(); Set<Id> result = new LinkedHashSet<>(); n.strings(field).forEach(raw -> result.add(Id.of(raw))); return Set.copyOf(result);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            WaveSystem system = new WaveSystem(Config.parse(config), session); session.services().provide(ACCESS, system); return system;
        }
    }
    private static final class GroupState {
        int emitted;
        long nextDue;
        GroupState(int emitted, long nextDue) { this.emitted = emitted; this.nextDue = nextDue; }
        GroupState copy() { return new GroupState(emitted, nextDue); }
    }
    private final Config config;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final List<GroupState> groups = new ArrayList<>();
    private final NavigableMap<Long, Spawn> pending = new TreeMap<>(), outstanding = new TreeMap<>();
    private int nextWave, activeWave = -1;
    private long nextSequence = 1, elapsed, lastTick = -1, revision;
    private Clear clear;
    private boolean active, closed;
    private WaveSystem(Config config, GenericSession session) { this.config = config; this.session = session; thread = session.thread(); }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Waves inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Waves already initialized"); active = true; }
    @Override public List<Wave> waves() { requireActive(); return config.waves(); }
    @Override public int nextWaveIndex() { requireActive(); return nextWave; }
    @Override public OptionalInt activeWaveIndex() { requireActive(); return activeWave < 0 ? OptionalInt.empty() : OptionalInt.of(activeWave); }
    @Override public boolean complete() { requireActive(); return config.repeatFrom() < 0 && nextWave >= config.waves().size() && activeWave < 0; }
    @Override public long revision() { requireActive(); return revision; }
    @Override public StateChange prepareStartNext() {
        requireActive(); if (activeWave >= 0 || clear != null || complete() || revision == Long.MAX_VALUE) throw new IllegalStateException("Wave start unavailable");
        int waveIndex = nextWave; long expected = revision;
        return new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || activeWave >= 0 || nextWave != waveIndex) throw new IllegalStateException("Stale wave start");
                activeWave = waveIndex; groups.clear(); for (int i = 0; i < config.waves().get(waveIndex).groups().size(); i++) groups.add(new GroupState(0, elapsed)); revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || activeWave != waveIndex || !pending.isEmpty() || !outstanding.isEmpty()) throw new IllegalStateException("Wave start rollback conflict");
                activeWave = -1; groups.clear(); revision = expected; state = 2;
            }
        };
    }
    @Override public List<Spawn> pendingSpawns(int maximum) {
        requireActive(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Spawn page limit");
        return List.copyOf(pending.values().stream().limit(maximum).toList());
    }
    @Override public StateChange prepareSpawned(long sequence) {
        requireActive(); Spawn spawn = pending.get(sequence); if (spawn == null || outstanding.containsKey(sequence) || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Unknown pending spawn");
        long expected = revision;
        return new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || pending.get(sequence) != spawn) throw new IllegalStateException("Stale spawn acknowledgement");
                pending.remove(sequence); outstanding.put(sequence, spawn); revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || outstanding.get(sequence) != spawn) throw new IllegalStateException("Spawn acknowledgement rollback conflict");
                outstanding.remove(sequence); pending.put(sequence, spawn); revision = expected; state = 2;
            }
        };
    }
    @Override public List<Spawn> outstandingSpawns() { requireActive(); return List.copyOf(outstanding.values()); }
    @Override public StateChange prepareResolved(long sequence) {
        requireActive(); Spawn spawn = outstanding.get(sequence); if (spawn == null || clear != null || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Unknown outstanding spawn");
        long expected = revision;
        return new StateChange() {
            private int state; private Clear generated;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || outstanding.get(sequence) != spawn) throw new IllegalStateException("Stale enemy resolution");
                outstanding.remove(sequence); if (allEmitted() && pending.isEmpty() && outstanding.isEmpty()) { generated = currentClear(); clear = generated; }
                revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || outstanding.containsKey(sequence) || generated != null && clear != generated) throw new IllegalStateException("Enemy resolution rollback conflict");
                if (generated != null) clear = null; outstanding.put(sequence, spawn); revision = expected; state = 2;
            }
        };
    }
    @Override public Optional<Clear> clearEvent() { requireActive(); return Optional.ofNullable(clear); }
    @Override public StateChange prepareClearAcknowledged() {
        requireActive(); if (clear == null || activeWave < 0 || !pending.isEmpty() || !outstanding.isEmpty() || revision == Long.MAX_VALUE) throw new IllegalStateException("Wave clear acknowledgement unavailable");
        Clear before = clear; int waveBefore = activeWave, nextBefore = nextWave; long expected = revision; List<GroupState> groupBefore = groups.stream().map(GroupState::copy).toList();
        int after = waveBefore + 1; if (after >= config.waves().size() && config.repeatFrom() >= 0) after = config.repeatFrom(); int nextAfter = after;
        return new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); if (state != 0 || revision != expected || clear != before || activeWave != waveBefore) throw new IllegalStateException("Stale wave clear acknowledgement");
                clear = null; activeWave = -1; groups.clear(); nextWave = nextAfter; revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || clear != null || activeWave >= 0 || nextWave != nextAfter) throw new IllegalStateException("Wave clear rollback conflict");
                clear = before; activeWave = waveBefore; nextWave = nextBefore; groups.clear(); groupBefore.forEach(value -> groups.add(value.copy())); revision = expected; state = 2;
            }
        };
    }
    private boolean allEmitted() {
        if (activeWave < 0) return false; List<Group> definitions = config.waves().get(activeWave).groups();
        if (groups.size() != definitions.size()) throw new IllegalStateException("Wave group state mismatch");
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).emitted != definitions.get(i).count()) return false; return true;
    }
    private Clear currentClear() {
        Wave wave = config.waves().get(activeWave); return new Clear(activeWave, wave.id(), wave.clearReward(), wave.shop());
    }
    @Override public void tick(long tick) {
        requireActive(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing wave tick");
        if (lastTick >= 0) elapsed = Math.addExact(elapsed, tick - lastTick); lastTick = tick;
        if (activeWave < 0 || clear != null || pending.size() >= config.maxPendingSpawns()) return;
        Wave wave = config.waves().get(activeWave); int budget = config.maxSpawnsPerTick(); boolean changed = false;
        for (int i = 0; i < groups.size() && budget > 0 && pending.size() < config.maxPendingSpawns(); i++) {
            Group definition = wave.groups().get(i); GroupState state = groups.get(i);
            while (state.emitted < definition.count() && state.nextDue <= elapsed && budget > 0 && pending.size() < config.maxPendingSpawns()) {
                if (nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Wave spawn sequence exhausted");
                Spawn spawn = new Spawn(nextSequence, activeWave, wave.id(), i, definition.enemy(), definition.lane(), definition.modifiers(), definition.tags());
                pending.put(nextSequence, spawn); nextSequence++; state.emitted++; state.nextDue = Math.addExact(state.nextDue, definition.intervalTicks()); budget--; changed = true;
            }
        }
        if (changed) { revision = Math.incrementExact(revision); session.markDirty(); }
    }
    @Override public int stateSchema() { return 1; }
    private static Map<String, Object> encode(Spawn spawn) {
        return Map.of("sequence", spawn.sequence(), "wave", spawn.waveIndex(), "group", spawn.groupIndex(), "enemy", spawn.enemy().toString(), "lane", spawn.lane().toString(),
                "modifiers", spawn.modifiers().stream().map(Id::toString).sorted().toList(), "tags", spawn.tags().stream().map(Id::toString).sorted().toList());
    }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> groupState = new ArrayList<>();
        for (GroupState state : groups) groupState.add(Map.of("emitted", state.emitted, "remaining", Math.max(0, state.nextDue - elapsed)));
        Map<String, Object> result = new LinkedHashMap<>(Map.of("revision", revision, "next_wave", nextWave, "active_wave", activeWave, "next_sequence", nextSequence,
                "groups", groupState, "pending", pending.values().stream().map(WaveSystem::encode).toList(), "outstanding", outstanding.values().stream().map(WaveSystem::encode).toList()));
        result.put("clear", clear != null); return Values.map(result);
    }
    private Spawn decodeSpawn(Node n) {
        n.only("sequence", "wave", "group", "enemy", "lane", "modifiers", "tags"); long sequence = n.integer("sequence", 1, Long.MAX_VALUE - 1);
        int waveIndex = (int) n.integer("wave", 0, config.waves().size() - 1), groupIndex = (int) n.integer("group", 0, 1023); Wave wave = config.waves().get(waveIndex);
        if (groupIndex >= wave.groups().size()) throw new ConfigException("Restored spawn group outside wave"); Group group = wave.groups().get(groupIndex);
        Set<Id> modifiers = new LinkedHashSet<>(), tags = new LinkedHashSet<>(); n.strings("modifiers").forEach(raw -> modifiers.add(Id.of(raw))); n.strings("tags").forEach(raw -> tags.add(Id.of(raw)));
        if (!group.enemy().equals(Id.of(n.string("enemy"))) || !group.lane().equals(Id.of(n.string("lane"))) || !group.modifiers().equals(modifiers) || !group.tags().equals(tags)) throw new ConfigException("Restored spawn definition mismatch");
        return new Spawn(sequence, waveIndex, wave.id(), groupIndex, group.enemy(), group.lane(), modifiers, tags);
    }
    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid wave restore"); Node n = new Node(saved, "wave-state");
        n.only("revision", "next_wave", "active_wave", "next_sequence", "groups", "pending", "outstanding", "clear"); revision = n.integer("revision", 0, Long.MAX_VALUE - 1);
        nextWave = (int) n.integer("next_wave", 0, config.waves().size()); activeWave = (int) n.integer("active_wave", -1, config.waves().size() - 1); nextSequence = n.integer("next_sequence", 1, Long.MAX_VALUE);
        if (config.repeatFrom() >= 0 && nextWave >= config.waves().size()) throw new ConfigException("Repeating wave state cannot be complete");
        List<Node> restoredGroups = n.nodes("groups");
        if (activeWave < 0 != restoredGroups.isEmpty()) throw new ConfigException("Wave active/group state mismatch");
        if (activeWave >= 0) {
            List<Group> definitions = config.waves().get(activeWave).groups(); if (restoredGroups.size() != definitions.size()) throw new ConfigException("Restored wave group count mismatch");
            for (int i = 0; i < restoredGroups.size(); i++) { Node row = restoredGroups.get(i); row.only("emitted", "remaining"); int emitted = (int) row.integer("emitted", 0, definitions.get(i).count()); long remaining = row.integer("remaining", 0, 1_000_000); groups.add(new GroupState(emitted, remaining)); }
        }
        Set<Long> sequences = new HashSet<>();
        for (Node row : n.nodes("pending")) { Spawn spawn = decodeSpawn(row); if (spawn.waveIndex() != activeWave || spawn.sequence() >= nextSequence || !sequences.add(spawn.sequence())) throw new ConfigException("Invalid restored pending spawn"); pending.put(spawn.sequence(), spawn); }
        for (Node row : n.nodes("outstanding")) { Spawn spawn = decodeSpawn(row); if (spawn.waveIndex() != activeWave || spawn.sequence() >= nextSequence || !sequences.add(spawn.sequence())) throw new ConfigException("Invalid restored outstanding spawn"); outstanding.put(spawn.sequence(), spawn); }
        if (pending.size() > config.maxPendingSpawns()) throw new ConfigException("Restored pending spawn capacity exceeded");
        boolean cleared = n.bool("clear", false); if (cleared) {
            if (activeWave < 0 || !pending.isEmpty() || !outstanding.isEmpty() || !allEmitted()) throw new ConfigException("Invalid restored clear state"); clear = currentClear();
        } else if (activeWave >= 0 && allEmitted() && pending.isEmpty() && outstanding.isEmpty()) throw new ConfigException("Completed restored wave missing clear event");
        elapsed = 0; lastTick = -1; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; groups.clear(); pending.clear(); outstanding.clear(); clear = null; }
}
