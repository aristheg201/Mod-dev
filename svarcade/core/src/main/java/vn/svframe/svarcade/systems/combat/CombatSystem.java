package vn.svframe.svarcade.systems.combat;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.combat.CombatAccess.*;

/** Bounded deterministic combat resolver compiled entirely from definition data. */
public final class CombatSystem implements SessionSystem, CombatAccess {
    public static final Id ID = Id.of("svarcade:combat");
    public record Config(Set<Id> damageTypes, double armorScale, Map<Id, StatusDefinition> statuses) {
        public Config {
            damageTypes = Set.copyOf(damageTypes); statuses = Map.copyOf(statuses);
            if (damageTypes.isEmpty() || damageTypes.size() > 256 || armorScale <= 0 || !Double.isFinite(armorScale) || armorScale > 1_000_000 || statuses.size() > 512)
                throw new ConfigException("Combat definition limits");
        }
        public static Config parse(Node n) {
            n.only("damage_types", "armor_scale", "statuses");
            Set<Id> damage = new LinkedHashSet<>(); n.strings("damage_types").forEach(raw -> damage.add(Id.of(raw)));
            Map<Id, StatusDefinition> statuses = new LinkedHashMap<>(); Node values = n.node("statuses");
            for (String raw : values.values().keySet()) {
                Node s = values.node(raw); s.only("kind", "base_duration_ticks", "tick_interval", "magnitude", "stacking", "max_stacks", "immunity_tags");
                StatusKind kind;
                Stacking stacking;
                try { kind = StatusKind.valueOf(s.string("kind")); stacking = Stacking.valueOf(s.string("stacking")); }
                catch (IllegalArgumentException e) { throw s.error("kind", "Unknown status enum"); }
                long duration = s.integer("base_duration_ticks", 1, 1_000_000), interval = s.integer("tick_interval", 1, 1_000_000);
                if (interval > duration) throw s.error("tick_interval", "Tick interval exceeds duration");
                double magnitude = Numbers.decimal(s, "magnitude", -1_000_000, 1_000_000);
                Set<Id> immunity = new LinkedHashSet<>();
                if (s.has("immunity_tags")) s.strings("immunity_tags").forEach(tag -> immunity.add(Id.of(tag)));
                Id id = Id.of(raw);
                if (statuses.putIfAbsent(id, new StatusDefinition(kind, duration, interval, magnitude, stacking,
                        (int) s.integer("max_stacks", 1, 1024), immunity)) != null) throw new ConfigException("Duplicate status: " + id);
            }
            return new Config(damage, Numbers.decimal(n, "armor_scale", Double.MIN_NORMAL, 1_000_000), statuses);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            CombatSystem system = new CombatSystem(Config.parse(config), session.thread());
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final ThreadGuard thread;
    private boolean active, closed;
    private CombatSystem(Config config, ThreadGuard thread) { this.config = config; this.thread = thread; }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Combat inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Combat already initialized"); active = true; }
    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() { requireActive(); return Map.of(); }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (schema != 1 || !state.isEmpty()) throw new ConfigException("Invalid combat state"); start();
    }
    @Override public Map<Id, StatusDefinition> statusDefinitions() { requireActive(); return config.statuses(); }
    @Override public Resolution resolve(Attack attack, Target target, long entropy) {
        requireActive(); validate(attack, target);
        long stream = mix(entropy); boolean critical = unit(stream) < attack.critChance();
        double raw = attack.damage() * (critical ? attack.critMultiplier() : 1.0);
        double armorFactor = config.armorScale() / (config.armorScale() + target.armor());
        double resistance = target.resistances().getOrDefault(attack.damageType(), 0.0);
        double damage = raw * armorFactor * (1.0 - resistance);
        if (!Double.isFinite(damage) || damage < 0) throw new IllegalStateException("Non-finite combat result");
        List<AppliedStatus> applied = new ArrayList<>(); int index = 0;
        for (StatusAttempt attempt : attack.statuses()) {
            StatusDefinition definition = config.statuses().get(attempt.status());
            if (definition == null) throw new IllegalArgumentException("Unknown status: " + attempt.status());
            boolean immune = definition.immunityTags().stream().anyMatch(target.tags()::contains);
            double resistanceFactor = 1.0 - target.statusResistances().getOrDefault(attempt.status(), 0.0);
            long roll = mix(stream + (++index * 0x9E3779B97F4A7C15L));
            if (immune || unit(roll) >= attempt.chance() * resistanceFactor) continue;
            long duration = Math.max(1, Math.round(definition.baseDurationTicks() * attempt.durationMultiplier()));
            applied.add(new AppliedStatus(attempt.status(), definition.kind(), duration, definition.tickInterval(), definition.magnitude(),
                    definition.stacking(), Math.min(attempt.stacks(), definition.maxStacks())));
        }
        return new Resolution(damage, critical, attack.delivery(), attack.aoeCap(), attack.chainCap(), applied);
    }
    private void validate(Attack attack, Target target) {
        Objects.requireNonNull(attack); Objects.requireNonNull(target);
        if (!config.damageTypes().contains(attack.damageType()) || !Double.isFinite(attack.damage()) || attack.damage() < 0 || !Double.isFinite(attack.range()) || attack.range() < 0
                || attack.cooldownTicks() < 0 || !finite01(attack.critChance()) || !Double.isFinite(attack.critMultiplier()) || attack.critMultiplier() < 1 || attack.critMultiplier() > 100
                || attack.aoeCap() < 0 || attack.aoeCap() > 100_000 || attack.chainCap() < 0 || attack.chainCap() > 100_000 || attack.statuses().size() > 64)
            throw new IllegalArgumentException("Invalid attack");
        if (target.armor() < 0 || !Double.isFinite(target.armor()) || target.armor() > 1_000_000 || target.resistances().size() > 256 || target.statusResistances().size() > 512 || target.tags().size() > 512)
            throw new IllegalArgumentException("Invalid combat target");
        target.resistances().forEach((id, value) -> { if (!config.damageTypes().contains(id) || !finiteResistance(value)) throw new IllegalArgumentException("Invalid resistance"); });
        target.statusResistances().forEach((id, value) -> { if (!config.statuses().containsKey(id) || !finite01(value)) throw new IllegalArgumentException("Invalid status resistance"); });
        for (StatusAttempt attempt : attack.statuses()) if (!config.statuses().containsKey(attempt.status()) || !finite01(attempt.chance())
                || !Double.isFinite(attempt.durationMultiplier()) || attempt.durationMultiplier() <= 0 || attempt.durationMultiplier() > 100 || attempt.stacks() < 1 || attempt.stacks() > 1024)
            throw new IllegalArgumentException("Invalid status attempt");
        if (attack.delivery() == Delivery.AOE && attack.aoeCap() < 1 || attack.delivery() == Delivery.CHAIN && attack.chainCap() < 1) throw new IllegalArgumentException("Delivery cap missing");
    }
    private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    private static boolean finiteResistance(double value) { return Double.isFinite(value) && value >= -1 && value <= 1; }
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L; z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL; return z ^ (z >>> 31);
    }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    @Override public void close() { thread.check(); active = false; closed = true; }
}
