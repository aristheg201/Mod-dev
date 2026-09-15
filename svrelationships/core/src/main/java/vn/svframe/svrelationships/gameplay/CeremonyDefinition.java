package vn.svframe.svrelationships.gameplay;

import java.util.Map;
import java.util.Objects;

public record CeremonyDefinition(
        String id,
        String requiredRoute,
        String requiredState,
        String partnershipMilestone,
        boolean exclusiveMilestone,
        boolean requireHousehold,
        boolean repeatable,
        long cooldownMillis,
        Map<String, Long> progressionDeltas,
        String messageKey
) {
    public CeremonyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredRoute, "requiredRoute");
        Objects.requireNonNull(requiredState, "requiredState");
        Objects.requireNonNull(partnershipMilestone, "partnershipMilestone");
        Objects.requireNonNull(messageKey, "messageKey");
        progressionDeltas = Map.copyOf(progressionDeltas);
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis");
    }
}
