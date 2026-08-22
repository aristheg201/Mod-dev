package vn.svframe.lively.law;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Persistent semantic justice state. The engine never guesses ground truth from a crime's hidden perpetrator field:
 * warrants, convictions and reversals are driven by evidence scores supplied by the investigation layer.
 */
public final class LawEnforcementEngine {
    public enum WantedLevel { NONE, PERSON_OF_INTEREST, WANTED, HIGH_RISK, FUGITIVE }
    public enum WarrantStatus { ACTIVE, SERVED, REVOKED, EXPIRED }
    public enum CustodyStatus { DETAINED, JAILED, RELEASED, ESCAPED }
    public enum CourtStatus { FILED, HEARING, CONVICTED, ACQUITTED, DISMISSED, OVERTURNED }

    public record WantedKey(ActorId subject, String jurisdiction) {
        public WantedKey {
            Objects.requireNonNull(subject);
            jurisdiction = normalizeJurisdiction(jurisdiction);
        }
    }

    public record WantedRecord(ActorId subject, String jurisdiction, int points, long bounty,
                               Set<UUID> crimeIds, WantedLevel level, Instant updatedAt, long revision) {
        public WantedRecord {
            Objects.requireNonNull(subject); Objects.requireNonNull(level); Objects.requireNonNull(updatedAt);
            jurisdiction = normalizeJurisdiction(jurisdiction);
            points = Math.max(0, Math.min(1_000_000, points));
            bounty = Math.max(0L, Math.min(10_000_000_000_000L, bounty));
            crimeIds = Set.copyOf(crimeIds == null ? Set.of() : crimeIds);
        }
    }

    public record Warrant(UUID id, ActorId subject, String jurisdiction, Set<UUID> crimeIds,
                          double probableCause, Instant issuedAt, Instant expiresAt,
                          WarrantStatus status, long revision) {
        public Warrant {
            Objects.requireNonNull(id); Objects.requireNonNull(subject); Objects.requireNonNull(issuedAt);
            Objects.requireNonNull(expiresAt); Objects.requireNonNull(status);
            jurisdiction = normalizeJurisdiction(jurisdiction);
            crimeIds = Set.copyOf(crimeIds == null ? Set.of() : crimeIds);
            probableCause = unit(probableCause);
        }
    }

    public record Custody(UUID id, ActorId subject, ActorId officer, UUID warrantId, String jurisdiction,
                          String facilityId, Instant arrestedAt, Instant releaseAt, long fine, long bail,
                          boolean previousAiEnabled, CustodyStatus status, Map<String, String> facts, long revision) {
        public Custody {
            Objects.requireNonNull(id); Objects.requireNonNull(subject); Objects.requireNonNull(arrestedAt);
            Objects.requireNonNull(status);
            jurisdiction = normalizeJurisdiction(jurisdiction);
            facilityId = facilityId == null ? "" : facilityId;
            fine = boundedMoney(fine); bail = boundedMoney(bail);
            facts = sanitizeFacts(facts);
        }
    }

    public record CourtCase(UUID id, ActorId defendant, String jurisdiction, Set<UUID> crimeIds, UUID custodyId,
                            Instant filedAt, Instant hearingAt, Instant decidedAt, CourtStatus status,
                            double evidenceScore, double alibiStrength, int evidenceCount,
                            long fine, long jailSeconds, Map<String, String> facts, long revision) {
        public CourtCase {
            Objects.requireNonNull(id); Objects.requireNonNull(defendant); Objects.requireNonNull(filedAt);
            Objects.requireNonNull(hearingAt); Objects.requireNonNull(status);
            jurisdiction = normalizeJurisdiction(jurisdiction);
            crimeIds = Set.copyOf(crimeIds == null ? Set.of() : crimeIds);
            evidenceScore = unit(evidenceScore); alibiStrength = unit(alibiStrength);
            evidenceCount = Math.max(0, Math.min(1_000_000, evidenceCount));
            fine = boundedMoney(fine); jailSeconds = Math.max(0L, Math.min(31_536_000L, jailSeconds));
            facts = sanitizeFacts(facts);
        }
    }

    private static final int MAX_WARRANTS = 100_000;
    private static final int MAX_CUSTODY = 100_000;
    private static final int MAX_COURT_CASES = 100_000;

    private final ConcurrentHashMap<WantedKey, WantedRecord> wanted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Warrant> warrants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Custody> custody = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CourtCase> courtCases = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    /** One crime contributes to wanted points/bounty once; repeated evidence review is idempotent. */
    public WantedRecord raiseWanted(ActorId subject, String jurisdiction, UUID crimeId, int severity,
                                    double evidenceScore, long bountyUnit) {
        Objects.requireNonNull(subject);
        WantedKey key = new WantedKey(subject, jurisdiction);
        int scorePoints = Math.max(1, Math.min(250, severity + (int) Math.round(unit(evidenceScore) * 60D)));
        long bountyDelta = boundedMoney(Math.max(0L, bountyUnit) * Math.max(1L, severity));
        return wanted.compute(key, (ignored, old) -> {
            HashSet<UUID> crimes = new HashSet<>(old == null ? Set.of() : old.crimeIds());
            boolean repeatedCrime = crimeId != null && crimes.contains(crimeId);
            if (repeatedCrime && old != null) return old;
            if (crimeId != null && crimes.size() < 1024) crimes.add(crimeId);
            int points = old == null ? scorePoints : Math.min(1_000_000, old.points() + scorePoints);
            long bounty = saturatingMoney(old == null ? 0L : old.bounty(), bountyDelta);
            long rev = revision.incrementAndGet();
            return new WantedRecord(subject, key.jurisdiction(), points, bounty, crimes, level(points), Instant.now(), rev);
        });
    }

    public Optional<WantedRecord> wanted(ActorId subject, String jurisdiction) {
        return Optional.ofNullable(wanted.get(new WantedKey(subject, jurisdiction)));
    }

    public List<WantedRecord> wantedFor(ActorId subject) {
        return wanted.values().stream().filter(value -> value.subject().equals(subject))
                .sorted(Comparator.comparingInt(WantedRecord::points).reversed()).toList();
    }

    public Optional<WantedRecord> reduceWanted(ActorId subject, String jurisdiction, int points, long bounty) {
        WantedKey key = new WantedKey(subject, jurisdiction);
        AtomicReference<WantedRecord> result = new AtomicReference<>();
        wanted.computeIfPresent(key, (ignored, old) -> {
            int nextPoints = Math.max(0, old.points() - Math.max(0, points));
            long nextBounty = Math.max(0L, old.bounty() - Math.max(0L, bounty));
            WantedRecord next = new WantedRecord(old.subject(), old.jurisdiction(), nextPoints, nextBounty,
                    nextPoints == 0 ? Set.of() : old.crimeIds(), level(nextPoints), Instant.now(), revision.incrementAndGet());
            result.set(next);
            return nextPoints == 0 && nextBounty == 0L ? null : next;
        });
        return Optional.ofNullable(result.get());
    }

    /** Remove one crime's proportional pressure while preserving unrelated active cases in the same jurisdiction. */
    public Optional<WantedRecord> removeWantedCrime(ActorId subject, String jurisdiction, UUID crimeId) {
        if (crimeId == null) return wanted(subject, jurisdiction);
        WantedKey key = new WantedKey(subject, jurisdiction);
        AtomicReference<WantedRecord> result = new AtomicReference<>();
        wanted.computeIfPresent(key, (ignored, old) -> {
            if (!old.crimeIds().contains(crimeId)) { result.set(old); return old; }
            int oldCount = Math.max(1, old.crimeIds().size());
            HashSet<UUID> crimes = new HashSet<>(old.crimeIds());
            crimes.remove(crimeId);
            int pointShare = old.points() / oldCount + (old.points() % oldCount == 0 ? 0 : 1);
            long bountyShare = old.bounty() / oldCount + (old.bounty() % oldCount == 0L ? 0L : 1L);
            int nextPoints = crimes.isEmpty() ? 0 : Math.max(0, old.points() - pointShare);
            long nextBounty = crimes.isEmpty() ? 0L : Math.max(0L, old.bounty() - bountyShare);
            WantedRecord next = new WantedRecord(old.subject(), old.jurisdiction(), nextPoints, nextBounty, crimes,
                    level(nextPoints), Instant.now(), revision.incrementAndGet());
            result.set(next);
            return crimes.isEmpty() || (nextPoints == 0 && nextBounty == 0L) ? null : next;
        });
        return Optional.ofNullable(result.get());
    }

    public void clearWanted(ActorId subject, String jurisdiction) {
        if (wanted.remove(new WantedKey(subject, jurisdiction)) != null) revision.incrementAndGet();
    }

    public Warrant issueWarrant(ActorId subject, String jurisdiction, Set<UUID> crimeIds, double probableCause,
                                Duration lifetime) {
        Objects.requireNonNull(subject); Objects.requireNonNull(lifetime);
        String normalized = normalizeJurisdiction(jurisdiction);
        Set<UUID> crimes = Set.copyOf(crimeIds == null ? Set.of() : crimeIds);
        Optional<Warrant> existing = warrants.values().stream()
                .filter(warrant -> warrant.subject().equals(subject) && warrant.jurisdiction().equals(normalized)
                        && warrant.status() == WarrantStatus.ACTIVE && warrant.crimeIds().equals(crimes))
                .findFirst();
        if (existing.isPresent()) return existing.get();
        if (warrants.size() >= MAX_WARRANTS) throw new IllegalStateException("warrant_limit");
        Duration safe = lifetime.isNegative() || lifetime.isZero() ? Duration.ofHours(1)
                : lifetime.compareTo(Duration.ofDays(30)) > 0 ? Duration.ofDays(30) : lifetime;
        Instant now = Instant.now();
        Warrant warrant = new Warrant(UUID.randomUUID(), subject, normalized, crimes, probableCause,
                now, now.plus(safe), WarrantStatus.ACTIVE, revision.incrementAndGet());
        warrants.put(warrant.id(), warrant);
        return warrant;
    }

    public Optional<Warrant> activeWarrant(ActorId subject, String jurisdiction) {
        String normalized = normalizeJurisdiction(jurisdiction);
        return warrants.values().stream()
                .filter(warrant -> warrant.subject().equals(subject) && warrant.jurisdiction().equals(normalized)
                        && warrant.status() == WarrantStatus.ACTIVE && warrant.expiresAt().isAfter(Instant.now()))
                .max(Comparator.comparingDouble(Warrant::probableCause).thenComparing(Warrant::issuedAt));
    }

    public boolean hasActiveWarrant(ActorId subject, String jurisdiction) { return activeWarrant(subject, jurisdiction).isPresent(); }

    public List<Warrant> activeWarrants() {
        Instant now = Instant.now();
        return warrants.values().stream().filter(warrant -> warrant.status() == WarrantStatus.ACTIVE && warrant.expiresAt().isAfter(now))
                .sorted(Comparator.comparingDouble(Warrant::probableCause).reversed().thenComparing(Warrant::issuedAt)).toList();
    }

    public Optional<Warrant> serveWarrant(UUID id) { return setWarrantStatus(id, WarrantStatus.SERVED); }
    public Optional<Warrant> revokeWarrant(UUID id) { return setWarrantStatus(id, WarrantStatus.REVOKED); }

    public int expireWarrants(Instant now) {
        int changed = 0;
        for (Warrant warrant : List.copyOf(warrants.values())) {
            if (warrant.status() != WarrantStatus.ACTIVE || warrant.expiresAt().isAfter(now)) continue;
            if (setWarrantStatus(warrant.id(), WarrantStatus.EXPIRED).isPresent()) {
                changed++;
                for (UUID crimeId : warrant.crimeIds()) removeWantedCrime(warrant.subject(), warrant.jurisdiction(), crimeId);
                if (warrant.crimeIds().isEmpty() && !hasActiveWarrant(warrant.subject(), warrant.jurisdiction())
                        && activeCustody(warrant.subject()).isEmpty()) clearWanted(warrant.subject(), warrant.jurisdiction());
            }
        }
        return changed;
    }

    private Optional<Warrant> setWarrantStatus(UUID id, WarrantStatus next) {
        AtomicReference<Warrant> result = new AtomicReference<>();
        warrants.computeIfPresent(id, (ignored, old) -> {
            if (old.status() == next) { result.set(old); return old; }
            Warrant value = new Warrant(old.id(), old.subject(), old.jurisdiction(), old.crimeIds(), old.probableCause(),
                    old.issuedAt(), old.expiresAt(), next, revision.incrementAndGet());
            result.set(value); return value;
        });
        return Optional.ofNullable(result.get());
    }

    public Custody detain(ActorId subject, ActorId officer, Warrant warrant, String facilityId,
                          long fine, long bail, boolean previousAiEnabled, Map<String, String> facts) {
        Objects.requireNonNull(subject); Objects.requireNonNull(warrant);
        Optional<Custody> existing = activeCustody(subject);
        if (existing.isPresent()) return existing.get();
        if (custody.size() >= MAX_CUSTODY) throw new IllegalStateException("custody_limit");
        Custody value = new Custody(UUID.randomUUID(), subject, officer, warrant.id(), warrant.jurisdiction(), facilityId,
                Instant.now(), null, fine, bail, previousAiEnabled, CustodyStatus.DETAINED, facts, revision.incrementAndGet());
        custody.put(value.id(), value);
        serveWarrant(warrant.id());
        return value;
    }

    public Optional<Custody> activeCustody(ActorId subject) {
        return custody.values().stream().filter(value -> value.subject().equals(subject)
                        && (value.status() == CustodyStatus.DETAINED || value.status() == CustodyStatus.JAILED))
                .max(Comparator.comparing(Custody::arrestedAt));
    }

    public Optional<Custody> jail(UUID custodyId, Instant releaseAt, long fine, long bail, Map<String, String> facts) {
        AtomicReference<Custody> result = new AtomicReference<>();
        custody.computeIfPresent(custodyId, (ignored, old) -> {
            HashMap<String, String> merged = new HashMap<>(old.facts());
            if (facts != null) merged.putAll(sanitizeFacts(facts));
            Custody next = new Custody(old.id(), old.subject(), old.officer(), old.warrantId(), old.jurisdiction(), old.facilityId(),
                    old.arrestedAt(), releaseAt, fine, bail, old.previousAiEnabled(), CustodyStatus.JAILED, merged, revision.incrementAndGet());
            result.set(next); return next;
        });
        return Optional.ofNullable(result.get());
    }

    public Optional<Custody> release(UUID custodyId, String reason) {
        return setCustodyStatus(custodyId, CustodyStatus.RELEASED, reason);
    }

    public Optional<Custody> escape(UUID custodyId) {
        Optional<Custody> escaped = setCustodyStatus(custodyId, CustodyStatus.ESCAPED, "escape");
        escaped.ifPresent(value -> raiseWanted(value.subject(), value.jurisdiction(), null, 120, 1D, 100L));
        return escaped;
    }

    private Optional<Custody> setCustodyStatus(UUID id, CustodyStatus next, String reason) {
        AtomicReference<Custody> result = new AtomicReference<>();
        custody.computeIfPresent(id, (ignored, old) -> {
            HashMap<String, String> facts = new HashMap<>(old.facts());
            if (reason != null && !reason.isBlank()) facts.put("release_reason", reason);
            Custody value = new Custody(old.id(), old.subject(), old.officer(), old.warrantId(), old.jurisdiction(), old.facilityId(),
                    old.arrestedAt(), old.releaseAt(), old.fine(), old.bail(), old.previousAiEnabled(), next, facts, revision.incrementAndGet());
            result.set(value); return value;
        });
        return Optional.ofNullable(result.get());
    }

    public CourtCase fileCourtCase(ActorId defendant, String jurisdiction, Set<UUID> crimeIds, UUID custodyId,
                                   Instant hearingAt, int evidenceCount, Map<String, String> facts) {
        Objects.requireNonNull(defendant); Objects.requireNonNull(hearingAt);
        Set<UUID> crimes = Set.copyOf(crimeIds == null ? Set.of() : crimeIds);
        Optional<CourtCase> existing = courtCases.values().stream()
                .filter(value -> value.defendant().equals(defendant) && value.status() == CourtStatus.FILED
                        && value.crimeIds().equals(crimes)).findFirst();
        if (existing.isPresent()) return existing.get();
        if (courtCases.size() >= MAX_COURT_CASES) throw new IllegalStateException("court_case_limit");
        CourtCase value = new CourtCase(UUID.randomUUID(), defendant, jurisdiction, crimes, custodyId, Instant.now(), hearingAt,
                null, CourtStatus.FILED, 0D, 0D, evidenceCount, 0L, 0L, facts, revision.incrementAndGet());
        courtCases.put(value.id(), value);
        return value;
    }

    public List<CourtCase> dueHearings(Instant now, int limit) {
        return courtCases.values().stream().filter(value -> value.status() == CourtStatus.FILED && !value.hearingAt().isAfter(now))
                .sorted(Comparator.comparing(CourtCase::hearingAt)).limit(Math.max(1, Math.min(256, limit))).toList();
    }

    public Optional<CourtCase> decide(UUID caseId, boolean convicted, double evidenceScore, double alibiStrength,
                                      int evidenceCount, long fine, long jailSeconds, Map<String, String> facts) {
        return setCourtDecision(caseId, convicted ? CourtStatus.CONVICTED : CourtStatus.ACQUITTED,
                evidenceScore, alibiStrength, evidenceCount, fine, jailSeconds, facts);
    }

    public Optional<CourtCase> overturn(UUID caseId, double evidenceScore, double alibiStrength,
                                        int evidenceCount, String reason) {
        return setCourtDecision(caseId, CourtStatus.OVERTURNED, evidenceScore, alibiStrength, evidenceCount,
                0L, 0L, Map.of("overturn_reason", reason == null ? "new_evidence" : reason));
    }

    private Optional<CourtCase> setCourtDecision(UUID caseId, CourtStatus status, double evidenceScore, double alibiStrength,
                                                  int evidenceCount, long fine, long jailSeconds, Map<String, String> facts) {
        AtomicReference<CourtCase> result = new AtomicReference<>();
        courtCases.computeIfPresent(caseId, (ignored, old) -> {
            HashMap<String, String> merged = new HashMap<>(old.facts());
            if (facts != null) merged.putAll(sanitizeFacts(facts));
            CourtCase next = new CourtCase(old.id(), old.defendant(), old.jurisdiction(), old.crimeIds(), old.custodyId(),
                    old.filedAt(), old.hearingAt(), Instant.now(), status, evidenceScore, alibiStrength, evidenceCount,
                    fine, jailSeconds, merged, revision.incrementAndGet());
            result.set(next); return next;
        });
        return Optional.ofNullable(result.get());
    }

    public List<CourtCase> convictedCases() {
        return courtCases.values().stream().filter(value -> value.status() == CourtStatus.CONVICTED)
                .sorted(Comparator.comparing(CourtCase::decidedAt, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    public List<Custody> dueReleases(Instant now, int limit) {
        return custody.values().stream().filter(value -> value.status() == CustodyStatus.JAILED
                        && value.releaseAt() != null && !value.releaseAt().isAfter(now))
                .sorted(Comparator.comparing(Custody::releaseAt)).limit(Math.max(1, Math.min(256, limit))).toList();
    }

    public Optional<CourtCase> courtCase(UUID id) { return Optional.ofNullable(courtCases.get(id)); }
    public Optional<Custody> custody(UUID id) { return Optional.ofNullable(custody.get(id)); }
    public Optional<Warrant> warrant(UUID id) { return Optional.ofNullable(warrants.get(id)); }

    public Snapshot snapshot() {
        return new Snapshot(revision.get(), Map.copyOf(wanted), Map.copyOf(warrants), Map.copyOf(custody), Map.copyOf(courtCases));
    }

    public void restore(Snapshot snapshot) {
        wanted.clear(); warrants.clear(); custody.clear(); courtCases.clear();
        if (snapshot != null) {
            wanted.putAll(snapshot.wanted()); warrants.putAll(snapshot.warrants());
            custody.putAll(snapshot.custody()); courtCases.putAll(snapshot.courtCases());
            revision.set(Math.max(0L, snapshot.revision()));
        } else revision.set(0L);
    }

    public record Snapshot(long revision, Map<WantedKey, WantedRecord> wanted, Map<UUID, Warrant> warrants,
                           Map<UUID, Custody> custody, Map<UUID, CourtCase> courtCases) {
        public Snapshot {
            wanted = Map.copyOf(wanted == null ? Map.of() : wanted);
            warrants = Map.copyOf(warrants == null ? Map.of() : warrants);
            custody = Map.copyOf(custody == null ? Map.of() : custody);
            courtCases = Map.copyOf(courtCases == null ? Map.of() : courtCases);
        }
    }

    private static WantedLevel level(int points) {
        if (points <= 0) return WantedLevel.NONE;
        if (points < 80) return WantedLevel.PERSON_OF_INTEREST;
        if (points < 220) return WantedLevel.WANTED;
        if (points < 500) return WantedLevel.HIGH_RISK;
        return WantedLevel.FUGITIVE;
    }

    private static Map<String, String> sanitizeFacts(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (result.size() >= 64) break;
            String key = entry.getKey(), value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64 || value == null || value.length() > 512) continue;
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static long boundedMoney(long value) { return Math.max(0L, Math.min(10_000_000_000_000L, value)); }
    private static long saturatingMoney(long a, long b) {
        try { return boundedMoney(Math.addExact(a, b)); }
        catch (ArithmeticException ignored) { return 10_000_000_000_000L; }
    }
    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
    private static String normalizeJurisdiction(String value) {
        String result = value == null ? "global" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.:-]", "_");
        return result.isBlank() ? "global" : result.substring(0, Math.min(96, result.length()));
    }
}
