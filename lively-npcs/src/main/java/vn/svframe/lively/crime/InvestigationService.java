package vn.svframe.lively.crime;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorSnapshot;
import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.social.SocialEngine;

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

/** Produces witness statements from actual simulated knowledge and records them as fallible evidence. */
public final class InvestigationService {
    public enum Outcome { TRUTHFUL, PARTIAL, REFUSED, MISTAKEN, DECEPTIVE }

    public record Statement(UUID id, UUID crimeId, ActorId witness, ActorId subject, Outcome outcome,
                            double reliability, String text, Map<String, String> facts, Instant at) {
        public Statement {
            Objects.requireNonNull(id); Objects.requireNonNull(crimeId); Objects.requireNonNull(witness);
            Objects.requireNonNull(outcome); Objects.requireNonNull(text); Objects.requireNonNull(at);
            reliability = clamp01(reliability); facts = Map.copyOf(facts);
        }
    }

    private final CrimeEngine crimes;
    private final SocialEngine social;
    private final ActorRegistry actors;
    private final NpcStateRegistry states;

    public InvestigationService(CrimeEngine crimes, SocialEngine social, ActorRegistry actors, NpcStateRegistry states) {
        this.crimes = Objects.requireNonNull(crimes); this.social = Objects.requireNonNull(social);
        this.actors = Objects.requireNonNull(actors); this.states = states;
    }

    public Optional<Statement> interview(UUID crimeId, ActorId witness, ActorId interviewer) {
        CrimeEngine.Crime crime = crimes.crime(crimeId).orElse(null);
        if (crime == null || witness == null) return Optional.empty();
        boolean listedWitness = crime.witnesses().contains(witness);
        NpcSnapshot snapshot = npcSnapshot(witness);
        boolean remembered = snapshot != null && snapshot.recentMemories().stream().anyMatch(memory ->
                crimeId.toString().equals(memory.facts().get("crime")) ||
                        crime.facts().getOrDefault("event", "").equals(memory.facts().get("event")));
        if (!listedWitness && !remembered) {
            return Optional.of(record(crime, witness, null, Outcome.PARTIAL, 0.20D,
                    "Tôi không trực tiếp thấy chuyện đó. Tôi chỉ có thể nói những gì mình nghe được.",
                    Map.of("direct_witness", "false")));
        }

        double fear = snapshot == null ? 0.2D : snapshot.trait("fearful");
        double deceptive = snapshot == null ? 0D : snapshot.trait("deceptive");
        double observant = snapshot == null ? 0.5D : Math.max(snapshot.trait("observant"), snapshot.trait("perceptive"));
        SocialEngine.Relationship interviewerRelation = interviewer == null ? null : social.findRelationship(witness, interviewer).orElse(null);
        double trust = interviewerRelation == null ? 0D : interviewerRelation.trust();
        double relationFear = interviewerRelation == null ? 0D : interviewerRelation.fear();
        double cooperation = clamp01(0.55D + trust * 0.25D - fear * 0.20D - relationFear * 0.25D);

        if (cooperation < 0.22D) {
            return Optional.of(record(crime, witness, null, Outcome.REFUSED, 0.15D,
                    "Tôi không muốn dính thêm vào chuyện này.", Map.of("direct_witness", Boolean.toString(listedWitness))));
        }

        double reliability = clamp01(0.50D + observant * 0.30D + cooperation * 0.12D - fear * 0.12D);
        if (deceptive >= 0.68D && cooperation < 0.72D) {
            ActorId falseSubject = plausibleFalseSubject(crime, witness).orElse(null);
            if (falseSubject != null) {
                return Optional.of(record(crime, witness, falseSubject, Outcome.DECEPTIVE, Math.min(0.38D, reliability),
                        "Tôi thấy " + display(falseSubject) + " quanh khu vực đó. Tôi sẽ chỉ nói vậy thôi.",
                        Map.of("direct_witness", "true", "intentional_deception", "true")));
            }
        }

        if (reliability < 0.52D) {
            ActorId mistaken = plausibleFalseSubject(crime, witness).orElse(crime.perpetrator());
            return Optional.of(record(crime, witness, mistaken, Outcome.MISTAKEN, reliability,
                    "Tôi không chắc. Có thể là " + display(mistaken) + ", nhưng ký ức của tôi không rõ.",
                    Map.of("direct_witness", Boolean.toString(listedWitness), "uncertain", "true")));
        }

        ActorId subject = crime.perpetrator();
        if (subject == null) {
            return Optional.of(record(crime, witness, null, Outcome.PARTIAL, reliability,
                    "Tôi nhớ được bối cảnh, nhưng không nhận ra ai đủ chắc để chỉ mặt.", Map.of("direct_witness", "true")));
        }
        String motive = crime.motive().isBlank() ? "không rõ động cơ" : "có vẻ liên quan tới " + crime.motive();
        return Optional.of(record(crime, witness, subject, Outcome.TRUTHFUL, reliability,
                "Tôi thấy " + display(subject) + " liên quan trực tiếp. " + motive + ".",
                Map.of("direct_witness", "true", "motive", crime.motive())));
    }

    public List<CrimeEngine.SuspectScore> suspects(UUID crimeId) {
        Set<ActorId> candidates = actors.snapshot().actors().keySet().stream()
                .filter(actor -> actor.kind() == ActorId.Kind.NPC || actor.kind() == ActorId.Kind.PLAYER)
                .sorted(Comparator.comparing((ActorId actor) -> actor.kind().name()).thenComparing(actor -> actor.uuid().toString()))
                .limit(256).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return crimes.rankSuspects(crimeId, Set.copyOf(candidates));
    }

    private Statement record(CrimeEngine.Crime crime, ActorId witness, ActorId subject, Outcome outcome,
                             double reliability, String text, Map<String, String> extra) {
        Map<String, String> facts = new HashMap<>(extra);
        facts.put("statement_outcome", outcome.name()); facts.put("statement", text);
        CrimeEngine.EvidenceType evidenceType = outcome == Outcome.TRUTHFUL || outcome == Outcome.PARTIAL
                ? CrimeEngine.EvidenceType.WITNESS : CrimeEngine.EvidenceType.RUMOR;
        crimes.addEvidence(crime.id(), evidenceType, witness, subject, reliability,
                outcome == Outcome.REFUSED ? 0.10D : 0.72D, false, facts);
        if (states != null && witness.kind() == ActorId.Kind.NPC) {
            states.get(witness.uuid()).ifPresent(state -> state.remember("investigation_interview",
                    Map.of("crime", crime.id().toString(), "outcome", outcome.name(),
                            "subject", subject == null ? "" : subject.uuid().toString()), 0.62D, reliability));
        }
        return new Statement(UUID.randomUUID(), crime.id(), witness, subject, outcome, reliability, text, facts, Instant.now());
    }

    private Optional<ActorId> plausibleFalseSubject(CrimeEngine.Crime crime, ActorId witness) {
        List<ActorId> candidates = new ArrayList<>(actors.snapshot().actors().keySet());
        candidates.remove(witness); candidates.remove(crime.victim()); candidates.remove(crime.perpetrator());
        return candidates.stream().filter(actor -> actor.kind() == ActorId.Kind.NPC || actor.kind() == ActorId.Kind.PLAYER)
                .sorted(Comparator.comparingDouble((ActorId actor) -> social.findRelationship(witness, actor)
                                .map(SocialEngine.Relationship::hostility).orElse(0D)).reversed()
                        .thenComparing(actor -> actor.uuid().toString())).findFirst();
    }

    private NpcSnapshot npcSnapshot(ActorId actor) {
        return states == null || actor.kind() != ActorId.Kind.NPC ? null : states.snapshot(actor.uuid()).orElse(null);
    }

    private String display(ActorId actor) {
        if (actor == null) return "không rõ ai";
        return actors.get(actor).map(ActorSnapshot::displayName).orElse(actor.uuid().toString());
    }

    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
