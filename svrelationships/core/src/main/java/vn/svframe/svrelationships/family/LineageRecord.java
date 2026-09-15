package vn.svframe.svrelationships.family;

import java.util.List;
import java.util.UUID;

public record LineageRecord(
        UUID pokemonId,
        UUID ownerId,
        List<UUID> parentPokemonIds,
        UUID householdId,
        int generation,
        long bornAtMillis,
        String inheritanceProfile,
        UUID daycareSessionId
) {
    public LineageRecord {
        parentPokemonIds = List.copyOf(parentPokemonIds);
        if (generation < 1) throw new IllegalArgumentException("generation");
    }
}
