package vn.svframe.svarcade.systems.upgrade;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.systems.currency.*;
import vn.svframe.svarcade.systems.deployable.*;
import vn.svframe.svarcade.systems.upgrade.UpgradeAccess.*;

/** Data-defined upgrade graph with atomic match-currency purchases. */
public final class UpgradeSystem implements SessionSystem, UpgradeAccess {
    public static final Id ID = Id.of("svarcade:upgrades");
    public record Config(Id currency, Map<Id, Definition> upgrades) {
        public Config {
            Objects.requireNonNull(currency); upgrades = Map.copyOf(upgrades);
            if (upgrades.isEmpty() || upgrades.size() > 4096) throw new ConfigException("Upgrade definition limits");
            for (Definition definition : upgrades.values()) {
                for (Requirement requirement : definition.prerequisites()) {
                    Definition dependency = upgrades.get(requirement.upgrade());
                    if (dependency == null || requirement.level() < 1 || requirement.level() > dependency.maxLevel()) throw new ConfigException("Invalid upgrade prerequisite");
                }
            }
            detectCycles(upgrades);
        }
        public static Config parse(Node n) {
            n.only("currency", "upgrades"); Map<Id, Definition> result = new LinkedHashMap<>(); Node values = n.node("upgrades");
            for (String raw : values.values().keySet()) {
                Id id = Id.of(raw); Node u = values.node(raw);
                u.only("max_level", "costs", "profiles", "required_tags", "forbidden_tags", "prerequisites", "modifiers");
                int max = (int) u.integer("max_level", 1, 1024); List<?> rawCosts = u.list("costs");
                if (rawCosts.size() != max) throw u.error("costs", "Expected one cost per level");
                List<Long> costs = new ArrayList<>(); for (Object value : rawCosts) {
                    if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) throw u.error("costs", "Expected non-negative integer costs");
                    costs.add(((Number) value).longValue());
                }
                Set<Id> profiles = ids(u, "profiles"), required = ids(u, "required_tags"), forbidden = ids(u, "forbidden_tags");
                if (!Collections.disjoint(required, forbidden)) throw u.error("required_tags", "Required and forbidden tags overlap");
                List<Requirement> prerequisites = new ArrayList<>();
                if (u.has("prerequisites")) for (Node p : u.nodes("prerequisites")) {
                    p.only("upgrade", "level"); prerequisites.add(new Requirement(Id.of(p.string("upgrade")), (int) p.integer("level", 1, 1024)));
                }
                Map<Id, Double> modifiers = new LinkedHashMap<>(); Node m = u.node("modifiers");
                for (String key : m.values().keySet()) {
                    double value = Numbers.decimal(m, key, -1_000_000, 1_000_000); if (value == 0) throw m.error(key, "Zero modifier has no effect");
                    modifiers.put(Id.of(key), value);
                }
                if (modifiers.isEmpty() || modifiers.size() > 256) throw u.error("modifiers", "Expected 1..256 modifiers");
                if (result.putIfAbsent(id, new Definition(id, max, costs, profiles, required, forbidden, prerequisites, modifiers)) != null) throw new ConfigException("Duplicate upgrade: " + id);
            }
            return new Config(Id.of(n.string("currency")), result);
        }
        private static Set<Id> ids(Node n, String field) {
            if (!n.has(field)) return Set.of(); Set<Id> result = new LinkedHashSet<>(); n.strings(field).forEach(raw -> result.add(Id.of(raw))); return Set.copyOf(result);
        }
        private static void detectCycles(Map<Id, Definition> values) {
            Set<Id> done = new HashSet<>(), active = new HashSet<>();
            for (Id id : values.keySet()) visit(id, values, done, active);
        }
        private static void visit(Id id, Map<Id, Definition> values, Set<Id> done, Set<Id> active) {
            if (done.contains(id)) return; if (!active.add(id)) throw new ConfigException("Cyclic upgrade prerequisites at " + id);
            for (Requirement r : values.get(id).prerequisites()) visit(r.upgrade(), values, done, active);
            active.remove(id); done.add(id);
        }
    }
    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<Id> dependencies() { return Set.of(CurrencySystem.ID, DeployableSystem.ID); }
        @Override public Set<SessionServices.Key<?>> requires() { return Set.of(CurrencySystem.ACCESS, DeployableAccess.ACCESS); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            UpgradeSystem system = new UpgradeSystem(Config.parse(config), session.services().require(CurrencySystem.ACCESS), session.services().require(DeployableAccess.ACCESS), session);
            session.services().provide(ACCESS, system); return system;
        }
    }
    private final Config config;
    private final CurrencyAccess currency;
    private final DeployableAccess deployables;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final Map<Long, NavigableMap<Id, Integer>> purchased = new HashMap<>();
    private long revision;
    private boolean active, closed;
    private UpgradeSystem(Config config, CurrencyAccess currency, DeployableAccess deployables, GenericSession session) {
        this.config = config; this.currency = currency; this.deployables = deployables; this.session = session; thread = session.thread();
    }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Upgrades inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Upgrades already initialized"); active = true; }
    @Override public int level(long deployment, Id upgrade) { requireActive(); requireDeployment(deployment); requireDefinition(upgrade); return levelUnsafe(deployment, upgrade); }
    @Override public Map<Id, Integer> levels(long deployment) { requireActive(); requireDeployment(deployment); return Map.copyOf(purchased.getOrDefault(deployment, new TreeMap<>())); }
    @Override public Map<Id, Double> modifiers(long deployment) {
        requireActive(); requireDeployment(deployment); Map<Id, Double> result = new TreeMap<>();
        purchased.getOrDefault(deployment, new TreeMap<>()).forEach((id, level) -> config.upgrades().get(id).modifiers().forEach((modifier, value) ->
                result.merge(modifier, value * level, Double::sum)));
        return Map.copyOf(result);
    }
    @Override public Map<Id, Definition> definitions() { requireActive(); return config.upgrades(); }
    @Override public long revision() { requireActive(); return revision; }
    @Override public StateChange preparePurchase(UUID actor, long deploymentId, Id upgradeId) {
        requireActive(); DeployableAccess.Deployment deployment = requireDeployment(deploymentId);
        if (!deployment.owner().equals(actor)) throw new IllegalArgumentException("Upgrade ownership");
        Definition definition = requireDefinition(upgradeId); DeployableAccess.Profile profile = deployables.profiles().get(deployment.profile());
        if (profile == null || !applicable(definition, profile)) throw new IllegalArgumentException("Upgrade does not apply");
        NavigableMap<Id, Integer> current = purchased.getOrDefault(deploymentId, new TreeMap<>()); int oldLevel = current.getOrDefault(upgradeId, 0);
        if (oldLevel >= definition.maxLevel() || revision == Long.MAX_VALUE) throw new IllegalStateException("Upgrade level unavailable");
        for (Requirement requirement : definition.prerequisites()) if (current.getOrDefault(requirement.upgrade(), 0) < requirement.level()) throw new IllegalStateException("Upgrade prerequisite missing");
        CurrencyAccess.Change payment = currency.prepare(List.of(new CurrencyAccess.Delta(actor, config.currency(), -definition.costs().get(oldLevel))));
        long expected = revision; int nextLevel = oldLevel + 1;
        StateChange local = new StateChange() {
            private int state;
            @Override public void apply() {
                requireActive(); DeployableAccess.Deployment live = requireDeployment(deploymentId);
                if (state != 0 || revision != expected || live.version() != deployment.version() || levelUnsafe(deploymentId, upgradeId) != oldLevel) throw new IllegalStateException("Stale upgrade transaction");
                purchased.computeIfAbsent(deploymentId, ignored -> new TreeMap<>()).put(upgradeId, nextLevel); revision++; state = 1;
            }
            @Override public void rollback() {
                requireActive(); if (state != 1 || revision != expected + 1 || levelUnsafe(deploymentId, upgradeId) != nextLevel) throw new IllegalStateException("Upgrade rollback conflict");
                NavigableMap<Id, Integer> row = purchased.get(deploymentId); if (oldLevel == 0) row.remove(upgradeId); else row.put(upgradeId, oldLevel);
                if (row.isEmpty()) purchased.remove(deploymentId); revision = expected; state = 2;
            }
        };
        return new CompositeChange(thread, List.of(payment, local));
    }
    private boolean applicable(Definition definition, DeployableAccess.Profile profile) {
        return (definition.profiles().isEmpty() || definition.profiles().contains(profile.id()))
                && profile.tags().containsAll(definition.requiredTags()) && Collections.disjoint(profile.tags(), definition.forbiddenTags());
    }
    private int levelUnsafe(long deployment, Id upgrade) { return purchased.getOrDefault(deployment, new TreeMap<>()).getOrDefault(upgrade, 0); }
    private DeployableAccess.Deployment requireDeployment(long id) { return deployables.deployment(id).orElseThrow(() -> new IllegalArgumentException("Unknown deployment")); }
    private Definition requireDefinition(Id id) { Definition value = config.upgrades().get(id); if (value == null) throw new IllegalArgumentException("Unknown upgrade"); return value; }
    @Override public void tick(long tick) {
        requireActive(); boolean removed = purchased.keySet().removeIf(id -> deployables.deployment(id).isEmpty());
        if (removed) { revision = Math.incrementExact(revision); session.markDirty(); }
    }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); Map<String, Object> rows = new TreeMap<>();
        purchased.forEach((deployment, levels) -> { Map<String, Object> values = new TreeMap<>(); levels.forEach((id, level) -> values.put(id.toString(), level)); rows.put(Long.toString(deployment), values); });
        return Values.map(Map.of("revision", revision, "levels", rows));
    }
    @Override public void restore(int schema, Map<String, Object> state) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid upgrade restore");
        Node n = new Node(state, "upgrade-state"); n.only("revision", "levels"); long restoredRevision = n.integer("revision", 0, Long.MAX_VALUE - 1); Node rows = n.node("levels");
        if (rows.values().size() > 100_000) throw new ConfigException("Too many upgraded deployments"); Map<Long, NavigableMap<Id, Integer>> restored = new HashMap<>();
        for (String key : rows.values().keySet()) {
            long deployment;
            try { deployment = Long.parseLong(key); } catch (NumberFormatException e) { throw new ConfigException("Invalid upgraded deployment id", e); }
            DeployableAccess.Deployment deployed = requireDeployment(deployment); Node levels = rows.node(key); NavigableMap<Id, Integer> values = new TreeMap<>();
            for (String raw : levels.values().keySet()) {
                Id id = Id.of(raw); Definition definition = requireDefinition(id); int level = (int) levels.integer(raw, 1, definition.maxLevel());
                DeployableAccess.Profile profile = deployables.profiles().get(deployed.profile());
                if (profile == null || !applicable(definition, profile)) throw new ConfigException("Restored inapplicable upgrade"); values.put(id, level);
            }
            for (var entry : values.entrySet()) for (Requirement requirement : config.upgrades().get(entry.getKey()).prerequisites())
                if (values.getOrDefault(requirement.upgrade(), 0) < requirement.level()) throw new ConfigException("Restored upgrade prerequisite missing");
            restored.put(deployment, values);
        }
        purchased.putAll(restored); revision = restoredRevision; active = true;
    }
    @Override public void close() { thread.check(); active = false; closed = true; purchased.clear(); }
}
