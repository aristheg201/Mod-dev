package vn.svframe.svrelationships.family;

import java.util.List;
import java.util.Map;

public record DaycareDefinition(
        String id,
        String mode,
        List<String> participantRoles,
        Map<String, String> participantRequirements,
        long durationMillis,
        String inheritanceProfile,
        String rewardProfile,
        String economyProvider,
        String currency,
        long cost
) {
    public DaycareDefinition {
        participantRoles = List.copyOf(participantRoles);
        participantRequirements = Map.copyOf(participantRequirements);
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis");
        if (cost < 0) throw new IllegalArgumentException("cost");
    }
}
