package vn.svframe.lively.social;

import vn.svframe.lively.actor.ActorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

/** Persistent household and kinship model. It never spawns children or mutates actors implicitly. */
public final class FamilyEngine {
    public enum KinshipType { SPOUSE, PARENT, CHILD, SIBLING, GUARDIAN, DEPENDENT }

    public record Kinship(ActorId from, ActorId to, KinshipType type, double confidence, Instant since, Map<String, String> facts) {
        public Kinship {
            Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(type); Objects.requireNonNull(since);
            if (from.equals(to)) throw new IllegalArgumentException("self kinship");
            confidence = Math.max(0D, Math.min(1D, confidence));
            facts = Map.copyOf(facts);
        }
    }

    public record Household(UUID id, String homeStructure, Set<ActorId> members, Map<ActorId, String> roles,
                            Instant createdAt, long revision, Map<String, String> facts) {
        public Household {
            Objects.requireNonNull(id); Objects.requireNonNull(createdAt);
            members = Set.copyOf(members); roles = Map.copyOf(roles); facts = Map.copyOf(facts);
            if (members.size() > 64) throw new IllegalArgumentException("household too large");
        }
    }

    private final ConcurrentHashMap<UUID, Household> households = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Kinship> kinships = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Household createHousehold(String homeStructure, Set<ActorId> members, Map<ActorId, String> roles, Map<String, String> facts) {
        if (members.isEmpty()) throw new IllegalArgumentException("household needs members");
        if (!members.containsAll(roles.keySet())) throw new IllegalArgumentException("household roles contain non-member");
        Household household = new Household(UUID.randomUUID(), blankToNull(homeStructure), members, roles, Instant.now(), revision.incrementAndGet(), facts);
        households.put(household.id(), household);
        return household;
    }

    public Household ensureSpouseHousehold(ActorId a, ActorId b, String homeStructure) {
        link(a, b, KinshipType.SPOUSE, 1D, Map.of("source", "marriage"));
        Optional<Household> existing = householdOf(a).filter(h -> h.members().contains(b));
        if (existing.isPresent()) return existing.get();
        Household base = householdOf(a).or(() -> householdOf(b)).orElse(null);
        if (base == null) {
            return createHousehold(homeStructure, Set.of(a, b), Map.of(a, "spouse", b, "spouse"), Map.of("formed_by", "marriage"));
        }
        HashSet<ActorId> members = new HashSet<>(base.members());
        members.add(a); members.add(b);
        HashMap<ActorId, String> roles = new HashMap<>(base.roles());
        roles.put(a, "spouse"); roles.put(b, "spouse");
        Household updated = new Household(base.id(), blankToNull(homeStructure) == null ? base.homeStructure() : homeStructure,
                members, roles, base.createdAt(), revision.incrementAndGet(), base.facts());
        households.put(updated.id(), updated);
        return updated;
    }

    public void linkParentChild(ActorId parent, ActorId child, double confidence, Map<String, String> facts) {
        link(parent, child, KinshipType.PARENT, confidence, facts);
        link(child, parent, KinshipType.CHILD, confidence, facts);
        for (ActorId sibling : childrenOf(parent)) {
            if (!sibling.equals(child)) {
                link(child, sibling, KinshipType.SIBLING, confidence, Map.of("shared_parent", parent.uuid().toString()));
                link(sibling, child, KinshipType.SIBLING, confidence, Map.of("shared_parent", parent.uuid().toString()));
            }
        }
    }

    public Kinship link(ActorId from, ActorId to, KinshipType type, double confidence, Map<String, String> facts) {
        Kinship kinship = new Kinship(from, to, type, confidence, Instant.now(), facts);
        kinships.put(key(from, to, type), kinship); revision.incrementAndGet(); return kinship;
    }

    public Optional<Household> householdOf(ActorId actor) {
        return households.values().stream().filter(h -> h.members().contains(actor))
                .min(Comparator.comparing(Household::createdAt));
    }

    public List<Kinship> kinshipsOf(ActorId actor) {
        return kinships.values().stream().filter(k -> k.from().equals(actor) || k.to().equals(actor)).toList();
    }

    public Set<ActorId> childrenOf(ActorId parent) {
        return kinships.values().stream().filter(k -> k.from().equals(parent) && k.type() == KinshipType.PARENT)
                .map(Kinship::to).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean removeHousehold(UUID id) { boolean removed = households.remove(id) != null; if (removed) revision.incrementAndGet(); return removed; }
    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(households), Map.copyOf(kinships)); }
    public void restore(Snapshot snapshot) {
        households.clear(); households.putAll(snapshot.households());
        kinships.clear(); kinships.putAll(snapshot.kinships()); revision.set(Math.max(0L, snapshot.revision()));
    }

    public record Snapshot(long revision, Map<UUID, Household> households, Map<String, Kinship> kinships) {
        public Snapshot { households = Map.copyOf(households); kinships = Map.copyOf(kinships); }
    }

    private static String key(ActorId from, ActorId to, KinshipType type) { return from.kind() + ":" + from.uuid() + ">" + to.kind() + ":" + to.uuid() + ":" + type; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
