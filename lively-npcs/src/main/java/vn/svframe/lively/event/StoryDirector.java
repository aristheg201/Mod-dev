package vn.svframe.lively.event;

import vn.svframe.lively.actor.ActorId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded story director. It proposes causal event seeds; it never authors block mutations or commands. */
public final class StoryDirector {
    public record Tension(String id, WorldEventEngine.Category category, String structureId,
                          double severity, double novelty, Set<ActorId> actors, Map<String, String> facts) {
        public Tension {
            Objects.requireNonNull(id); Objects.requireNonNull(category);
            severity = clamp01(severity); novelty = clamp01(novelty);
            actors = Set.copyOf(actors); facts = Map.copyOf(facts);
        }
    }

    public List<WorldEventEngine.EventProposal> propose(List<Tension> tensions, int maxProposals) {
        int limit = Math.max(0, Math.min(16, maxProposals));
        if (limit == 0) return List.of();
        List<Tension> ranked = new ArrayList<>(tensions);
        ranked.sort(Comparator.comparingDouble(this::score).reversed());
        List<WorldEventEngine.EventProposal> result = new ArrayList<>();
        for (Tension tension : ranked) {
            if (result.size() >= limit) break;
            double score = score(tension);
            if (score < 0.35D) continue;
            Duration duration = Duration.ofMinutes(Math.max(5L, Math.min(720L, Math.round(20D + score * 220D))));
            result.add(new WorldEventEngine.EventProposal(
                    tension.category(), tension.id(), tension.structureId(), tension.actors(),
                    clamp01(tension.severity()), duration, tension.facts()));
        }
        return List.copyOf(result);
    }

    private double score(Tension tension) {
        double socialWeight = switch (tension.category()) {
            case CRIME, FACTION_CONFLICT, POLITICAL, MYSTERY -> 0.12D;
            case ECONOMIC, DISASTER, MIGRATION -> 0.08D;
            default -> 0.04D;
        };
        return clamp01(tension.severity() * 0.62D + tension.novelty() * 0.30D + socialWeight);
    }

    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
}
