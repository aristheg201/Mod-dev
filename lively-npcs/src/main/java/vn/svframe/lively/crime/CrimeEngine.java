package vn.svframe.lively.crime;

import vn.svframe.lively.actor.ActorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Crime simulation is evidence-based and semantic. It never mutates terrain or player containers. */
public final class CrimeEngine {
    public enum Type { MURDER, THEFT, ASSAULT, MISSING_PERSON, TRESPASSING, FRAUD, FACTION_CRIME }
    public enum Status { OPEN, INVESTIGATING, CHARGED, RESOLVED, COLD, DISMISSED }
    public enum EvidenceType { WITNESS, PHYSICAL, TIMELINE, MOTIVE, OPPORTUNITY, ALIBI, RECORD, RUMOR }

    public record Crime(UUID id, Type type, ActorId victim, ActorId perpetrator, String locationId,
                        Instant occurredAt, Status status, String motive, Set<ActorId> witnesses,
                        Map<String, String> facts, long revision) {
        public Crime {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(occurredAt); Objects.requireNonNull(status);
            if (locationId != null && locationId.length() > 128) throw new IllegalArgumentException("location id too long");
            motive = motive == null ? "" : motive; witnesses = Set.copyOf(witnesses); facts = Map.copyOf(facts);
        }
        public Crime withStatus(Status next, long nextRevision) {
            return new Crime(id, type, victim, perpetrator, locationId, occurredAt, next, motive, witnesses, facts, nextRevision);
        }
        public Crime withFacts(Map<String, String> nextFacts, long nextRevision) {
            return new Crime(id, type, victim, perpetrator, locationId, occurredAt, status, motive, witnesses, nextFacts, nextRevision);
        }
    }

    public record Evidence(UUID id, UUID crimeId, EvidenceType type, ActorId source, ActorId subject,
                           double reliability, double relevance, Instant discoveredAt, boolean publicKnowledge,
                           Map<String, String> facts) {
        public Evidence {
            Objects.requireNonNull(id); Objects.requireNonNull(crimeId); Objects.requireNonNull(type); Objects.requireNonNull(discoveredAt);
            reliability = unit(reliability); relevance = unit(relevance); facts = Map.copyOf(facts);
        }
        public double weight() { return reliability * relevance; }
    }

    public record SuspectScore(ActorId suspect, double score, double motive, double opportunity,
                               double evidence, double alibiStrength, List<UUID> evidenceIds) {}

    private final ConcurrentHashMap<UUID, Crime> crimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Evidence> evidence = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public Crime create(Type type, ActorId victim, ActorId perpetrator, String locationId, String motive,
                        Set<ActorId> witnesses, Map<String, String> facts) {
        if (crimes.size() >= 100_000) throw new IllegalStateException("crime_limit");
        long rev = revision.incrementAndGet();
        Crime crime = new Crime(UUID.randomUUID(), type, victim, perpetrator, locationId, Instant.now(), Status.OPEN,
                motive, witnesses, sanitizeFacts(facts));
        crimes.put(crime.id(), crime); return crime;
    }

    private Crime new Crime(UUID id, Type type, ActorId victim, ActorId perpetrator, String locationId, Instant occurredAt,
                            Status status, String motive, Set<ActorId> witnesses, Map<String, String> facts) {
        return new Crime(id, type, victim, perpetrator, locationId, occurredAt, status, motive, witnesses, facts, revision.get());
    }

    public Optional<Crime> status(UUID crimeId, Status next) {
        Objects.requireNonNull(next);
        AtomicReference<Crime> changed = new AtomicReference<>();
        crimes.computeIfPresent(crimeId, (id, old) -> {
            if (old.status() == next) { changed.set(old); return old; }
            Crime value = old.withStatus(next, revision.incrementAndGet());
            changed.set(value);
            return value;
        });
        return Optional.ofNullable(changed.get());
    }

    /** Adds bounded semantic metadata such as charged suspect, review time or wrong-charge history. */
    public Optional<Crime> updateFacts(UUID crimeId, Map<String, String> changes) {
        if (changes == null || changes.isEmpty() || changes.size() > 32) return Optional.empty();
        AtomicReference<Crime> changed = new AtomicReference<>();
        crimes.computeIfPresent(crimeId, (id, old) -> {
            Map<String, String> facts = new HashMap<>(old.facts());
            boolean modified = false;
            for (Map.Entry<String, String> entry : changes.entrySet()) {
                String key = entry.getKey(); String value = entry.getValue();
                if (key == null || key.isBlank() || key.length() > 64 || value == null || value.length() > 512) continue;
                String previous = facts.put(key, value);
                if (!Objects.equals(previous, value)) modified = true;
            }
            Crime value = modified ? old.withFacts(facts, revision.incrementAndGet()) : old;
            changed.set(value);
            return value;
        });
        return Optional.ofNullable(changed.get());
    }

    public Evidence addEvidence(UUID crimeId, EvidenceType type, ActorId source, ActorId subject,
                                double reliability, double relevance, boolean publicKnowledge, Map<String, String> facts) {
        if (!crimes.containsKey(crimeId)) throw new IllegalArgumentException("unknown crime");
        if (evidence.size() >= 1_000_000) throw new IllegalStateException("evidence_limit");
        Evidence item = new Evidence(UUID.randomUUID(), crimeId, type, source, subject, reliability, relevance,
                Instant.now(), publicKnowledge, sanitizeFacts(facts));
        evidence.put(item.id(), item); revision.incrementAndGet(); return item;
    }

    public List<SuspectScore> rankSuspects(UUID crimeId, Set<ActorId> candidates) {
        if (!crimes.containsKey(crimeId)) return List.of();
        List<Evidence> items = evidence.values().stream().filter(e -> e.crimeId().equals(crimeId)).toList();
        List<SuspectScore> scores = new ArrayList<>();
        for (ActorId candidate : candidates.stream().limit(256).toList()) {
            double motive = 0D, opportunity = 0D, support = 0D, alibi = 0D;
            List<UUID> ids = new ArrayList<>();
            for (Evidence item : items) {
                if (item.subject() == null || !item.subject().equals(candidate)) continue;
                ids.add(item.id());
                switch (item.type()) {
                    case MOTIVE -> motive += item.weight();
                    case OPPORTUNITY, TIMELINE, PHYSICAL, WITNESS, RECORD -> support += item.weight();
                    case ALIBI -> alibi += item.weight();
                    case RUMOR -> support += item.weight() * 0.25D;
                }
                if (item.type() == EvidenceType.OPPORTUNITY) opportunity += item.weight();
            }
            double score = unit(motive * 0.22D + opportunity * 0.23D + support * 0.55D - alibi * 0.65D);
            scores.add(new SuspectScore(candidate, score, unit(motive), unit(opportunity), unit(support), unit(alibi), List.copyOf(ids)));
        }
        scores.sort(Comparator.comparingDouble(SuspectScore::score).reversed()
                .thenComparing(score -> score.suspect().uuid().toString()));
        return List.copyOf(scores);
    }

    public Optional<Crime> crime(UUID id) { return Optional.ofNullable(crimes.get(id)); }
    public List<Evidence> evidence(UUID crimeId) { return evidence.values().stream().filter(e -> e.crimeId().equals(crimeId)).toList(); }
    public Snapshot snapshot() { return new Snapshot(revision.get(), Map.copyOf(crimes), Map.copyOf(evidence)); }
    public void restore(Snapshot snapshot) { crimes.clear(); crimes.putAll(snapshot.crimes()); evidence.clear(); evidence.putAll(snapshot.evidence()); revision.set(snapshot.revision()); }

    public record Snapshot(long revision, Map<UUID, Crime> crimes, Map<UUID, Evidence> evidence) {
        public Snapshot { crimes = Map.copyOf(crimes); evidence = Map.copyOf(evidence); }
    }

    private static Map<String, String> sanitizeFacts(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (result.size() >= 128) break;
            String key = entry.getKey(), value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64 || value == null || value.length() > 512) continue;
            result.put(key, value);
        }
        return Map.copyOf(result);
    }
    private static double unit(double v) { return Math.max(0D, Math.min(1D, v)); }
}
