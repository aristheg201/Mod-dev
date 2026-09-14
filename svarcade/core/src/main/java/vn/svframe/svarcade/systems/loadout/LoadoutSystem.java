package vn.svframe.svarcade.systems.loadout;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.*;

/** Immutable party snapshots plus data-authored profile derivation. */
public final class LoadoutSystem implements SessionSystem, LoadoutAccess {
    public static final Id ID = Id.of("svarcade:loadouts");

    public record LevelCurve(int minimum, int center, int maximum, double minimumMultiplier, double maximumMultiplier) {
        public LevelCurve {
            if (minimum < 1 || center < minimum || maximum < center || maximum > 10_000
                    || !finite(minimumMultiplier, 0.01, 100) || !finite(maximumMultiplier, 0.01, 100))
                throw new ConfigException("Invalid level curve");
        }
        double multiplier(int level) {
            int clamped = Math.max(minimum, Math.min(maximum, level));
            if (clamped == center) return 1.0;
            if (clamped < center) {
                if (center == minimum) return 1.0;
                double f = (clamped - minimum) / (double) (center - minimum);
                return minimumMultiplier + (1.0 - minimumMultiplier) * f;
            }
            if (maximum == center) return 1.0;
            double f = (clamped - center) / (double) (maximum - center);
            return 1.0 + (maximumMultiplier - 1.0) * f;
        }
    }

    public record Rule(Set<Id> species, Set<String> forms, Set<Id> allAspects, Set<Id> anyTypes,
                       Set<Id> allMoves, Set<Id> abilities, Set<Id> tags, Map<Id, Double> modifiers) {
        public Rule {
            species = Set.copyOf(species); forms = Set.copyOf(forms); allAspects = Set.copyOf(allAspects); anyTypes = Set.copyOf(anyTypes);
            allMoves = Set.copyOf(allMoves); abilities = Set.copyOf(abilities); tags = Set.copyOf(tags); modifiers = Map.copyOf(modifiers);
        }
        boolean matches(Snapshot snapshot) {
            return (species.isEmpty() || species.contains(snapshot.species()))
                    && (forms.isEmpty() || forms.contains(snapshot.form()))
                    && snapshot.aspects().containsAll(allAspects)
                    && (anyTypes.isEmpty() || !Collections.disjoint(snapshot.types(), anyTypes))
                    && snapshot.moves().containsAll(allMoves)
                    && (abilities.isEmpty() || snapshot.ability() != null && abilities.contains(snapshot.ability()));
        }
    }

    public record MoveEffect(Set<Id> tags, Map<Id, Double> modifiers) {
        public MoveEffect { tags = Set.copyOf(tags); modifiers = Map.copyOf(modifiers); }
    }

    public record Config(int maxSnapshots, int maxPerActor, LevelCurve levels, Map<Id, Double> baseModifiers,
                         Set<Id> baseTags, List<Rule> rules, Map<Id, MoveEffect> moveEffects) {
        public Config {
            baseModifiers = Map.copyOf(baseModifiers); baseTags = Set.copyOf(baseTags); rules = List.copyOf(rules); moveEffects = Map.copyOf(moveEffects);
            if (maxSnapshots < 1 || maxSnapshots > 100_000 || maxPerActor < 1 || maxPerActor > 10_000
                    || baseModifiers.size() > 256 || baseTags.size() > 256 || rules.size() > 4096 || moveEffects.size() > 100_000)
                throw new ConfigException("Loadout definition limits");
        }

        public static Config parse(Node n) {
            n.only("max_snapshots", "max_per_actor", "level_curve", "base_modifiers", "base_tags", "rules", "move_effects");
            Node level = n.node("level_curve"); level.only("minimum", "center", "maximum", "minimum_multiplier", "maximum_multiplier");
            LevelCurve curve = new LevelCurve((int) level.integer("minimum", 1, 10_000), (int) level.integer("center", 1, 10_000),
                    (int) level.integer("maximum", 1, 10_000), Numbers.decimal(level, "minimum_multiplier", 0.01, 100),
                    Numbers.decimal(level, "maximum_multiplier", 0.01, 100));
            Map<Id, Double> baseModifiers = numericMap(n, "base_modifiers"); Set<Id> baseTags = ids(n, "base_tags");
            List<Rule> rules = new ArrayList<>();
            if (n.has("rules")) for (Node r : n.nodes("rules")) {
                r.only("species", "forms", "all_aspects", "any_types", "all_moves", "abilities", "tags", "modifiers");
                rules.add(new Rule(ids(r, "species"), strings(r, "forms"), ids(r, "all_aspects"), ids(r, "any_types"), ids(r, "all_moves"),
                        ids(r, "abilities"), ids(r, "tags"), numericMap(r, "modifiers")));
            }
            Map<Id, MoveEffect> effects = new LinkedHashMap<>();
            if (n.has("move_effects")) {
                Node values = n.node("move_effects");
                for (String raw : values.values().keySet()) {
                    Node e = values.node(raw); e.only("tags", "modifiers");
                    effects.put(Id.of(raw), new MoveEffect(ids(e, "tags"), numericMap(e, "modifiers")));
                }
            }
            return new Config((int) n.integer("max_snapshots", 1, 100_000), (int) n.integer("max_per_actor", 1, 10_000), curve,
                    baseModifiers, baseTags, rules, effects);
        }

        private static Set<Id> ids(Node n, String field) {
            if (!n.has(field)) return Set.of(); Set<Id> result = new LinkedHashSet<>(); n.strings(field).forEach(v -> result.add(Id.of(v))); return Set.copyOf(result);
        }
        private static Set<String> strings(Node n, String field) {
            if (!n.has(field)) return Set.of(); Set<String> result = new LinkedHashSet<>();
            for (String value : n.strings(field)) if (value.length() > 160 || !result.add(value)) throw n.error(field, "Invalid string set");
            return Set.copyOf(result);
        }
        private static Map<Id, Double> numericMap(Node n, String field) {
            if (!n.has(field)) return Map.of(); Map<Id, Double> result = new LinkedHashMap<>(); Node values = n.node(field);
            for (String raw : values.values().keySet()) result.put(Id.of(raw), Numbers.decimal(values, raw, -1_000_000, 1_000_000));
            return Map.copyOf(result);
        }
    }

    public static final class Plan implements SystemSchema, SystemFactory {
        @Override public void validate(Node config) { Config.parse(config); }
        @Override public Set<SessionServices.Key<?>> provides() { return Set.of(ACCESS); }
        @Override public SessionSystem create(GenericSession session, Node config) {
            LoadoutSystem system = new LoadoutSystem(Config.parse(config), session); session.services().provide(ACCESS, system); return system;
        }
    }

    private record Entry(UUID owner, Snapshot snapshot, Derived derived) { }
    private final Config config;
    private final GenericSession session;
    private final ThreadGuard thread;
    private final NavigableMap<String, Entry> entries = new TreeMap<>();
    private long revision;
    private boolean active, closed;

    private LoadoutSystem(Config config, GenericSession session) { this.config = config; this.session = session; thread = session.thread(); }
    private void requireActive() { thread.check(); if (!active || closed) throw new IllegalStateException("Loadouts inactive"); }
    @Override public void start() { thread.check(); if (active || closed) throw new IllegalStateException("Loadouts already initialized"); active = true; }
    @Override public long revision() { requireActive(); return revision; }

    @Override public StateChange prepareRegister(UUID owner, Snapshot snapshot) {
        requireActive(); validateOwner(owner); Snapshot checked = validateSnapshot(snapshot);
        if (entries.containsKey(checked.sourceId()) || entries.size() >= config.maxSnapshots() || ownedCount(owner) >= config.maxPerActor() || revision == Long.MAX_VALUE)
            throw new IllegalStateException("Loadout registration unavailable");
        Derived derived = derive(checked); long expected = revision;
        return new StateChange() {
            private int used;
            @Override public void apply() {
                requireActive(); if (used != 0 || revision != expected || entries.containsKey(checked.sourceId())) throw new IllegalStateException("Stale loadout registration");
                entries.put(checked.sourceId(), new Entry(owner, checked, derived)); revision++; used = 1;
            }
            @Override public void rollback() {
                requireActive(); if (used != 1 || revision != expected + 1 || entries.remove(checked.sourceId()) == null) throw new IllegalStateException("Loadout rollback conflict");
                revision = expected; used = 2;
            }
        };
    }

    @Override public Optional<Snapshot> snapshot(String sourceId) { requireActive(); Entry e = entries.get(source(sourceId)); return e == null ? Optional.empty() : Optional.of(e.snapshot()); }
    @Override public Optional<Derived> derived(String sourceId) { requireActive(); Entry e = entries.get(source(sourceId)); return e == null ? Optional.empty() : Optional.of(e.derived()); }
    @Override public List<Snapshot> owned(UUID owner) {
        requireActive(); validateOwner(owner); return entries.values().stream().filter(e -> e.owner().equals(owner)).map(Entry::snapshot).toList();
    }

    private long ownedCount(UUID owner) { return entries.values().stream().filter(e -> e.owner().equals(owner)).count(); }
    private void validateOwner(UUID owner) {
        Participant participant = session.participants().get(Objects.requireNonNull(owner));
        if (participant == null || participant.kind() == Participant.Kind.SPECTATOR) throw new IllegalArgumentException("Invalid loadout owner");
    }
    private Snapshot validateSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot); String source = source(snapshot.sourceId());
        if (snapshot.level() < 1 || snapshot.level() > 10_000 || snapshot.form().length() > 160 || snapshot.heldItem().length() > 256
                || snapshot.aspects().size() > 128 || snapshot.types().isEmpty() || snapshot.types().size() > 16 || snapshot.moves().size() > 64)
            throw new IllegalArgumentException("Invalid source snapshot");
        return new Snapshot(source, snapshot.species(), snapshot.form(), snapshot.aspects(), snapshot.types(), snapshot.level(), snapshot.moves(), snapshot.ability(), snapshot.heldItem());
    }
    private static String source(String source) {
        Objects.requireNonNull(source); if (source.isBlank() || source.length() > 256 || !source.equals(source.trim())) throw new IllegalArgumentException("Invalid canonical source id"); return source;
    }

    private Derived derive(Snapshot snapshot) {
        Set<Id> tags = new LinkedHashSet<>(config.baseTags()); Map<Id, Double> modifiers = new TreeMap<>(config.baseModifiers());
        double levelMultiplier = config.levels().multiplier(snapshot.level());
        modifiers.merge(Id.of("svarcade:level_multiplier_delta"), levelMultiplier - 1.0, Double::sum);
        for (Rule rule : config.rules()) if (rule.matches(snapshot)) { tags.addAll(rule.tags()); merge(modifiers, rule.modifiers()); }
        for (Id move : snapshot.moves()) {
            MoveEffect effect = config.moveEffects().get(move); if (effect != null) { tags.addAll(effect.tags()); merge(modifiers, effect.modifiers()); }
        }
        modifiers.values().forEach(v -> { if (!Double.isFinite(v) || Math.abs(v) > 1_000_000) throw new ConfigException("Derived loadout modifier overflow"); });
        return new Derived(snapshot.sourceId(), levelMultiplier, tags, modifiers);
    }
    private static void merge(Map<Id, Double> target, Map<Id, Double> source) { source.forEach((id, value) -> target.merge(id, value, Double::sum)); }

    @Override public void tick(long tick) { requireActive(); }
    @Override public int stateSchema() { return 1; }
    @Override public Map<String, Object> snapshot() {
        requireActive(); List<Object> rows = new ArrayList<>();
        for (Entry entry : entries.values()) {
            Snapshot s = entry.snapshot(); Map<String,Object> row = new LinkedHashMap<>();
            row.put("owner", entry.owner().toString()); row.put("source", s.sourceId()); row.put("species", s.species().toString()); row.put("form", s.form());
            row.put("aspects", s.aspects().stream().map(Id::toString).sorted().toList()); row.put("types", s.types().stream().map(Id::toString).sorted().toList()); row.put("level", s.level());
            row.put("moves", s.moves().stream().map(Id::toString).sorted().toList()); row.put("ability", s.ability() == null ? "" : s.ability().toString()); row.put("held_item", s.heldItem()); rows.add(row);
        }
        return Values.map(Map.of("revision", revision, "snapshots", rows));
    }

    @Override public void restore(int schema, Map<String, Object> saved) {
        thread.check(); if (active || closed || schema != 1) throw new IllegalArgumentException("Invalid loadout restore"); Node n = new Node(saved, "loadout-state"); n.only("revision", "snapshots");
        if (n.list("snapshots").size() > config.maxSnapshots()) throw new ConfigException("Restored loadout capacity"); NavigableMap<String, Entry> restored = new TreeMap<>();
        for (Node row : n.nodes("snapshots")) {
            row.only("owner", "source", "species", "form", "aspects", "types", "level", "moves", "ability", "held_item"); UUID owner = UUID.fromString(row.string("owner")); validateOwner(owner);
            Set<Id> aspects = ids(row.strings("aspects")), types = ids(row.strings("types")), moves = ids(row.strings("moves")); String abilityRaw = row.string("ability");
            Snapshot snapshot = validateSnapshot(new Snapshot(row.string("source"), Id.of(row.string("species")), row.string("form"), aspects, types,
                    (int) row.integer("level", 1, 10_000), moves, abilityRaw.isEmpty() ? null : Id.of(abilityRaw), row.string("held_item")));
            if (restored.putIfAbsent(snapshot.sourceId(), new Entry(owner, snapshot, derive(snapshot))) != null) throw new ConfigException("Duplicate restored loadout source");
        }
        for (Participant participant : session.participants().values()) if (participant.kind() != Participant.Kind.SPECTATOR) {
            long count = restored.values().stream().filter(e -> e.owner().equals(participant.id())).count(); if (count > config.maxPerActor()) throw new ConfigException("Restored loadout owner cap");
        }
        entries.putAll(restored); revision = n.integer("revision", 0, Long.MAX_VALUE - 1); active = true;
    }
    private static Set<Id> ids(List<String> values) { Set<Id> result = new LinkedHashSet<>(); values.forEach(v -> result.add(Id.of(v))); return Set.copyOf(result); }
    @Override public void close() { thread.check(); entries.clear(); active = false; closed = true; }
    private static boolean finite(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
}
