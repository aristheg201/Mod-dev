package vn.svframe.lively.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable NPC input for worker-thread reasoning. */
public record NpcSnapshot(
        UUID id,
        long revision,
        String name,
        String role,
        Map<String, Double> traits,
        Map<String, Double> needs,
        Map<String, String> beliefs
) {
    public NpcSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(role);
        traits = Map.copyOf(traits);
        needs = Map.copyOf(needs);
        beliefs = Map.copyOf(beliefs);
    }

    public double trait(String key) { return traits.getOrDefault(key, 0.5D); }
    public double need(String key) { return needs.getOrDefault(key, 0D); }
}
