package vn.svframe.svarcade.systems.enemy;

import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.*;
import vn.svframe.svarcade.systems.path.*;
import vn.svframe.svarcade.systems.targeting.*;
import vn.svframe.svarcade.systems.wave.*;

/** Owns durable TD enemy identity/state while delegating movement, indexing, combat math and wave lifecycle. */
public final class EnemySystem implements SessionSystem, EnemyAccess {
    public static final Id ID = Id.of("svarcade:enemies");

    public record Profile(Map<String, Object> renderer, double maxHealth, double armor, double speed, double strength,
                          Map<Id, Double> resistances, Map<Id, Double> statusResistances, Set<Id> tags,
                          Map<String, Object> reward, int statusCapacity) {
        public Profile {
            renderer = Values.map(renderer); resistances = Map.copyOf(resistances); statusResistances = Map.copyOf(statusResistances);
            tags = Set.copyOf(tags); reward = Values.map(reward);
            if (!positive(maxHealth) || !finite(armor, 0, 1_000_000) || !finite(speed, 0, 1_000_000)
                    || !finite(strength, 0, 1_000_000) || statusCapacity < 1 || statusCapacity > 512 || tags.size() > 512)
                throw new ConfigException("Enemy profile limits");
            resistances.values().forEach(v -> { if (!finite(v, -1, 1)) throw new ConfigException("Enemy resistance limits"); });
            statusResistances.values().forEach(v -> { if (!finite(v, 0, 1)) throw new ConfigException("Enemy status resistance limits"); });
        }
    }

    public record Modifier(double healthMultiplier, double armorMultiplier, double speedMultiplier, double strengthMultiplier, Set<Id> tags) {
        public Modifier {
            tags = Set.copyOf(tags);
            if (!finite(healthMultiplier, 0.000001, 1_000) || !finite(armorMultiplier, 0, 1_000)
                    || !finite(speedMultiplier, 0, 1_000) || !finite(strengthMultiplier, 0, 1_000) || tags.size() > 256)
                throw new ConfigException("Enemy modifier limits");
        }
    }

    public record Config(int capacity, int maxTickWork, int maxSpawnsPerTick, int maxOutcomes,
                         Map<Id, Profile> profiles, Map<Id, Modifier> modifiers) {
        public Config {
            profiles = Map.copyOf(profiles); modifiers = Map.copyOf(modifiers);
            if (capacity < 1 || capacity > 100_000 || maxTickWork < 1 || maxTickWork > capacity
                    || maxSpawnsPerTick < 1 || maxSpawnsPerTick > 4096 || maxOutcomes < 1 || maxOutcomes > 100_000
                    || profiles.isEmpty() || profiles.size() > 10_000 || modifiers.size() > 10_000)
                throw new ConfigException("Enemy system limits");
        }
        public static Config parse(Node n) {
            n.only("capacity", "max_tick_work", "max_spawns_per_tick", "max_outcomes", "profiles", "modifiers");
            Map<Id, Profile> profiles = new LinkedHashMap<>(); Node definitions = n.node("profiles");
            for (String raw : definitions.values().keySet()) {
                Node p = definitions.node(raw); p.only("renderer", "max_health", "armor", "speed", "strength", "resistances", "status_resistances", "tags", "reward", "status_capacity");
                profiles.put(Id.of(raw), new Profile(p.has("renderer") ? p.node("renderer").values() : Map.of(),
                        number(p.require("max_health"), Double.MIN_NORMAL, 1_000_000_000), number(p.require("armor"), 0, 1_000_000),
                        number(p.require("speed"), 0, 1_000_000), number(p.require("strength"), 0, 1_000_000),
                        numericMap(p, "resistances", -1, 1), numericMap(p, "status_resistances", 0, 1), ids(p, "tags"),
                        p.has("reward") ? p.node("reward").values() : Map.of(), (int) p.integer("status_capacity", 1, 512)));
            }
            Map<Id, Modifier> modifiers = new LinkedHashMap<>();
            if (n.has("modifiers")) {
                Node values = n.node("modifiers");
                for (String raw : values.values().keySet()) {
                    Node m = values.node(raw); m.only("health_multiplier", "armor_multiplier", "speed_multiplier", "strength_multiplier", "tags");
                    modifiers.put(Id.of(raw), new Modifier(number(m.require("health_multiplier"), 0.000001, 1_000),
                            number(m.require("armor_multiplier"), 0, 1_000), number(m.require("speed_multiplier"), 0, 1_000),
                            number(m.require("strength_multiplier"), 0, 1_000), ids(m, "tags")));
                }
            }
            return new Config((int) n.integer("capacity", 1, 100_000), (int) n.integer("max_tick_work", 1, 100_000),
                    (int) n.integer("max_spawns_per_tick", 1, 4096), (int) n.integer("max_outcomes", 1, 100_000), profiles, modifiers);
        }
        private static Map<Id, Double> numericMap(Node n, String field, double min, double max) {
            if (!n.has(field)) return Map.of(); Map<Id, Double> result = new LinkedHashMap<>();
            n.node(field).values().forEach((key, value) -> result.put(Id.of(key), number(value, min, max))); return Map.copyOf(result);
        }
        private static Set<Id> ids(Node n, String field) {
            if (!n.has(field)) return Set.of(); Set<Id> result = new LinkedHashSet<>(); n.strings(field).forEach(v -> result.add(Id.of(v))); return Set.copyOf(result);
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(WaveSystem.ID, PathSystem.ID, TargetingSystem.ID, CombatSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(WaveAccess.ACCESS, PathSystem.ACCESS, TargetingSystem.ACCESS, CombatAccess.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            EnemySystem system = new EnemySystem(Config.parse(config), session); session.services().provide(ACCESS, system); return system;
        }
    }

    private record Effective(Id profile, Id lane, double maxHealth, double armor, double speed, double strength,
                             Map<Id, Double> resistances, Map<Id, Double> statusResistances, Set<Id> tags,
                             Map<String, Object> reward, int statusCapacity) { }

    private static final class EnemyState {
        final UUID id;
        final long sequence;
        final Effective effective;
        final StatusRuntime statuses;
        double health;
        long lastUpdateTick = -1;
        EnemyState(UUID id, long sequence, Effective effective, double health, StatusRuntime statuses) {
            this.id = id; this.sequence = sequence; this.effective = effective; this.health = health; this.statuses = statuses;
        }
    }

    private final Config config;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final WaveAccess waves;
    private final PathAccess paths;
    private final TargetingAccess targeting;
    private final CombatAccess combat;
    private final NavigableMap<UUID, EnemyState> enemies = new TreeMap<>();
    private final ArrayDeque<Outcome> outcomes = new ArrayDeque<>();
    private UUID cursor;
    private long revision;
    private long lastTick = -1;
    private boolean active, closed;

    private EnemySystem(Config config, GenericSession session) {
        this.config = config; this.session = session; thread = session.thread();
        waves = session.services().require(WaveAccess.ACCESS); paths = session.services().require(PathSystem.ACCESS);
        targeting = session.services().require(TargetingSystem.ACCESS); combat = session.services().require(CombatAccess.ACCESS);
        validateCombatReferences();
    }

    private void validateCombatReferences() {
        Set<Id> damageTypes = new HashSet<>();
        // CombatAccess intentionally exposes only validation through resolve; profile resistance IDs are checked lazily by a zero-damage probe.
        for (Profile profile : config.profiles().values()) {
            for (Id status : profile.statusResistances().keySet()) if (!combat.statusDefinitions().containsKey(status))
                throw new ConfigException("Enemy profile references unknown status: " + status);
            damageTypes.addAll(profile.resistances().keySet());
        }
        if (!damageTypes.isEmpty()) {
            Profile sample = config.profiles().values().iterator().next();
            for (Id type : damageTypes) {
                try {
                    combat.resolve(new CombatAccess.Attack(type, 0, 0, 0, 0, 1, CombatAccess.Delivery.INSTANT, 0, 0, List.of()),
                            new CombatAccess.Target(sample.armor(), sample.resistances(), sample.tags(), sample.statusResistances()), 0);
                } catch (IllegalArgumentException e) {
                    throw new ConfigException("Enemy profile references unknown damage type: " + type, e);
                }
            }
        }
    }

    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Enemies inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Enemies already initialized"); active = true; }
    @Override public long revision() { requireActive(); return revision; }
    @Override public int size() { requireActive(); return enemies.size(); }

    @Override public Optional<View> enemy(UUID id) { requireActive(); EnemyState state = enemies.get(id); return state == null ? Optional.empty() : Optional.of(view(state)); }
    @Override public List<View> enemies(int maximum) {
        requireActive(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Enemy page limit");
        return enemies.values().stream().limit(maximum).map(this::view).toList();
    }
    private View view(EnemyState state) {
        double speed = paths.agent(state.id).map(PathAccess.Agent::speed).orElse(0.0);
        double progress = paths.agent(state.id).isPresent() ? paths.progress(state.id) : 1.0;
        return new View(state.id, state.sequence, state.effective.profile(), state.effective.lane(), state.health, state.effective.maxHealth(),
                speed, state.effective.armor(), state.effective.strength(), progress, state.effective.tags(), state.statuses.ids());
    }

    @Override public CombatAccess.Target combatTarget(UUID id) {
        requireActive(); EnemyState state = requireEnemy(id);
        return new CombatAccess.Target(state.effective.armor(), state.effective.resistances(), state.effective.tags(), state.effective.statusResistances());
    }

    @Override public StateChange prepareHit(UUID id, CombatAccess.Resolution resolution) {
        requireActive(); EnemyState state = requireEnemy(id); Objects.requireNonNull(resolution);
        if (!Double.isFinite(resolution.damage()) || resolution.damage() < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid enemy damage");
        long expected = revision; double beforeHealth = state.health; Map<String, Object> beforeStatuses = state.statuses.snapshot();
        StatusRuntime candidate = new StatusRuntime(combat.statusDefinitions(), state.effective.statusCapacity()); candidate.restore(beforeStatuses);
        for (CombatAccess.AppliedStatus applied : resolution.statuses()) candidate.apply(applied);
        Map<String, Object> afterStatuses = candidate.snapshot(); double afterHealth = Math.max(0, beforeHealth - resolution.damage());
        double beforeSpeed = paths.agent(id).orElseThrow(() -> new IllegalStateException("Enemy path missing")).speed();
        double afterSpeed = effectiveSpeed(state.effective.speed(), candidate);
        return new StateChange() {
            private int used;
            @Override public void apply() {
                requireActive(); if (used != 0 || revision != expected || enemies.get(id) != state || state.health != beforeHealth) throw new IllegalStateException("Stale enemy hit");
                try {
                    state.health = afterHealth; state.statuses.restore(afterStatuses); paths.speed(id, afterSpeed);
                    if (afterHealth <= 0) targeting.remove(id); else publish(state);
                    revision = expected + 1; used = 1;
                } catch (RuntimeException failure) {
                    state.health = beforeHealth; state.statuses.restore(beforeStatuses);
                    try { paths.speed(id, beforeSpeed); publish(state); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
            }
            @Override public void rollback() {
                requireActive(); if (used != 1 || revision != expected + 1 || enemies.get(id) != state) throw new IllegalStateException("Enemy hit rollback conflict");
                state.health = beforeHealth; state.statuses.restore(beforeStatuses); paths.speed(id, beforeSpeed); publish(state); revision = expected; used = 2;
            }
        };
    }

    @Override public List<Outcome> drainOutcomes(int maximum) {
        requireActive(); if (maximum < 1 || maximum > 4096) throw new IllegalArgumentException("Outcome drain limit");
        List<Outcome> result = new ArrayList<>(); while (!outcomes.isEmpty() && result.size() < maximum) result.add(outcomes.removeFirst());
        if (!result.isEmpty()) session.markDirty(); return List.copyOf(result);
    }

    @Override public void tick(long tick) {
        requireActive(); if (tick < 0 || lastTick >= 0 && tick <= lastTick) throw new IllegalArgumentException("Non-increasing enemy tick"); lastTick = tick;
        boolean changed = spawnPending(tick);
        List<UUID> batch = batch(config.maxTickWork());
        for (UUID id : batch) {
            EnemyState state = enemies.get(id); if (state == null) continue;
            long delta = state.lastUpdateTick < 0 ? 0 : tick - state.lastUpdateTick; state.lastUpdateTick = tick;
            double dot = state.statuses.advance(delta); if (dot > 0) { state.health = Math.max(0, state.health - dot); changed = true; }
            if (state.health <= 0) { resolve(state, Cause.DEATH); changed = true; continue; }
            double speed = effectiveSpeed(state.effective.speed(), state.statuses); PathAccess.Agent agent = paths.agent(id).orElseThrow(() -> new IllegalStateException("Enemy path missing"));
            if (Double.compare(agent.speed(), speed) != 0) { paths.speed(id, speed); changed = true; }
            if (paths.progress(id) >= 1.0) { resolve(state, Cause.GOAL); changed = true; continue; }
            publish(state);
        }
        if (changed) session.markDirty();
    }

    private boolean spawnPending(long tick) {
        boolean changed = false;
        for (WaveAccess.Spawn spawn : waves.pendingSpawns(config.maxSpawnsPerTick())) {
            if (enemies.size() >= config.capacity()) break;
            UUID id = identity(spawn.sequence()); if (enemies.containsKey(id)) throw new IllegalStateException("Duplicate deterministic enemy identity");
            Effective effective = derive(spawn); StatusRuntime statuses = new StatusRuntime(combat.statusDefinitions(), effective.statusCapacity());
            EnemyState state = new EnemyState(id, spawn.sequence(), effective, effective.maxHealth(), statuses); state.lastUpdateTick = tick;
            StateChange acknowledgement = waves.prepareSpawned(spawn.sequence()); paths.spawn(id, spawn.lane(), effective.speed());
            boolean acknowledged = false;
            try {
                acknowledgement.apply(); acknowledged = true; enemies.put(id, state); publish(state); revision = Math.incrementExact(revision); changed = true;
            } catch (RuntimeException failure) {
                targeting.remove(id); enemies.remove(id); paths.remove(id);
                if (acknowledged) try { acknowledgement.rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
        return changed;
    }

    private void resolve(EnemyState state, Cause cause) {
        if (outcomes.size() >= config.maxOutcomes()) throw new IllegalStateException("Enemy outcome queue capacity");
        StateChange resolved = waves.prepareResolved(state.sequence); resolved.apply();
        targeting.remove(state.id); paths.remove(state.id); enemies.remove(state.id);
        Map<String, Object> reward = cause == Cause.DEATH ? state.effective.reward() : Map.of();
        outcomes.addLast(new Outcome(state.id, state.sequence, state.effective.profile(), cause, reward)); revision = Math.incrementExact(revision);
    }

    private List<UUID> batch(int maximum) {
        if (enemies.isEmpty()) { cursor = null; return List.of(); }
        List<UUID> result = new ArrayList<>(Math.min(maximum, enemies.size()));
        NavigableSet<UUID> keys = enemies.navigableKeySet();
        Iterator<UUID> tail = cursor == null ? keys.iterator() : keys.tailSet(cursor, false).iterator();
        while (tail.hasNext() && result.size() < maximum) result.add(tail.next());
        if (result.size() < maximum) {
            Iterator<UUID> head = cursor == null ? Collections.emptyIterator() : keys.headSet(cursor, true).iterator();
            while (head.hasNext() && result.size() < maximum) result.add(head.next());
        }
        if (!result.isEmpty()) cursor = result.getLast(); return List.copyOf(result);
    }

    private EnemyState requireEnemy(UUID id) { EnemyState state = enemies.get(Objects.requireNonNull(id)); if (state == null) throw new IllegalArgumentException("Unknown enemy"); return state; }
    private UUID identity(long sequence) {
        String source = "svarcade/enemy/" + session.id() + "/" + sequence; return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
    private Effective derive(WaveAccess.Spawn spawn) {
        Profile profile = config.profiles().get(spawn.enemy()); if (profile == null) throw new ConfigException("Wave references unknown enemy profile: " + spawn.enemy());
        double health = profile.maxHealth(), armor = profile.armor(), speed = profile.speed(), strength = profile.strength(); Set<Id> tags = new LinkedHashSet<>(profile.tags()); tags.addAll(spawn.tags());
        for (Id id : new TreeSet<>(spawn.modifiers())) {
            Modifier modifier = config.modifiers().get(id); if (modifier == null) throw new ConfigException("Wave references unknown enemy modifier: " + id);
            health = checkedMultiply(health, modifier.healthMultiplier()); armor = checkedMultiply(armor, modifier.armorMultiplier());
            speed = checkedMultiply(speed, modifier.speedMultiplier()); strength = checkedMultiply(strength, modifier.strengthMultiplier()); tags.addAll(modifier.tags());
        }
        return new Effective(spawn.enemy(), spawn.lane(), health, armor, speed, strength, profile.resistances(), profile.statusResistances(), Set.copyOf(tags), profile.reward(), profile.statusCapacity());
    }
    private double effectiveSpeed(double base, StatusRuntime statuses) {
        if (statuses.has(CombatAccess.StatusKind.STUN)) return 0;
        double slow = 0;
        for (Id id : statuses.ids()) if (combat.statusDefinitions().get(id).kind() == CombatAccess.StatusKind.SLOW) slow += Math.max(0, statuses.magnitude(id));
        return Math.max(0, base * Math.max(0, 1 - Math.min(1, slow)));
    }
    private void publish(EnemyState state) {
        if (state.health <= 0 || paths.agent(state.id).isEmpty()) { targeting.remove(state.id); return; }
        Set<String> tags = new LinkedHashSet<>(); state.effective.tags().forEach(id -> tags.add(id.toString())); state.statuses.ids().forEach(id -> tags.add("status/" + id));
        targeting.upsert(new TargetingAccess.Target(state.id, paths.position(state.id), state.health, state.effective.strength(), paths.progress(state.id), tags));
    }

    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> rows = new ArrayList<>();
        for (EnemyState state : enemies.values()) rows.add(Map.of("id", state.id.toString(), "sequence", state.sequence, "health", state.health, "statuses", state.statuses.snapshot()));
        List<Object> savedOutcomes = outcomes.stream().map(o -> Map.of("id", o.id().toString(), "sequence", o.sequence(), "profile", o.profile().toString(), "cause", o.cause().name(), "reward", o.reward())).toList();
        Map<String, Object> result = new LinkedHashMap<>(); result.put("revision", revision); result.put("enemies", rows); result.put("outcomes", savedOutcomes);
        if (cursor != null) result.put("cursor", cursor.toString()); return Values.map(result);
    }

    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid enemy restore"); Node n = new Node(saved, "enemy-state"); n.only("revision", "enemies", "outcomes", "cursor");
        long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1); if (n.list("enemies").size() > config.capacity() || n.list("outcomes").size() > config.maxOutcomes()) throw new ConfigException("Restored enemy capacity");
        Map<Long, WaveAccess.Spawn> outstanding = new HashMap<>();
        for (WaveAccess.Spawn spawn : waves.outstandingSpawns()) if (outstanding.putIfAbsent(spawn.sequence(), spawn) != null) throw new ConfigException("Duplicate outstanding wave sequence");
        NavigableMap<UUID, EnemyState> restored = new TreeMap<>(); Set<Long> sequences = new HashSet<>();
        for (Node row : n.nodes("enemies")) {
            row.only("id", "sequence", "health", "statuses"); long sequence = row.integer("sequence", 1, Long.MAX_VALUE - 1); WaveAccess.Spawn spawn = outstanding.get(sequence);
            if (spawn == null || !sequences.add(sequence)) throw new ConfigException("Enemy state lacks matching outstanding wave spawn"); UUID id = UUID.fromString(row.string("id"));
            if (!id.equals(identity(sequence))) throw new ConfigException("Enemy deterministic identity mismatch"); Effective effective = derive(spawn);
            double health = number(row.require("health"), 0, effective.maxHealth()); StatusRuntime statuses = new StatusRuntime(combat.statusDefinitions(), effective.statusCapacity()); statuses.restore(row.node("statuses").values());
            EnemyState state = new EnemyState(id, sequence, effective, health, statuses); if (restored.putIfAbsent(id, state) != null) throw new ConfigException("Duplicate restored enemy identity");
        }
        if (!sequences.equals(outstanding.keySet())) throw new ConfigException("Outstanding wave/enemy state mismatch");
        for (EnemyState state : restored.values()) {
            PathAccess.Agent agent = paths.agent(state.id).orElseThrow(() -> new ConfigException("Restored enemy path missing"));
            if (!agent.path().equals(state.effective.lane()) || Math.abs(agent.speed() - effectiveSpeed(state.effective.speed(), state.statuses)) > 1.0e-9)
                throw new ConfigException("Restored enemy path state mismatch");
        }
        ArrayDeque<Outcome> restoredOutcomes = new ArrayDeque<>();
        for (Node row : n.nodes("outcomes")) {
            row.only("id", "sequence", "profile", "cause", "reward"); Id profile = Id.of(row.string("profile")); if (!config.profiles().containsKey(profile)) throw new ConfigException("Unknown restored enemy outcome profile");
            Cause cause; try { cause = Cause.valueOf(row.string("cause")); } catch (IllegalArgumentException e) { throw new ConfigException("Unknown restored enemy outcome cause", e); }
            restoredOutcomes.addLast(new Outcome(UUID.fromString(row.string("id")), row.integer("sequence", 1, Long.MAX_VALUE - 1), profile, cause, row.node("reward").values()));
        }
        UUID restoredCursor = n.has("cursor") ? UUID.fromString(n.string("cursor")) : null;
        enemies.clear(); enemies.putAll(restored); outcomes.clear(); outcomes.addAll(restoredOutcomes); cursor = restoredCursor; revision = restoredRevision; active = true;
        List<UUID> published = new ArrayList<>();
        try { for (EnemyState state : enemies.values()) { if (state.health > 0) { publish(state); published.add(state.id); } } }
        catch (RuntimeException failure) { published.forEach(targeting::remove); enemies.clear(); outcomes.clear(); active = false; throw failure; }
    }

    @Override public void close() {
        thread.check();
        for (UUID id : new ArrayList<>(enemies.keySet())) { targeting.remove(id); paths.remove(id); }
        enemies.clear(); outcomes.clear(); active = false; closed = true; cursor = null;
    }

    private static double checkedMultiply(double left, double right) { double result = left * right; if (!Double.isFinite(result) || result < 0 || result > 1_000_000_000) throw new ConfigException("Enemy modifier overflow"); return result; }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0 && value <= 1_000_000_000; }
    private static boolean finite(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
    private static double number(Object value, double min, double max) {
        if (!(value instanceof Number n)) throw new ConfigException("Expected numeric enemy value"); double result = n.doubleValue(); if (!finite(result, min, max)) throw new ConfigException("Enemy number outside bounds"); return result;
    }
}
