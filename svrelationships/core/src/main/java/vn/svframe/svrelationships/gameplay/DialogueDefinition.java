package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DialogueDefinition(
        String id,
        String requiredRoute,
        String requiredState,
        Set<String> requiredPersonalityTags,
        long cooldownMillis,
        List<Entry> entries
) {
    public DialogueDefinition {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(requiredRoute, "requiredRoute"); Objects.requireNonNull(requiredState, "requiredState");
        requiredPersonalityTags = Set.copyOf(requiredPersonalityTags); entries = List.copyOf(entries);
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis");
    }
    public record Entry(String id, int weight, String messageKey) {
        public Entry { Objects.requireNonNull(id, "id"); Objects.requireNonNull(messageKey, "messageKey"); if (weight <= 0) throw new IllegalArgumentException("weight"); }
    }
}
