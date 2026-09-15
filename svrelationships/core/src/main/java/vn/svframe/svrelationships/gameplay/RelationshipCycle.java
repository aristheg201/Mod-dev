package vn.svframe.svrelationships.gameplay;

import java.util.Objects;

public record RelationshipCycle(long index, long startedAtMillis, long endsAtMillis) {
    public static RelationshipCycle resolve(long relationshipStartedAtMillis, long nowMillis, long periodMillis) {
        if (relationshipStartedAtMillis <= 0) throw new IllegalArgumentException("relationshipStartedAtMillis");
        if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis");
        long elapsed = Math.max(0L, nowMillis - relationshipStartedAtMillis);
        long index = Math.floorDiv(elapsed, periodMillis);
        long startedAt = Math.addExact(relationshipStartedAtMillis, Math.multiplyExact(index, periodMillis));
        return new RelationshipCycle(index, startedAt, Math.addExact(startedAt, periodMillis));
    }

    public String claimId(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        if (eventId.isBlank()) throw new IllegalArgumentException("eventId");
        return "anniversary:" + eventId + ":" + index;
    }
}
