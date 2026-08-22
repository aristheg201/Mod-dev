package vn.svframe.lively.law;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.economy.DebtEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.society.SocietyApi;
import vn.svframe.lively.social.SocialEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-thread justice runtime. Investigations remain fallible: warrants and verdicts consume suspect scores/alibis,
 * never the hidden perpetrator field. NPC custody is physical (semantic jail structure + AI suspension) when enabled.
 */
public final class LawEnforcementService {
    private static final int MAX_CASE_REVIEWS = 64;
    private static final int MAX_OFFICERS = 64;
    private static final int MAX_HEARINGS = 32;
    private static final double ARREST_DISTANCE_SQ = 3.75D * 3.75D;

    private final MinecraftServer server;
    private final LawConfig config;
    private final LawEnforcementEngine law;
    private final ConcurrentHashMap<UUID, Long> playerNoticeAt = new ConcurrentHashMap<>();
    private long lastReview;
    private long lastOfficerPulse;

    public LawEnforcementService(MinecraftServer server, LawConfig config, LawEnforcementEngine law) {
        this.server = server;
        this.config = config == null ? LawConfig.defaults() : config;
        this.law = law;
    }

    public LawConfig config() { return config; }

    public void tick(long tick) {
        if (!config.enabled() || LivelyApi.investigation() == null || LivelyApi.npcs() == null) return;
        Instant now = Instant.now();
        law.expireWarrants(now);
        if (tick - lastReview >= config.reviewPulseTicks()) {
            lastReview = tick;
            reviewInvestigations(now);
            reviewAppeals(now);
        }
        processHearings(now);
        processReleases(now);
        if (tick - lastOfficerPulse >= config.officerPulseTicks()) {
            lastOfficerPulse = tick;
            dispatchOfficers(tick);
        }
    }

    private void reviewInvestigations(Instant now) {
        int processed = 0;
        for (CrimeEngine.Crime crime : LivelyApi.crime().snapshot().crimes().values().stream()
                .filter(value -> value.status() == CrimeEngine.Status.OPEN || value.status() == CrimeEngine.Status.INVESTIGATING)
                .sorted(Comparator.comparing(CrimeEngine.Crime::occurredAt)).toList()) {
            if (processed++ >= MAX_CASE_REVIEWS) break;
            List<CrimeEngine.Evidence> evidence = LivelyApi.crime().evidence(crime.id());
            if (evidence.size() < config.warrantEvidenceCount()) continue;
            CrimeEngine.SuspectScore top = LivelyApi.investigation().suspects(crime.id()).stream().findFirst().orElse(null);
            if (top == null) continue;
            if (!config.trackPlayers() && top.suspect().kind() == ActorId.Kind.PLAYER) continue;
            String jurisdiction = jurisdiction(crime);

            if (top.score() >= config.warrantScore() && top.alibiStrength() < config.acquitAlibiStrength()) {
                revokeSupersededWarrant(crime, top, jurisdiction);
                int severity = severity(crime.type());
                law.raiseWanted(top.suspect(), jurisdiction, crime.id(), severity, top.score(), config.bountyUnit());
                LawEnforcementEngine.Warrant warrant = law.issueWarrant(top.suspect(), jurisdiction, Set.of(crime.id()), top.score(),
                        Duration.ofSeconds(config.warrantLifetimeSeconds()));
                LivelyApi.crime().updateFacts(crime.id(), Map.of(
                        "warrant_id", warrant.id().toString(),
                        "warrant_subject", top.suspect().uuid().toString(),
                        "probable_cause", Double.toString(top.score()),
                        "jurisdiction", jurisdiction,
                        "last_law_review", now.toString()));
                if (crime.status() == CrimeEngine.Status.OPEN) LivelyApi.crime().status(crime.id(), CrimeEngine.Status.INVESTIGATING);
            } else {
                revokeWeakWarrant(crime, top, jurisdiction);
            }
        }
    }

    private void revokeSupersededWarrant(CrimeEngine.Crime crime, CrimeEngine.SuspectScore top, String jurisdiction) {
        String warrantId = crime.facts().get("warrant_id");
        if (warrantId == null || warrantId.isBlank()) return;
        try {
            UUID id = UUID.fromString(warrantId);
            law.warrant(id).filter(value -> value.status() == LawEnforcementEngine.WarrantStatus.ACTIVE)
                    .filter(value -> !value.subject().equals(top.suspect())).ifPresent(value -> {
                        law.revokeWarrant(value.id());
                        value.crimeIds().forEach(crimeId -> law.removeWantedCrime(value.subject(), jurisdiction, crimeId));
                        LivelyApi.crime().updateFacts(crime.id(), Map.of(
                                "superseded_warrant", value.id().toString(),
                                "superseded_subject", value.subject().uuid().toString(),
                                "superseded_at", Instant.now().toString()));
                    });
        } catch (IllegalArgumentException ignored) { }
    }

    private void revokeWeakWarrant(CrimeEngine.Crime crime, CrimeEngine.SuspectScore top, String jurisdiction) {
        String warrantId = crime.facts().get("warrant_id");
        if (warrantId == null || warrantId.isBlank()) return;
        try {
            UUID id = UUID.fromString(warrantId);
            law.warrant(id).filter(value -> value.status() == LawEnforcementEngine.WarrantStatus.ACTIVE).ifPresent(value -> {
                if (top == null || !top.suspect().equals(value.subject()) || top.score() < config.warrantScore() * .75D
                        || top.alibiStrength() >= config.acquitAlibiStrength()) {
                    law.revokeWarrant(id);
                    value.crimeIds().forEach(crimeId -> law.removeWantedCrime(value.subject(), jurisdiction, crimeId));
                    LivelyApi.crime().updateFacts(crime.id(), Map.of("warrant_revoked", Instant.now().toString(), "warrant_id", ""));
                }
            });
        } catch (IllegalArgumentException ignored) { }
    }

    private void settleWantedAfterVerdict(ActorId subject, String jurisdiction, Set<UUID> crimeIds) {
        for (UUID crimeId : crimeIds) law.removeWantedCrime(subject, jurisdiction, crimeId);
    }

    private void dispatchOfficers(long tick) {
        List<LawEnforcementEngine.Warrant> warrants = law.activeWarrants();
        if (warrants.isEmpty()) return;
        NpcRuntime npcs = LivelyApi.npcs();
        List<NpcDefinition> officers = npcs.snapshot().values().stream().filter(this::isOfficer)
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_OFFICERS).toList();
        Set<ActorId> assignedSubjects = new HashSet<>();
        for (NpcDefinition officer : officers) {
            if (!officer.spawned() || law.activeCustody(new ActorId(officer.id(), ActorId.Kind.NPC)).isPresent()) continue;
            String officerJurisdiction = officerJurisdiction(officer);
            LawEnforcementEngine.Warrant warrant = warrants.stream()
                    .filter(value -> law.activeCustody(value.subject()).isEmpty())
                    .filter(value -> !assignedSubjects.contains(value.subject()))
                    .filter(value -> value.jurisdiction().equals("global") || officerJurisdiction.equals("global")
                            || value.jurisdiction().equals(officerJurisdiction))
                    .max(Comparator.comparingInt((LawEnforcementEngine.Warrant value) ->
                                    law.wanted(value.subject(), value.jurisdiction()).map(LawEnforcementEngine.WantedRecord::points).orElse(0))
                            .thenComparingDouble(LawEnforcementEngine.Warrant::probableCause)).orElse(null);
            if (warrant == null) continue;
            assignedSubjects.add(warrant.subject());
            if (warrant.subject().kind() == ActorId.Kind.NPC) dispatchNpcOfficer(officer, warrant);
            else if (warrant.subject().kind() == ActorId.Kind.PLAYER) dispatchPlayerNotice(tick, officer, warrant);
        }
    }

    private void dispatchNpcOfficer(NpcDefinition officer, LawEnforcementEngine.Warrant warrant) {
        NpcRuntime npcs = LivelyApi.npcs();
        UUID suspectId = warrant.subject().uuid();
        NpcDefinition suspect = npcs.get(suspectId).orElse(null);
        if (suspect == null || !suspect.spawned()) return;
        Optional<Vec3d> officerPos = npcs.position(officer.id());
        Optional<Vec3d> suspectPos = npcs.position(suspectId);
        String officerWorld = npcs.worldKey(officer.id()).orElse(officer.world());
        String suspectWorld = npcs.worldKey(suspectId).orElse(suspect.world());
        if (officerPos.isPresent() && suspectPos.isPresent() && officerWorld.equals(suspectWorld)
                && officerPos.get().squaredDistanceTo(suspectPos.get()) <= ARREST_DISTANCE_SQ) {
            arrestNpc(officer, suspect, warrant);
            return;
        }
        if (LivelyApi.worldNavigation() != null) LivelyApi.worldNavigation().follow(officer.id(), suspectId);
        remember(officer.id(), "police_pursuit", Map.of("warrant", warrant.id().toString(), "suspect", suspectId.toString()), .42D);
    }

    private void dispatchPlayerNotice(long tick, NpcDefinition officer, LawEnforcementEngine.Warrant warrant) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(warrant.subject().uuid());
        if (player == null) return;
        if (LivelyApi.worldNavigation() != null) LivelyApi.worldNavigation().follow(officer.id(), player.getUuid());
        Vec3d officerPos = LivelyApi.npcs().position(officer.id()).orElse(null);
        if (officerPos == null || !LivelyApi.npcs().worldKey(officer.id()).orElse("")
                .equals(player.getServerWorld().getRegistryKey().getValue().toString()) || officerPos.squaredDistanceTo(player.getPos()) > ARREST_DISTANCE_SQ) return;
        long previous = playerNoticeAt.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2L);
        if (tick - previous < 600L) return;
        playerNoticeAt.put(player.getUuid(), tick);
        long bounty = law.wanted(warrant.subject(), warrant.jurisdiction()).map(LawEnforcementEngine.WantedRecord::bounty).orElse(0L);
        player.sendMessage(Text.literal("[Lively] Bạn đang bị truy nã tại " + warrant.jurisdiction()
                + ". Bounty: " + bounty + ". Hệ thống NPC sẽ tiếp tục điều tra; không có fake jail/command cưỡng chế player."), false);
    }

    private void arrestNpc(NpcDefinition officer, NpcDefinition suspect, LawEnforcementEngine.Warrant warrant) {
        ActorId subject = new ActorId(suspect.id(), ActorId.Kind.NPC);
        if (law.activeCustody(subject).isPresent()) return;
        SemanticStructureRegistry.Structure facility = facility(warrant.jurisdiction()).orElse(null);
        String facilityId = facility == null ? "" : facility.id();
        long expectedFine = config.baseFine() * Math.max(1L, warrant.crimeIds().stream()
                .map(LivelyApi.crime()::crime).flatMap(Optional::stream).mapToInt(value -> severity(value.type())).sum());
        expectedFine = Math.min(10_000_000_000_000L, expectedFine);
        LawEnforcementEngine.Custody custody = law.detain(subject, new ActorId(officer.id(), ActorId.Kind.NPC), warrant,
                facilityId, expectedFine, Math.max(0L, expectedFine / 2L), suspect.aiEnabled(),
                Map.of("arrested_by", officer.id().toString()));

        if (LivelyApi.worldNavigation() != null) {
            LivelyApi.worldNavigation().stop(suspect.id());
            LivelyApi.worldNavigation().stop(officer.id());
        }
        if (config.physicallyJailNpcs()) {
            if (facility != null) {
                Vec3d cell = point(facility, "cell").or(() -> point(facility, "holding")).orElseGet(() -> center(facility.bounds()));
                LivelyApi.npcs().teleport(server, suspect.id(), facility.bounds().world(), cell, suspect.yaw(), suspect.pitch());
            }
            LivelyApi.npcs().setFlag(suspect.id(), NpcRuntime.Flag.AI, false);
        }

        int evidenceCount = warrant.crimeIds().stream().mapToInt(id -> LivelyApi.crime().evidence(id).size()).sum();
        LawEnforcementEngine.CourtCase courtCase = law.fileCourtCase(subject, warrant.jurisdiction(), warrant.crimeIds(), custody.id(),
                Instant.now().plusSeconds(config.hearingDelaySeconds()), evidenceCount,
                Map.of("warrant", warrant.id().toString(), "facility", facilityId));
        for (UUID crimeId : warrant.crimeIds()) {
            LivelyApi.crime().updateFacts(crimeId, Map.of(
                    "charged_suspect", suspect.id().toString(),
                    "charged_at", Instant.now().toString(),
                    "court_case", courtCase.id().toString(),
                    "custody", custody.id().toString()));
            LivelyApi.crime().status(crimeId, CrimeEngine.Status.CHARGED);
        }
        remember(suspect.id(), "arrested", Map.of("warrant", warrant.id().toString(), "court_case", courtCase.id().toString()), .92D);
        remember(officer.id(), "arrest_made", Map.of("suspect", suspect.id().toString(), "warrant", warrant.id().toString()), .72D);
        LivelyApi.social().apply(new ActorId(officer.id(), ActorId.Kind.NPC), subject,
                new SocialEngine.SocialDelta(-.02D, -.04D, .08D, .15D, 0D, 0D, .08D,
                        "lawful_arrest", Map.of("warrant", warrant.id().toString())));
    }

    private void processHearings(Instant now) {
        for (LawEnforcementEngine.CourtCase courtCase : law.dueHearings(now, MAX_HEARINGS)) {
            Aggregate aggregate = evidenceFor(courtCase);
            boolean convicted = aggregate.evidenceCount() >= config.warrantEvidenceCount()
                    && aggregate.score() >= config.convictionScore()
                    && aggregate.alibi() < config.acquitAlibiStrength();
            int severity = courtCase.crimeIds().stream().map(LivelyApi.crime()::crime).flatMap(Optional::stream)
                    .mapToInt(value -> severity(value.type())).sum();
            severity = Math.max(1, severity);
            long fine = convicted ? boundedMultiply(config.baseFine(), severity) : 0L;
            long jailSeconds = convicted ? Math.min(config.maxJailSeconds(), boundedMultiply(config.baseJailSeconds(), severity)) : 0L;
            LawEnforcementEngine.CourtCase decided = law.decide(courtCase.id(), convicted, aggregate.score(), aggregate.alibi(),
                    aggregate.evidenceCount(), fine, jailSeconds,
                    Map.of("decision_basis", "evidence_only", "decided_at", now.toString())).orElse(courtCase);

            if (convicted) convict(decided, now);
            else acquit(decided, "insufficient_probative_evidence");
        }
    }

    private void convict(LawEnforcementEngine.CourtCase courtCase, Instant now) {
        LawEnforcementEngine.Custody custody = courtCase.custodyId() == null ? null : law.custody(courtCase.custodyId()).orElse(null);
        if (custody != null) {
            law.jail(custody.id(), now.plusSeconds(courtCase.jailSeconds()), courtCase.fine(), custody.bail(),
                    Map.of("court_case", courtCase.id().toString(), "verdict", "convicted"));
        }
        for (UUID crimeId : courtCase.crimeIds()) {
            LivelyApi.crime().updateFacts(crimeId, Map.of(
                    "resolution", "court_conviction",
                    "resolved_at", now.toString(),
                    "conviction_score", Double.toString(courtCase.evidenceScore()),
                    "court_case", courtCase.id().toString()));
            LivelyApi.crime().status(crimeId, CrimeEngine.Status.RESOLVED);
            releaseInvestigationLocation(crimeId);
        }
        settleWantedAfterVerdict(courtCase.defendant(), courtCase.jurisdiction(), courtCase.crimeIds());
        collectFine(courtCase);
        if (courtCase.defendant().kind() == ActorId.Kind.NPC) {
            remember(courtCase.defendant().uuid(), "court_conviction", Map.of(
                    "case", courtCase.id().toString(), "fine", Long.toString(courtCase.fine()),
                    "jail_seconds", Long.toString(courtCase.jailSeconds())), .90D);
            LivelyApi.social().changeReputation(courtCase.defendant(), SocialEngine.ReputationScope.GLOBAL, "",
                    -Math.min(.35D, .04D + courtCase.jailSeconds() / 3600D * .02D));
        }
    }

    private void acquit(LawEnforcementEngine.CourtCase courtCase, String reason) {
        for (UUID crimeId : courtCase.crimeIds()) {
            LivelyApi.crime().updateFacts(crimeId, Map.of(
                    "last_acquitted_suspect", courtCase.defendant().uuid().toString(),
                    "acquitted_at", Instant.now().toString(),
                    "acquittal_reason", reason,
                    "charged_suspect", ""));
            LivelyApi.crime().status(crimeId, CrimeEngine.Status.INVESTIGATING);
        }
        if (courtCase.custodyId() != null) releaseCustody(courtCase.custodyId(), "acquitted");
        settleWantedAfterVerdict(courtCase.defendant(), courtCase.jurisdiction(), courtCase.crimeIds());
        if (courtCase.defendant().kind() == ActorId.Kind.NPC) {
            remember(courtCase.defendant().uuid(), "court_acquittal", Map.of("case", courtCase.id().toString()), .82D);
        }
    }

    private void reviewAppeals(Instant now) {
        int reviewed = 0;
        for (LawEnforcementEngine.CourtCase courtCase : law.convictedCases()) {
            if (reviewed++ >= MAX_CASE_REVIEWS) break;
            Aggregate aggregate = evidenceFor(courtCase);
            boolean newEvidence = aggregate.evidenceCount() > courtCase.evidenceCount();
            boolean materiallyChanged = Math.abs(aggregate.score() - courtCase.evidenceScore()) >= .12D
                    || aggregate.alibi() > courtCase.alibiStrength() + .15D;
            if (!newEvidence && !materiallyChanged) continue;
            if (aggregate.score() >= config.convictionScore() && aggregate.alibi() < config.acquitAlibiStrength()) continue;
            law.overturn(courtCase.id(), aggregate.score(), aggregate.alibi(), aggregate.evidenceCount(), "new_or_changed_evidence");
            for (UUID crimeId : courtCase.crimeIds()) {
                LivelyApi.crime().updateFacts(crimeId, Map.of(
                        "conviction_overturned", now.toString(),
                        "overturned_case", courtCase.id().toString(),
                        "charged_suspect", ""));
                LivelyApi.crime().status(crimeId, CrimeEngine.Status.INVESTIGATING);
            }
            if (courtCase.custodyId() != null) releaseCustody(courtCase.custodyId(), "conviction_overturned");
            settleWantedAfterVerdict(courtCase.defendant(), courtCase.jurisdiction(), courtCase.crimeIds());
            if (courtCase.defendant().kind() == ActorId.Kind.NPC) {
                remember(courtCase.defendant().uuid(), "conviction_overturned", Map.of("case", courtCase.id().toString()), .96D);
            }
        }
    }

    private void processReleases(Instant now) {
        for (LawEnforcementEngine.Custody custody : law.dueReleases(now, 64)) releaseCustody(custody.id(), "sentence_served");
    }

    public boolean releaseCustody(UUID custodyId, String reason) {
        LawEnforcementEngine.Custody custody = law.custody(custodyId).orElse(null);
        if (custody == null || (custody.status() != LawEnforcementEngine.CustodyStatus.DETAINED
                && custody.status() != LawEnforcementEngine.CustodyStatus.JAILED)) return false;
        law.release(custodyId, reason);
        if (custody.subject().kind() != ActorId.Kind.NPC) return true;
        NpcDefinition npc = LivelyApi.npcs().get(custody.subject().uuid()).orElse(null);
        if (npc == null) return true;
        LivelyApi.npcs().setFlag(npc.id(), NpcRuntime.Flag.AI, custody.previousAiEnabled());
        SemanticStructureRegistry.Structure facility = custody.facilityId().isBlank() ? null
                : LivelyApi.structures().get(custody.facilityId()).orElse(null);
        if (facility != null && npc.spawned()) {
            Vec3d exit = point(facility, "release").or(() -> point(facility, "exit")).or(() -> point(facility, "entrance"))
                    .orElseGet(() -> center(facility.bounds()));
            LivelyApi.npcs().teleport(server, npc.id(), facility.bounds().world(), exit, npc.yaw(), npc.pitch());
        }
        remember(npc.id(), "released_from_custody", Map.of("reason", reason == null ? "released" : reason), .68D);
        return true;
    }

    private Aggregate evidenceFor(LawEnforcementEngine.CourtCase courtCase) {
        double weightedScore = 0D, weightedAlibi = 0D, weight = 0D;
        int evidenceCount = 0;
        for (UUID crimeId : courtCase.crimeIds()) {
            CrimeEngine.Crime crime = LivelyApi.crime().crime(crimeId).orElse(null);
            if (crime == null) continue;
            CrimeEngine.SuspectScore suspect = LivelyApi.investigation().suspects(crimeId).stream()
                    .filter(value -> value.suspect().equals(courtCase.defendant())).findFirst().orElse(null);
            if (suspect == null) continue;
            double severity = severity(crime.type());
            weightedScore += suspect.score() * severity;
            weightedAlibi += suspect.alibiStrength() * severity;
            weight += severity;
            evidenceCount += LivelyApi.crime().evidence(crimeId).size();
        }
        return weight <= 0D ? new Aggregate(0D, 0D, evidenceCount)
                : new Aggregate(weightedScore / weight, weightedAlibi / weight, evidenceCount);
    }

    private void collectFine(LawEnforcementEngine.CourtCase courtCase) {
        if (courtCase.fine() <= 0L || courtCase.defendant().kind() != ActorId.Kind.NPC) return;
        EconomyEngine.Snapshot economy = LivelyApi.economy().snapshot();
        EconomyEngine.Wallet wallet = economy.wallets().get(courtCase.defendant());
        long available = wallet == null ? 0L : wallet.balance();
        long immediate = Math.min(available, courtCase.fine());
        ActorId treasury = treasury(courtCase.jurisdiction());
        LivelyApi.economy().ensureWallet(treasury, 0L);
        if (immediate > 0L) {
            LivelyApi.economy().transferOnce(EconomyEngine.TransactionType.TAX, courtCase.defendant(), treasury, immediate,
                    "court-fine:" + courtCase.id());
        }
        long remaining = courtCase.fine() - immediate;
        if (remaining <= 0L) return;
        boolean existing = SocietyApi.debts().forDebtor(courtCase.defendant()).stream()
                .anyMatch(debt -> "court_fine".equals(debt.facts().get("source"))
                        && courtCase.id().toString().equals(debt.facts().get("court_case"))
                        && debt.status() != DebtEngine.Status.REPAID && debt.status() != DebtEngine.Status.FORGIVEN);
        if (!existing) {
            SocietyApi.debts().issue(treasury, courtCase.defendant(), remaining, 0, Instant.now().plus(Duration.ofHours(2)), true,
                    Map.of("source", "court_fine", "court_case", courtCase.id().toString(), "jurisdiction", courtCase.jurisdiction()));
        }
    }

    private Optional<SemanticStructureRegistry.Structure> facility(String jurisdiction) {
        List<SemanticStructureRegistry.Structure> candidates = new ArrayList<>();
        for (String type : List.of("jail", "police_station", "sheriff_office", "guard_station")) candidates.addAll(LivelyApi.structures().byType(type));
        return candidates.stream().filter(value -> jurisdiction.equals("global") || jurisdiction.equalsIgnoreCase(safe(value.townId())))
                .sorted(Comparator.comparing(SemanticStructureRegistry.Structure::id)).findFirst()
                .or(() -> candidates.stream().sorted(Comparator.comparing(SemanticStructureRegistry.Structure::id)).findFirst());
    }

    private String jurisdiction(CrimeEngine.Crime crime) {
        String explicit = crime.facts().get("jurisdiction");
        if (explicit != null && !explicit.isBlank()) return normalize(explicit);
        if (crime.locationId() != null) {
            SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(crime.locationId()).orElse(null);
            if (structure != null && structure.townId() != null && !structure.townId().isBlank()) return normalize(structure.townId());
        }
        return "global";
    }

    private String officerJurisdiction(NpcDefinition officer) {
        String explicit = officer.metadata().get("law.jurisdiction");
        if (explicit != null && !explicit.isBlank()) return normalize(explicit);
        String work = officer.metadata().get("work.structure");
        if (work != null) {
            SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(work).orElse(null);
            if (structure != null && structure.townId() != null && !structure.townId().isBlank()) return normalize(structure.townId());
        }
        return "global";
    }

    private boolean isOfficer(NpcDefinition definition) {
        if (Boolean.parseBoolean(definition.metadata().getOrDefault("society.police", "false"))) return true;
        String role = definition.role().toLowerCase(Locale.ROOT);
        return role.contains("police") || role.contains("officer") || role.contains("sheriff")
                || role.contains("guard") || role.contains("cảnh sát");
    }

    private void releaseInvestigationLocation(UUID crimeId) {
        LivelyApi.crime().crime(crimeId).ifPresent(crime -> {
            if (crime.locationId() == null) return;
            LivelyApi.structures().get(crime.locationId()).ifPresent(structure -> {
                if (structure.state() == SemanticStructureRegistry.OperationalState.UNDER_INVESTIGATION) {
                    LivelyApi.structures().setState(structure.id(), SemanticStructureRegistry.OperationalState.OPEN);
                }
            });
        });
    }

    private void remember(UUID npcId, String type, Map<String, String> facts, double importance) {
        if (LivelyApi.states() == null) return;
        LivelyApi.states().get(npcId).ifPresent(state -> state.remember(type, facts, importance, 1D));
    }

    private static Optional<Vec3d> point(SemanticStructureRegistry.Structure structure, String name) {
        String raw = structure.points().get(name);
        if (raw == null) return Optional.empty();
        String[] parts = raw.split(",");
        if (parts.length != 3) return Optional.empty();
        try { return Optional.of(new Vec3d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]))); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    private static Vec3d center(SemanticStructureRegistry.Bounds bounds) {
        return new Vec3d((bounds.minX() + bounds.maxX()) / 2D, bounds.minY() + 1D, (bounds.minZ() + bounds.maxZ()) / 2D);
    }

    private static ActorId treasury(String jurisdiction) {
        return new ActorId(UUID.nameUUIDFromBytes(("lively:law:" + jurisdiction).getBytes(StandardCharsets.UTF_8)), ActorId.Kind.SYSTEM);
    }

    private static int severity(CrimeEngine.Type type) {
        return switch (type) {
            case MURDER -> 10;
            case ASSAULT -> 6;
            case FACTION_CRIME -> 7;
            case FRAUD -> 5;
            case THEFT -> 4;
            case TRESPASSING -> 2;
            case MISSING_PERSON -> 3;
        };
    }

    private static long boundedMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) return 0L;
        try { return Math.min(10_000_000_000_000L, Math.multiplyExact(value, multiplier)); }
        catch (ArithmeticException ignored) { return 10_000_000_000_000L; }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "global" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]", "_");
        return normalized.isBlank() ? "global" : normalized.substring(0, Math.min(96, normalized.length()));
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private record Aggregate(double score, double alibi, int evidenceCount) {}
}
