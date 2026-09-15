package vn.svframe.svrelationships.gameplay;

import java.util.Map;
import java.util.Objects;

public record AnniversaryDefinition(
        String id,
        long periodMillis,
        int minimumCycles,
        String rewardProfile,
        Map<String, Long> progressionDeltas,
        String messageKey
) {
    public AnniversaryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rewardProfile, "rewardProfile");
        Objects.requireNonNull(messageKey, "messageKey");
        progressionDeltas = Map.copyOf(progressionDeltas);
        if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis");
        if (minimumCycles < 1) throw new IllegalArgumentException("minimumCycles");
    }
}
