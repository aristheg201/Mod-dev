package vn.svframe.svrelationships.gameplay;

import java.util.Map;

public record InteractionDefinition(
        String id,
        long cooldownMillis,
        Map<String, Long> progressionDeltas,
        String requiredRoute,
        String requiredState,
        String messageKey
) {
    public InteractionDefinition {
        progressionDeltas = Map.copyOf(progressionDeltas);
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis");
    }
}
