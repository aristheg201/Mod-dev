package vn.svframe.lively.actor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable actor input for AI/event workers. Players are actors, not a special-case API. */
public record ActorSnapshot(
        ActorId id,
        long revision,
        Instant capturedAt,
        String displayName,
        Map<String, Double> socialStats,
        Map<String, String> facts,
        Set<String> tags
) {
    public ActorSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(displayName);
        socialStats = Map.copyOf(socialStats);
        facts = Map.copyOf(facts);
        tags = Set.copyOf(tags);
    }

    public double social(String key) { return socialStats.getOrDefault(normalize(key), 0D); }
    public String fact(String key) { return facts.get(normalize(key)); }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
