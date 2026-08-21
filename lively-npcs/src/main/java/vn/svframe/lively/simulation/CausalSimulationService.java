package vn.svframe.lively.simulation;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorSnapshot;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.faction.FactionEngine;
import vn.svframe.lively.social.RomanceEngine;
import vn.svframe.lively.social.SocialEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns world events and accumulated state into actual domain consequences.
 * This layer changes Lively semantic state only. It never edits blocks, player inventories, NBT or commands.
 */
public final class CausalSimulationService implements AutoCloseable {
    private static final long DOMAIN_PULSE_TICKS = 1200L;
    private static final int MAX_RELATIONSHIPS_PER_PULSE = 128;
    private static final int MAX_CRIMES_PER_PULSE = 64;
    private static final int MAX_BUSINESS_STOCKS_PER_PULSE = 256;
    private static final Duration COLD_CASE_AGE = Duration.ofHours(24);
    private static final Duration STALE_CHARGE_AGE = Duration.ofHours(72);

    private final WorldEventEngine.Listener listener = new WorldEventEngine.Listener() {
        @Override public void onStarted(WorldEventEngine.WorldEvent event) { materializeEvent(event); }
    };
    private long lastPulse;

    public CausalSimulationService() {
        LivelyApi.events().addListener(listener);
    }

    public void tick(long tick) {
        if (tick - lastPulse < DOMAIN_PULSE_TICKS) return;
        lastPulse = tick;
        advanceRomance();
        advanceInvestigations();
        advanceBusinesses();
        advanceFactionStrategies();
    }

    private void materializeEvent(WorldEventEngine.WorldEvent event) {
        switch (event.category()) {
            case CRIME -> materializeCrime(event);
            case ECONOMIC -> materializeEconomicShock(event);
            case FACTION_CONFLICT, POLITICAL -> materializeFactionConflict(event);
            case SOCIAL, FESTIVAL -> materializeSocialEvent(event);
            case DISASTER -> materializeDisaster(event);
            case MYSTERY, DISCOVERY, MIGRATION -> materializeKnowledgeEvent(event);
        }
    }

    private void materializeCrime(WorldEventEngine.WorldEvent event) {
        if (event.facts().containsKey("crime_id")) return;
        List<ActorId> candidates = event.participants().stream()
                .filter(actor -> actor.kind() == ActorId.Kind.NPC || actor.kind() == ActorId.Kind.PLAYER)
                .sorted(Comparator.comparing(actor -> actor.uuid().toString()))
                .limit(32)
                .toList();
        if (candidates.size() < 2) return;

        ActorId perpetrator = candidates.stream().max(Comparator.comparingDouble(this::perpetratorScore)).orElse(candidates.get(0));
        ActorId victim = candidates.stream().filter(actor -> !actor.equals(perpetrator))
                .min(Comparator.comparingDouble(this::perpetratorScore)).orElse(null);
        if (victim == null) return;

        CrimeEngine.Type type = crimeType(event);
        Set<ActorId> witnesses = candidates.stream().filter(actor -> !actor.equals(perpetrator) && !actor.equals(victim))
                .limit(8).collect(java.util.stream.Collectors.toUnmodifiableSet());
        String motive = motive(perpetrator, victim, event);
        Map<String, String> facts = new HashMap<>(event.facts());
        facts.put("event", event.id().toString());
        facts.put("semantic_only", "true");
        CrimeEngine.Crime crime = LivelyApi.crime().create(type, victim, perpetrator, event.structureId(), motive, witnesses, facts);

        LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.MOTIVE, null, perpetrator,
                0.62D + event.intensity() * 0.18D, 0.65D, false, Map.of("motive", motive));
        LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.OPPORTUNITY, null, perpetrator,
                0.58D + event.intensity() * 0.20D, 0.70D, false, Map.of("location", safe(event.structureId())));
        for (ActorId witness : witnesses) {
            double reliability = witnessReliability(witness, perpetrator);
            LivelyApi.crime().addEvidence(crime.id(), CrimeEngine.EvidenceType.WITNESS, witness, perpetrator,
                    reliability, 0.72D, false, Map.of("event", event.id().toString()));
            try {
                LivelyApi.social().createRumor("crime:" + crime.id(), perpetrator, witness,
                        "Có chuyện đáng ngờ liên quan đến " + display(perpetrator) + ".",
                        reliability * 0.70D, 0.70D, Duration.ofDays(7));
            } catch (IllegalArgumentException ignored) { }
        }
        LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
        if (event.structureId() != null) {
            LivelyApi.structures().setState(event.structureId(), SemanticStructureRegistry.OperationalState.UNDER_INVESTIGATION);
        }
        LivelyApi.social().changeReputation(perpetrator, SocialEngine.ReputationScope.GLOBAL, "", -0.08D * event.intensity());
    }

    private void materializeEconomicShock(WorldEventEngine.WorldEvent event) {
        int touched = 0;
        EconomyEngine.Snapshot snapshot = LivelyApi.economy().snapshot();
        for (EconomyEngine.Stock stock : snapshot.stocks().values()) {
            if (touched++ >= MAX_BUSINESS_STOCKS_PER_PULSE) break;
            EconomyEngine.Business business = snapshot.businesses().get(stock.key().businessId());
            if (business == null) continue;
            if (event.structureId() != null && !Objects.equals(event.structureId(), business.locationId())) continue;
            double demand = clamp01(stock.demand() + 0.18D * event.intensity());
            double supply = clamp01(stock.supply() - 0.24D * event.intensity());
            long quantity = Math.max(0L, stock.quantity() - Math.round(stock.targetQuantity() * 0.10D * event.intensity()));
            LivelyApi.economy().setStock(stock.key().businessId(), stock.key().itemId(), quantity,
                    stock.targetQuantity(), stock.basePrice(), demand, supply);
        }
    }

    private void materializeFactionConflict(WorldEventEngine.WorldEvent event) {
        Set<UUID> factions = new HashSet<>();
        for (ActorId participant : event.participants()) {
            if (participant.kind() == ActorId.Kind.FACTION) factions.add(participant.uuid());
            LivelyApi.actors().get(participant).map(snapshot -> snapshot.facts().get("faction")).flatMap(CausalSimulationService::uuid)
                    .ifPresent(factions::add);
        }
        List<UUID> values = factions.stream().sorted().limit(16).toList();
        for (int i = 0; i < values.size(); i++) {
            for (int j = i + 1; j < values.size(); j++) {
                LivelyApi.factions().changeRelation(values.get(i), values.get(j),
                        -0.08D * event.intensity(), 0.14D * event.intensity(), -0.03D * event.intensity());
            }
        }
        for (ActorId participant : event.participants()) {
            LivelyApi.social().changeReputation(participant, SocialEngine.ReputationScope.GLOBAL, "", -0.01D * event.intensity());
        }
    }

    private void materializeSocialEvent(WorldEventEngine.WorldEvent event) {
        List<ActorId> participants = event.participants().stream().sorted(Comparator.comparing(a -> a.uuid().toString())).limit(32).toList();
        for (int i = 0; i < participants.size(); i++) {
            for (int j = i + 1; j < participants.size(); j++) {
                LivelyApi.social().apply(participants.get(i), participants.get(j), new SocialEngine.SocialDelta(
                        0.015D * event.intensity(), 0.025D * event.intensity(), 0.01D * event.intensity(), 0D,
                        0.008D * event.intensity(), 0.006D * event.intensity(), 0.025D,
                        "shared_world_event", Map.of("event", event.id().toString(), "seed", event.seed())));
            }
        }
    }

    private void materializeDisaster(WorldEventEngine.WorldEvent event) {
        if (event.structureId() == null) return;
        LivelyApi.structures().setState(event.structureId(), SemanticStructureRegistry.OperationalState.DAMAGED);
    }

    private void materializeKnowledgeEvent(WorldEventEngine.WorldEvent event) {
        for (ActorId participant : event.participants().stream().limit(64).toList()) {
            if (participant.kind() != ActorId.Kind.NPC || LivelyApi.states() == null) continue;
            LivelyApi.states().get(participant.uuid()).ifPresent(state -> state.updateBelief(
                    "world_event." + normalize(event.seed()), event.structureId() == null ? event.seed() : event.structureId(),
                    0.55D + event.intensity() * 0.35D, null));
        }
    }

    private void advanceRomance() {
        int processed = 0;
        SocialEngine.Snapshot social = LivelyApi.social().snapshot();
        for (SocialEngine.Relationship relationship : social.relationships().values()) {
            if (processed++ >= MAX_RELATIONSHIPS_PER_PULSE) break;
            ActorId a = relationship.pair().a();
            ActorId b = relationship.pair().b();
            if (a.kind() != ActorId.Kind.NPC || b.kind() != ActorId.Kind.NPC) continue;
            if (relationship.fear() > 0.45D || relationship.trust() < 0.10D || relationship.affection() < 0.18D) continue;

            RomanceEngine.Bond bond = LivelyApi.romance().bond(a, b).orElse(null);
            if (bond == null) {
                if (relationship.attraction() >= 0.35D && relationship.familiarity() >= 0.30D) LivelyApi.romance().begin(a, b);
                continue;
            }
            if (bond.stage() == RomanceEngine.Stage.ENDED) continue;
            if (relationship.trust() < -0.25D || relationship.affection() < -0.30D || bond.jealousy() > 0.88D) {
                LivelyApi.romance().transition(a, b, RomanceEngine.Stage.SEPARATED);
                continue;
            }
            Duration age = Duration.between(bond.since(), Instant.now());
            RomanceEngine.Stage next = switch (bond.stage()) {
                case INTEREST -> age.compareTo(Duration.ofMinutes(30)) >= 0 && bond.stability() >= 0.48D ? RomanceEngine.Stage.DATING : null;
                case DATING -> age.compareTo(Duration.ofHours(2)) >= 0 && bond.stability() >= 0.55D ? RomanceEngine.Stage.PARTNERED : null;
                case PARTNERED -> age.compareTo(Duration.ofHours(8)) >= 0 && bond.stability() >= 0.66D ? RomanceEngine.Stage.ENGAGED : null;
                case ENGAGED -> age.compareTo(Duration.ofHours(12)) >= 0 && bond.stability() >= 0.72D ? RomanceEngine.Stage.MARRIED : null;
                case SEPARATED -> bond.stability() < 0.18D ? RomanceEngine.Stage.ENDED : null;
                default -> null;
            };
            if (next != null) {
                LivelyApi.romance().transition(a, b, next);
                LivelyApi.social().apply(a, b, new SocialEngine.SocialDelta(0.01D, 0.018D, 0.008D, 0D,
                        next == RomanceEngine.Stage.MARRIED ? 0.08D : 0.02D, 0D, 0.01D,
                        "romance_stage:" + next.name().toLowerCase(Locale.ROOT), Map.of()));
            }
        }
    }

    private void advanceInvestigations() {
        LinkedHashSet<ActorId> candidates = LivelyApi.actors().snapshot().actors().keySet().stream()
                .filter(actor -> actor.kind() == ActorId.Kind.NPC || actor.kind() == ActorId.Kind.PLAYER)
                .sorted(Comparator.comparing((ActorId actor) -> actor.kind().name()).thenComparing(actor -> actor.uuid().toString()))
                .limit(256).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Instant now = Instant.now();
        int processed = 0;
        for (CrimeEngine.Crime crime : LivelyApi.crime().snapshot().crimes().values().stream()
                .sorted(Comparator.comparing(CrimeEngine.Crime::occurredAt)).toList()) {
            if (processed++ >= MAX_CRIMES_PER_PULSE) break;
            if (crime.status() == CrimeEngine.Status.RESOLVED || crime.status() == CrimeEngine.Status.DISMISSED) continue;

            List<CrimeEngine.Evidence> evidence = LivelyApi.crime().evidence(crime.id());
            Duration age = Duration.between(crime.occurredAt(), now);

            if (crime.status() == CrimeEngine.Status.COLD) {
                int oldCount = integer(crime.facts().get("cold_evidence_count"), evidence.size());
                if (evidence.size() > oldCount) {
                    LivelyApi.crime().updateFacts(crime.id(), Map.of(
                            "reopened_at", now.toString(),
                            "reopened_reason", "new_evidence",
                            "cold_evidence_count", Integer.toString(evidence.size())));
                    LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
                    markInvestigationLocation(crime);
                }
                continue;
            }

            if (evidence.size() >= 2 && crime.status() == CrimeEngine.Status.OPEN) {
                LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
            }

            List<CrimeEngine.SuspectScore> ranking = LivelyApi.crime().rankSuspects(crime.id(), Set.copyOf(candidates));
            CrimeEngine.SuspectScore top = ranking.isEmpty() ? null : ranking.get(0);

            if (crime.status() == CrimeEngine.Status.CHARGED) {
                ActorId charged = chargedSuspect(crime).orElse(null);
                CrimeEngine.SuspectScore chargedScore = charged == null ? null : ranking.stream()
                        .filter(score -> score.suspect().equals(charged)).findFirst().orElse(null);

                if (chargedScore == null || chargedScore.alibiStrength() >= .65D || chargedScore.score() < .45D) {
                    int wrong = integer(crime.facts().get("wrong_charge_count"), 0) + 1;
                    LivelyApi.crime().updateFacts(crime.id(), Map.of(
                            "wrong_charge_count", Integer.toString(wrong),
                            "last_wrong_charge", charged == null ? "unknown" : charged.uuid().toString(),
                            "last_reviewed_at", now.toString(),
                            "charged_suspect", ""));
                    LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
                    continue;
                }

                if (evidence.size() >= 6 && chargedScore.score() >= .90D && Objects.equals(charged, crime.perpetrator())) {
                    LivelyApi.crime().updateFacts(crime.id(), Map.of(
                            "resolution", "correct_charge",
                            "resolved_at", now.toString()));
                    LivelyApi.crime().status(crime.id(), CrimeEngine.Status.RESOLVED);
                    releaseInvestigationLocation(crime);
                    continue;
                }

                if (age.compareTo(STALE_CHARGE_AGE) >= 0 && !Objects.equals(charged, crime.perpetrator())) {
                    markCold(crime, evidence.size(), "unresolved_wrong_charge");
                }
                continue;
            }

            if (top != null && evidence.size() >= 4 && top.score() >= .78D) {
                LivelyApi.crime().updateFacts(crime.id(), Map.of(
                        "charged_suspect", top.suspect().uuid().toString(),
                        "charged_at", now.toString(),
                        "charged_score", Double.toString(top.score())));
                LivelyApi.crime().status(crime.id(), CrimeEngine.Status.CHARGED);
                if (evidence.size() >= 6 && top.score() >= .90D && Objects.equals(top.suspect(), crime.perpetrator())) {
                    LivelyApi.crime().updateFacts(crime.id(), Map.of("resolution", "correct_charge", "resolved_at", now.toString()));
                    LivelyApi.crime().status(crime.id(), CrimeEngine.Status.RESOLVED);
                    releaseInvestigationLocation(crime);
                }
                continue;
            }

            boolean noUsefulLead = top == null || top.score() < .55D;
            if (age.compareTo(COLD_CASE_AGE) >= 0 && (evidence.size() < 4 || noUsefulLead)) {
                markCold(crime, evidence.size(), "insufficient_evidence");
            }
        }
    }

    private void markCold(CrimeEngine.Crime crime, int evidenceCount, String reason) {
        LivelyApi.crime().updateFacts(crime.id(), Map.of(
                "cold_at", Instant.now().toString(),
                "cold_reason", reason,
                "cold_evidence_count", Integer.toString(evidenceCount)));
        LivelyApi.crime().status(crime.id(), CrimeEngine.Status.COLD);
        releaseInvestigationLocation(crime);
    }

    private void markInvestigationLocation(CrimeEngine.Crime crime) {
        if (crime.locationId() != null) {
            LivelyApi.structures().setState(crime.locationId(), SemanticStructureRegistry.OperationalState.UNDER_INVESTIGATION);
        }
    }

    private void releaseInvestigationLocation(CrimeEngine.Crime crime) {
        if (crime.locationId() == null) return;
        LivelyApi.structures().get(crime.locationId()).ifPresent(structure -> {
            if (structure.state() == SemanticStructureRegistry.OperationalState.UNDER_INVESTIGATION) {
                LivelyApi.structures().setState(crime.locationId(), SemanticStructureRegistry.OperationalState.OPEN);
            }
        });
    }

    private Optional<ActorId> chargedSuspect(CrimeEngine.Crime crime) {
        String raw = crime.facts().get("charged_suspect");
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            UUID id = UUID.fromString(raw);
            return LivelyApi.actors().snapshot().actors().keySet().stream().filter(actor -> actor.uuid().equals(id)).findFirst();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void advanceBusinesses() {
        EconomyEngine.Snapshot snapshot = LivelyApi.economy().snapshot();
        for (EconomyEngine.Business business : snapshot.businesses().values().stream().limit(256).toList()) {
            if (business.locationId() != null) {
                SemanticStructureRegistry.OperationalState state = LivelyApi.structures().get(business.locationId())
                        .map(SemanticStructureRegistry.Structure::state).orElse(SemanticStructureRegistry.OperationalState.OPEN);
                boolean shouldOpen = state == SemanticStructureRegistry.OperationalState.OPEN || state == SemanticStructureRegistry.OperationalState.FESTIVAL;
                if (business.open() != shouldOpen) LivelyApi.economy().setOpen(business.id(), shouldOpen);
            }
        }
        int touched = 0;
        for (EconomyEngine.Stock stock : snapshot.stocks().values()) {
            if (touched++ >= MAX_BUSINESS_STOCKS_PER_PULSE) break;
            if (stock.quantity() >= stock.targetQuantity() || stock.supply() < 0.20D) continue;
            long missing = stock.targetQuantity() - stock.quantity();
            long restock = Math.max(1L, Math.min(missing, Math.round(stock.targetQuantity() * (0.03D + stock.supply() * 0.10D))));
            LivelyApi.economy().setStock(stock.key().businessId(), stock.key().itemId(), stock.quantity() + restock,
                    stock.targetQuantity(), stock.basePrice(), clamp01(stock.demand() - 0.01D), clamp01(stock.supply() - 0.015D));
        }
    }

    private void advanceFactionStrategies() {
        FactionEngine.Snapshot snapshot = LivelyApi.factions().snapshot();
        Map<String, Double> signals = currentSignals();
        for (FactionEngine.Faction faction : snapshot.factions().values().stream().limit(64).toList()) {
            List<FactionEngine.Strategy> strategies = LivelyApi.factions().plan(faction.id(), signals);
            if (strategies.isEmpty() || strategies.get(0).utility() < 0.45D) continue;
            LivelyApi.factions().updateKnowledge(faction.id(), "current_strategy", strategies.get(0).action());
        }
    }

    private Map<String, Double> currentSignals() {
        long openCrime = LivelyApi.crime().snapshot().crimes().values().stream()
                .filter(c -> c.status() == CrimeEngine.Status.OPEN || c.status() == CrimeEngine.Status.INVESTIGATING || c.status() == CrimeEngine.Status.CHARGED).count();
        var stocks = LivelyApi.economy().snapshot().stocks().values();
        double scarcity = stocks.isEmpty() ? 0D : stocks.stream()
                .mapToDouble(s -> Math.max(0D, 1D - s.quantity() / (double) Math.max(1L, s.targetQuantity()))).average().orElse(0D);
        return Map.of("crime", Math.min(1D, openCrime / 8D), "scarcity", scarcity,
                "threat", Math.min(1D, LivelyApi.events().activeEvents().stream().mapToDouble(WorldEventEngine.WorldEvent::intensity).average().orElse(0D)));
    }

    private double perpetratorScore(ActorId actor) {
        ActorSnapshot snapshot = LivelyApi.actors().get(actor).orElse(null);
        if (snapshot == null) return 0.5D;
        double ambition = snapshot.social("ambition");
        double morality = snapshot.social("morality");
        double fear = snapshot.social("fear");
        return clamp01(ambition * 0.42D + (1D - morality) * 0.42D + (1D - fear) * 0.16D);
    }

    private double witnessReliability(ActorId witness, ActorId suspect) {
        SocialEngine.Relationship relation = LivelyApi.social().findRelationship(witness, suspect).orElse(null);
        if (relation == null) return 0.68D;
        return clamp01(0.68D + relation.familiarity() * 0.12D - Math.max(0D, -relation.trust()) * 0.10D - relation.fear() * 0.08D);
    }

    private String motive(ActorId perpetrator, ActorId victim, WorldEventEngine.WorldEvent event) {
        String configured = event.facts().get("motive");
        if (configured != null && !configured.isBlank()) return configured;
        SocialEngine.Relationship relation = LivelyApi.social().findRelationship(perpetrator, victim).orElse(null);
        if (relation != null && relation.affection() < -0.35D) return "resentment";
        if (relation != null && relation.trust() < -0.35D) return "betrayal";
        ActorSnapshot snapshot = LivelyApi.actors().get(perpetrator).orElse(null);
        if (snapshot != null && snapshot.social("ambition") > 0.70D) return "ambition";
        return "opportunity";
    }

    private CrimeEngine.Type crimeType(WorldEventEngine.WorldEvent event) {
        String explicit = event.facts().get("crime_type");
        if (explicit != null) {
            try { return CrimeEngine.Type.valueOf(explicit.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        String seed = event.seed().toLowerCase(Locale.ROOT);
        if (seed.contains("murder") || event.intensity() >= 0.88D) return CrimeEngine.Type.MURDER;
        if (seed.contains("missing")) return CrimeEngine.Type.MISSING_PERSON;
        if (seed.contains("assault")) return CrimeEngine.Type.ASSAULT;
        if (seed.contains("trespass")) return CrimeEngine.Type.TRESPASSING;
        if (seed.contains("fraud")) return CrimeEngine.Type.FRAUD;
        if (seed.contains("faction")) return CrimeEngine.Type.FACTION_CRIME;
        return CrimeEngine.Type.THEFT;
    }

    private String display(ActorId actor) {
        return LivelyApi.actors().get(actor).map(ActorSnapshot::displayName).orElse(actor.uuid().toString());
    }

    private static Optional<UUID> uuid(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try { return Optional.of(UUID.fromString(raw)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    private static int integer(String raw, int fallback) {
        try { return raw == null ? fallback : Integer.parseInt(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]+", "_");
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }

    @Override public void close() {
        LivelyApi.events().removeListener(listener);
    }
}
