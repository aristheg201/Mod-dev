package vn.svframe.svrelationships.gameplay;

import java.util.Map;

public record GiftDefinition(
        String id,
        String itemId,
        long cooldownMillis,
        int consumeAmount,
        Map<String, Long> progressionDeltas,
        String messageKey
) {
    public GiftDefinition {
        progressionDeltas = Map.copyOf(progressionDeltas);
        if (cooldownMillis < 0 || consumeAmount < 1) throw new IllegalArgumentException("invalid gift definition");
    }
}
