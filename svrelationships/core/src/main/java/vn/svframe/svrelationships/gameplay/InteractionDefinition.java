package vn.svframe.svrelationships.gameplay;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record InteractionDefinition(
        String id,
        long cooldownMillis,
        Map<String, Long> progressionDeltas,
        String requiredRoute,
        List<String> requiredStates,
        String messageKey
) {
    public InteractionDefinition {
        progressionDeltas = Map.copyOf(progressionDeltas);
        requiredStates = List.copyOf(requiredStates);
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis");
    }

    /** Backward-compatible constructor for existing YAML parser/tests. */
    public InteractionDefinition(
            String id,
            long cooldownMillis,
            Map<String, Long> progressionDeltas,
            String requiredRoute,
            String requiredState,
            String messageKey
    ) {
        this(id, cooldownMillis, progressionDeltas, requiredRoute, parseLegacyStates(requiredState), messageKey);
    }

    /**
     * Compatibility accessor for the current loader validator. The runtime uses
     * requiredStates(); returning the first state here keeps legacy validation
     * boot-safe until the loader schema is fully list-native.
     */
    public String requiredState() {
        return requiredStates.isEmpty() ? "" : requiredStates.getFirst();
    }

    public boolean allowsState(String currentState) {
        return requiredStates.isEmpty() || requiredStates.contains(currentState);
    }

    private static List<String> parseLegacyStates(String value) {
        if (value == null || value.isBlank() || "*".equals(value.trim())) return List.of();
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(state -> !state.isEmpty())
                .distinct()
                .toList();
    }
}
