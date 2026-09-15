package vn.svframe.svrelationships.family;

import java.util.List;
import java.util.UUID;

public record DaycareSession(
        UUID sessionId,
        UUID ownerId,
        String definitionId,
        List<UUID> participantPokemonIds,
        long startedAtMillis,
        long completeAtMillis,
        String status,
        UUID producedPokemonId
) {
    public DaycareSession { participantPokemonIds = List.copyOf(participantPokemonIds); }

    public boolean due(long nowMillis) {
        return ("ACTIVE".equals(status) || "REWARD_PENDING".equals(status)) && nowMillis >= completeAtMillis;
    }
    public DaycareSession withStatus(String nextStatus) { return new DaycareSession(sessionId, ownerId, definitionId, participantPokemonIds, startedAtMillis, completeAtMillis, nextStatus, producedPokemonId); }
    public DaycareSession awaitingReward(UUID pokemonId) { return new DaycareSession(sessionId, ownerId, definitionId, participantPokemonIds, startedAtMillis, completeAtMillis, "REWARD_PENDING", pokemonId); }
    public DaycareSession completed(UUID pokemonId) { return new DaycareSession(sessionId, ownerId, definitionId, participantPokemonIds, startedAtMillis, completeAtMillis, "COMPLETED", pokemonId); }
}
