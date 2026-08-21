package vn.svframe.lively.faction;

import vn.svframe.lively.actor.ActorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Strategic faction mind. Factions hold goals, resources, relations and share only permitted knowledge. */
public final class FactionEngine {
    private static final int MAX_MEMBERS = 4096;
    private static final int MAX_RESOURCES = 128;
    private static final long MAX_RESOURCE_VALUE = 10_000_000_000_000L;

    public record Faction(UUID id, String name, Set<ActorId> members, Map<String, Long> resources,
                          Map<String, Double> goals, Map<String, String> knowledge, long revision) {
        public Faction {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            members = Set.copyOf(members); resources = Map.copyOf(resources); goals = Map.copyOf(goals); knowledge = Map.copyOf(knowledge);
            if (members.size() > MAX_MEMBERS) throw new IllegalArgumentException("too many faction members");
            if (resources.size() > MAX_RESOURCES) throw new IllegalArgumentException("too many faction resources");
        }
    }
    public record Relation(UUID a, UUID b, double trust, double hostility, double influence, long revision) {
        public Relation { trust = signed(trust); hostility = unit(hostility); influence = signed(influence); }
    }
    public record Strategy(UUID factionId, String action, double utility, Map<String, String> facts, Instant generatedAt) {
        public Strategy { utility = unit(utility); facts = Map.copyOf(facts); }
    }

    private final ConcurrentHashMap<UUID, Faction> factions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Relation> relations = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Faction create(String name, Set<ActorId> members, Map<String, Long> resources, Map<String, Double> goals) {
        if (name == null || name.isBlank() || name.length() > 128) throw new IllegalArgumentException("invalid faction name");
        Set<ActorId> boundedMembers = members == null ? Set.of() : members.stream().limit(MAX_MEMBERS).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Faction faction = new Faction(UUID.randomUUID(), name, boundedMembers, normalizeResources(resources), normalize(goals), Map.of(), revision.incrementAndGet());
        factions.put(faction.id(), faction); return faction;
    }

    public Optional<Faction> updateKnowledge(UUID id, String key, String value) {
        if (key == null || key.isBlank() || key.length() > 96 || value == null || value.length() > 512) return Optional.empty();
        return Optional.ofNullable(factions.computeIfPresent(id, (k, old) -> {
            Map<String, String> knowledge = new HashMap<>(old.knowledge());
            if (knowledge.size() >= 256 && !knowledge.containsKey(key)) return old;
            knowledge.put(key, value);
            return new Faction(old.id(), old.name(), old.members(), old.resources(), old.goals(), knowledge, revision.incrementAndGet());
        }));
    }

    public Optional<Faction> adjustResource(UUID id, String resource, long delta) {
        if (resource == null || resource.isBlank() || resource.length() > 96 || delta == 0L) return Optional.empty();
        return Optional.ofNullable(factions.computeIfPresent(id, (key, old) -> {
            Map<String, Long> resources = new HashMap<>(old.resources());
            if (resources.size() >= MAX_RESOURCES && !resources.containsKey(resource)) return old;
            long current = resources.getOrDefault(resource, 0L);
            long next;
            try { next = Math.addExact(current, delta); }
            catch (ArithmeticException overflow) { next = delta > 0L ? MAX_RESOURCE_VALUE : 0L; }
            resources.put(resource, Math.max(0L, Math.min(MAX_RESOURCE_VALUE, next)));
            return new Faction(old.id(), old.name(), old.members(), resources, old.goals(), old.knowledge(), revision.incrementAndGet());
        }));
    }

    public Optional<Faction> addMember(UUID id, ActorId member) {
        if (member == null) return Optional.empty();
        return Optional.ofNullable(factions.computeIfPresent(id, (key, old) -> {
            if (old.members().contains(member) || old.members().size() >= MAX_MEMBERS) return old;
            HashSet<ActorId> members = new HashSet<>(old.members()); members.add(member);
            return new Faction(old.id(), old.name(), members, old.resources(), old.goals(), old.knowledge(), revision.incrementAndGet());
        }));
    }

    public Optional<Faction> removeMember(UUID id, ActorId member) {
        if (member == null) return Optional.empty();
        return Optional.ofNullable(factions.computeIfPresent(id, (key, old) -> {
            if (!old.members().contains(member)) return old;
            HashSet<ActorId> members = new HashSet<>(old.members()); members.remove(member);
            return new Faction(old.id(), old.name(), members, old.resources(), old.goals(), old.knowledge(), revision.incrementAndGet());
        }));
    }

    public Relation relation(UUID a, UUID b) {
        String key = key(a, b); return relations.computeIfAbsent(key, ignored -> new Relation(a, b, 0D, 0D, 0D, revision.incrementAndGet()));
    }

    public Relation changeRelation(UUID a, UUID b, double trustDelta, double hostilityDelta, double influenceDelta) {
        String key = key(a, b);
        Relation next = relations.compute(key, (k, old) -> {
            Relation base = old == null ? new Relation(a, b, 0D, 0D, 0D, 0L) : old;
            return new Relation(base.a(), base.b(), base.trust() + trustDelta, base.hostility() + hostilityDelta, base.influence() + influenceDelta, revision.incrementAndGet());
        }); return next;
    }

    public List<Strategy> plan(UUID factionId, Map<String, Double> worldSignals) {
        Faction faction = factions.get(factionId); if (faction == null) return List.of();
        List<Strategy> result = new ArrayList<>();
        double crime = unit(worldSignals.getOrDefault("crime", 0D));
        double scarcity = unit(worldSignals.getOrDefault("scarcity", 0D));
        double threat = unit(worldSignals.getOrDefault("threat", 0D));
        double expansion = unit(faction.goals().getOrDefault("expansion", 0D));
        double stability = unit(faction.goals().getOrDefault("stability", 0D));
        result.add(new Strategy(factionId, "increase_patrol", unit(threat * 0.55D + crime * 0.35D + stability * 0.10D), Map.of(), Instant.now()));
        result.add(new Strategy(factionId, "secure_supply", unit(scarcity * 0.70D + stability * 0.20D), Map.of(), Instant.now()));
        result.add(new Strategy(factionId, "recruit", unit(expansion * 0.55D + threat * 0.15D), Map.of(), Instant.now()));
        result.sort(java.util.Comparator.comparingDouble(Strategy::utility).reversed()); return List.copyOf(result);
    }

    public Optional<Faction> get(UUID id) { return Optional.ofNullable(factions.get(id)); }
    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(factions), Map.copyOf(relations)); }
    public void restore(Snapshot snapshot) { factions.clear(); factions.putAll(snapshot.factions()); relations.clear(); relations.putAll(snapshot.relations()); revision.set(snapshot.revision()); }
    public record Snapshot(long revision, Map<UUID, Faction> factions, Map<String, Relation> relations) { public Snapshot { factions = Map.copyOf(factions); relations = Map.copyOf(relations); } }

    private static String key(UUID a, UUID b) { return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a; }
    private static Map<String, Double> normalize(Map<String, Double> map) {
        if (map == null || map.isEmpty()) return Map.of();
        HashMap<String, Double> result = new HashMap<>();
        map.entrySet().stream().limit(128).forEach(entry -> result.put(entry.getKey(), unit(entry.getValue())));
        return Map.copyOf(result);
    }
    private static Map<String, Long> normalizeResources(Map<String, Long> map) {
        if (map == null || map.isEmpty()) return Map.of();
        HashMap<String, Long> result = new HashMap<>();
        map.entrySet().stream().limit(MAX_RESOURCES).forEach(entry -> {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getKey().length() <= 96 && entry.getValue() != null) {
                result.put(entry.getKey(), Math.max(0L, Math.min(MAX_RESOURCE_VALUE, entry.getValue())));
            }
        });
        return Map.copyOf(result);
    }
    private static double unit(double v) { return Math.max(0D, Math.min(1D, v)); }
    private static double signed(double v) { return Math.max(-1D, Math.min(1D, v)); }
}
