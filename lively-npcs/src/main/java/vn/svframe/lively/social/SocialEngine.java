package vn.svframe.lively.social;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.actor.ActorSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared social simulation for NPC<->NPC and NPC<->player relationships.
 * It models evidence-backed relationship vectors, reputation, attraction and rumor propagation.
 */
public final class SocialEngine {
    public enum RelationshipType { STRANGER, ACQUAINTANCE, FRIEND, BEST_FRIEND, RIVAL, ENEMY, FAMILY, PARTNER, ROMANCE }
    public enum ReputationScope { GLOBAL, TOWN, FACTION, OCCUPATION }

    public record Pair(ActorId a, ActorId b) {
        public Pair {
            Objects.requireNonNull(a); Objects.requireNonNull(b);
            if (a.equals(b)) throw new IllegalArgumentException("self relationship");
        }
        public static Pair normalized(ActorId a, ActorId b) {
            int compare = a.uuid().compareTo(b.uuid());
            if (compare == 0) compare = a.kind().compareTo(b.kind());
            return compare <= 0 ? new Pair(a, b) : new Pair(b, a);
        }
    }

    public record Relationship(
            Pair pair,
            double trust,
            double affection,
            double respect,
            double fear,
            double loyalty,
            double attraction,
            double familiarity,
            RelationshipType type,
            long interactions,
            Instant updatedAt,
            List<Evidence> evidence
    ) {
        public Relationship {
            Objects.requireNonNull(pair); Objects.requireNonNull(type); Objects.requireNonNull(updatedAt);
            trust = signed(trust); affection = signed(affection); respect = signed(respect);
            fear = unit(fear); loyalty = signed(loyalty); attraction = unit(attraction); familiarity = unit(familiarity);
            interactions = Math.max(0L, interactions);
            evidence = List.copyOf(evidence.size() > 32 ? evidence.subList(evidence.size() - 32, evidence.size()) : evidence);
        }
        public double hostility() { return unit((-affection + fear + -trust) / 3D); }
    }

    public record Evidence(UUID id, Instant at, String reason, double weight, Map<String, String> facts) {
        public Evidence {
            Objects.requireNonNull(id); Objects.requireNonNull(at); Objects.requireNonNull(reason);
            weight = signed(weight); facts = Map.copyOf(facts);
        }
    }

    public record SocialDelta(double trust, double affection, double respect, double fear,
                              double loyalty, double attraction, double familiarity,
                              String reason, Map<String, String> facts) {
        public SocialDelta {
            Objects.requireNonNull(reason); facts = Map.copyOf(facts);
        }
    }

    public record ReputationKey(ActorId actor, ReputationScope scope, String scopeId) {
        public ReputationKey {
            Objects.requireNonNull(actor); Objects.requireNonNull(scope);
            scopeId = scopeId == null ? "" : scopeId;
        }
    }

    public record Rumor(UUID id, String topic, ActorId subject, ActorId origin, String claim,
                        double confidence, double importance, Instant createdAt, Instant expiresAt,
                        Set<ActorId> carriers, int hops) {
        public Rumor {
            Objects.requireNonNull(id); Objects.requireNonNull(topic); Objects.requireNonNull(subject);
            Objects.requireNonNull(origin); Objects.requireNonNull(claim); Objects.requireNonNull(createdAt); Objects.requireNonNull(expiresAt);
            confidence = unit(confidence); importance = unit(importance); carriers = Set.copyOf(carriers); hops = Math.max(0, hops);
        }
        public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    }

    private final ConcurrentHashMap<Pair, Relationship> relationships = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ReputationKey, Double> reputation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Rumor> rumors = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Relationship relationship(ActorId a, ActorId b) {
        Pair pair = Pair.normalized(a, b);
        return relationships.computeIfAbsent(pair, key -> new Relationship(key, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                RelationshipType.STRANGER, 0L, Instant.now(), List.of()));
    }

    public Relationship apply(ActorId a, ActorId b, SocialDelta delta) {
        Objects.requireNonNull(delta);
        Pair pair = Pair.normalized(a, b);
        Relationship next = relationships.compute(pair, (key, old) -> {
            Relationship base = old == null ? relationship(a, b) : old;
            List<Evidence> evidence = new ArrayList<>(base.evidence());
            double importance = Math.max(Math.abs(delta.trust()), Math.max(Math.abs(delta.affection()), Math.abs(delta.respect())));
            evidence.add(new Evidence(UUID.randomUUID(), Instant.now(), delta.reason(), signed(importance), delta.facts()));
            double trust = base.trust() + delta.trust();
            double affection = base.affection() + delta.affection();
            double respect = base.respect() + delta.respect();
            double fear = base.fear() + delta.fear();
            double loyalty = base.loyalty() + delta.loyalty();
            double attraction = base.attraction() + delta.attraction();
            double familiarity = base.familiarity() + Math.max(0.01D, delta.familiarity());
            RelationshipType type = classify(trust, affection, fear, loyalty, attraction, familiarity, base.type());
            return new Relationship(key, trust, affection, respect, fear, loyalty, attraction, familiarity,
                    type, base.interactions() + 1L, Instant.now(), evidence);
        });
        revision.incrementAndGet();
        return next;
    }

    public double reputation(ActorId actor, ReputationScope scope, String scopeId) {
        return reputation.getOrDefault(new ReputationKey(actor, scope, scopeId), 0D);
    }

    public double changeReputation(ActorId actor, ReputationScope scope, String scopeId, double delta) {
        double value = reputation.merge(new ReputationKey(actor, scope, scopeId), signed(delta), (a, b) -> signed(a + b));
        revision.incrementAndGet();
        return value;
    }

    public Rumor createRumor(String topic, ActorId subject, ActorId origin, String claim,
                             double confidence, double importance, Duration lifetime) {
        Objects.requireNonNull(lifetime);
        if (topic.isBlank() || claim.isBlank() || claim.length() > 512) throw new IllegalArgumentException("invalid rumor");
        Duration bounded = lifetime.compareTo(Duration.ofDays(30)) > 0 ? Duration.ofDays(30) : lifetime;
        if (bounded.isNegative() || bounded.isZero()) throw new IllegalArgumentException("invalid rumor lifetime");
        Instant now = Instant.now();
        Rumor rumor = new Rumor(UUID.randomUUID(), topic, subject, origin, claim, confidence, importance,
                now, now.plus(bounded), Set.of(origin), 0);
        rumors.put(rumor.id(), rumor); revision.incrementAndGet();
        return rumor;
    }

    /** Propagates only if the carrier trusts the receiver enough and the rumor still has signal. */
    public Optional<Rumor> propagate(UUID rumorId, ActorId carrier, ActorId receiver) {
        Rumor rumor = rumors.get(rumorId);
        if (rumor == null || rumor.expired(Instant.now()) || rumor.hops() >= 12 || rumor.carriers().contains(receiver)) return Optional.empty();
        Relationship relation = relationship(carrier, receiver);
        double transfer = unit(0.58D + relation.trust() * 0.22D + relation.familiarity() * 0.10D);
        double confidence = rumor.confidence() * transfer * Math.pow(0.93D, rumor.hops());
        if (confidence < 0.12D) return Optional.empty();
        java.util.HashSet<ActorId> carriers = new java.util.HashSet<>(rumor.carriers()); carriers.add(receiver);
        Rumor next = new Rumor(rumor.id(), rumor.topic(), rumor.subject(), rumor.origin(), rumor.claim(), confidence,
                rumor.importance(), rumor.createdAt(), rumor.expiresAt(), carriers, rumor.hops() + 1);
        rumors.put(rumorId, next); revision.incrementAndGet();
        return Optional.of(next);
    }

    public double compatibility(ActorRegistry actors, ActorId a, ActorId b) {
        ActorSnapshot left = actors.get(a).orElse(null); ActorSnapshot right = actors.get(b).orElse(null);
        Relationship relationship = relationship(a, b);
        double personality = 0.5D;
        if (left != null && right != null) {
            personality = 1D - averageDifference(left.socialStats(), right.socialStats());
        }
        return unit(personality * 0.32D + relationship.trust() * 0.18D + relationship.affection() * 0.20D
                + relationship.attraction() * 0.18D + relationship.familiarity() * 0.12D);
    }

    public List<Relationship> strongestRelations(ActorId actor, int limit) {
        return relationships.values().stream().filter(r -> r.pair().a().equals(actor) || r.pair().b().equals(actor))
                .sorted(Comparator.comparingDouble((Relationship r) -> Math.abs(r.affection()) + Math.abs(r.trust()) + r.fear()).reversed())
                .limit(Math.max(1, Math.min(100, limit))).toList();
    }

    public int expireRumors(Instant now) {
        int before = rumors.size(); rumors.entrySet().removeIf(e -> e.getValue().expired(now));
        int removed = before - rumors.size(); if (removed > 0) revision.addAndGet(removed); return removed;
    }

    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(relationships), Map.copyOf(reputation), Map.copyOf(rumors)); }

    public void restore(Snapshot snapshot) {
        relationships.clear(); relationships.putAll(snapshot.relationships());
        reputation.clear(); reputation.putAll(snapshot.reputation());
        rumors.clear(); rumors.putAll(snapshot.rumors()); revision.set(Math.max(0L, snapshot.revision()));
    }

    public record Snapshot(long revision, Map<Pair, Relationship> relationships,
                           Map<ReputationKey, Double> reputation, Map<UUID, Rumor> rumors) {
        public Snapshot { relationships = Map.copyOf(relationships); reputation = Map.copyOf(reputation); rumors = Map.copyOf(rumors); }
    }

    private static RelationshipType classify(double trust, double affection, double fear, double loyalty,
                                             double attraction, double familiarity, RelationshipType old) {
        if (old == RelationshipType.FAMILY) return old;
        if (attraction > 0.75D && affection > 0.55D && trust > 0.45D) return RelationshipType.ROMANCE;
        if (loyalty > 0.78D && affection > 0.62D) return RelationshipType.PARTNER;
        if (fear > 0.70D && affection < -0.45D) return RelationshipType.ENEMY;
        if (affection < -0.55D || trust < -0.65D) return RelationshipType.RIVAL;
        if (affection > 0.78D && trust > 0.65D) return RelationshipType.BEST_FRIEND;
        if (affection > 0.45D && trust > 0.30D) return RelationshipType.FRIEND;
        if (familiarity > 0.20D) return RelationshipType.ACQUAINTANCE;
        return RelationshipType.STRANGER;
    }

    private static double averageDifference(Map<String, Double> a, Map<String, Double> b) {
        java.util.HashSet<String> keys = new java.util.HashSet<>(a.keySet()); keys.addAll(b.keySet());
        if (keys.isEmpty()) return 0.5D;
        double sum = 0D; for (String key : keys) sum += Math.abs(a.getOrDefault(key, 0.5D) - b.getOrDefault(key, 0.5D));
        return unit(sum / keys.size());
    }
    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
    private static double signed(double value) { return Math.max(-1D, Math.min(1D, value)); }
}
