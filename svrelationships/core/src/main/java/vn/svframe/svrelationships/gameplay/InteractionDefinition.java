package vn.svframe.svrelationships.gameplay;

import java.util.Map;
import java.util.Set;

public record InteractionDefinition(
        String id,
        long cooldownMillis,
        Map<String, Long> progressionDeltas,
        String requiredRoute,
        Set<String> requiredStates,
        String messageKey
) {
    public InteractionDefinition {
        progressionDeltas = Map.copyOf(progressionDeltas);
        requiredStates = Set.copyOf(requiredStates);
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis");
    }
}
