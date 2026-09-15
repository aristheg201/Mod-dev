package vn.svframe.svrelationships.family;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DaycareEngine {
    private final Map<String, DaycareDefinition> definitions;

    public DaycareEngine(Map<String, DaycareDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public DaycareSession start(UUID ownerId, String definitionId, List<UUID> participants, long nowMillis) {
        DaycareDefinition definition = requireDefinition(definitionId);
        Objects.requireNonNull(ownerId, "ownerId");
        if (participants.size() != definition.participantRoles().size()) {
            throw new IllegalArgumentException("Expected " + definition.participantRoles().size() + " participants for " + definitionId);
        }
        if (participants.stream().distinct().count() != participants.size()) {
            throw new IllegalArgumentException("Duplicate daycare participant");
        }
        long completeAt = Math.addExact(nowMillis, definition.durationMillis());
        return new DaycareSession(UUID.randomUUID(), ownerId, definitionId, participants, nowMillis, completeAt, "ACTIVE", null);
    }

    public DaycareDefinition requireDefinition(String id) {
        DaycareDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown daycare definition: " + id);
        return definition;
    }
}
