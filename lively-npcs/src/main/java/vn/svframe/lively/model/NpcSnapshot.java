package vn.svframe.lively.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record NpcSnapshot(
        UUID id,
        long revision,
        Instant capturedAt,
        String name,
        String role,
        Map<String, Double> traits,
        Map<String, Double> needs,
        Map<String, BeliefView> beliefs,
        Map<UUID, RelationshipView> relationships,
        List<MemoryView> recentMemories
) {
    public NpcSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(name);
        Objects.requireNonNull(role);
        traits = Map.copyOf(traits);
        needs = Map.copyOf(needs);
        beliefs = Map.copyOf(beliefs);
        relationships = Map.copyOf(relationships);
        recentMemories = List.copyOf(recentMemories);
    }

    public double trait(String key) { return traits.getOrDefault(normalize(key), 0.5D); }
    public double need(String key) { return needs.getOrDefault(normalize(key), 0D); }
    public Optional<BeliefView> belief(String key) { return Optional.ofNullable(beliefs.get(normalize(key))); }
    public String beliefValue(String key) { return belief(key).map(BeliefView::value).orElse(null); }
    public RelationshipView relationship(UUID subject) {
        return relationships.getOrDefault(subject, new RelationshipView(subject, 0D, 0D, 0D, 0D, 0L));
    }

    public record BeliefView(String key, String value, double confidence, UUID source, Instant updatedAt) {
        public BeliefView {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            Objects.requireNonNull(updatedAt);
            confidence = clamp01(confidence);
        }
    }

    public record RelationshipView(UUID subject, double trust, double affinity, double suspicion, double fear, long interactions) {
        public RelationshipView {
            Objects.requireNonNull(subject);
            trust = clampSigned(trust);
            affinity = clampSigned(affinity);
            suspicion = clamp01(suspicion);
            fear = clamp01(fear);
            interactions = Math.max(0L, interactions);
        }
    }

    public record MemoryView(UUID id, Instant occurredAt, String type, Map<String, String> facts, double importance, double confidence) {
        public MemoryView {
            Objects.requireNonNull(id);
            Objects.requireNonNull(occurredAt);
            Objects.requireNonNull(type);
            facts = Map.copyOf(facts);
            importance = clamp01(importance);
            confidence = clamp01(confidence);
        }
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
    private static double clamp01(double value) { return Math.max(0D, Math.min(1D, value)); }
    private static double clampSigned(double value) { return Math.max(-1D, Math.min(1D, value)); }
}
