package vn.svframe.svrelationships.family;

import java.util.List;

public record DaycareDefinition(
        String id,
        String mode,
        List<String> participantRoles,
        long durationMillis,
        String inheritanceProfile,
        String rewardProfile,
        String economyProvider,
        String currency,
        long cost
) {
    public DaycareDefinition {
        participantRoles = List.copyOf(participantRoles);
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis");
        if (cost < 0) throw new IllegalArgumentException("cost");
    }
}
