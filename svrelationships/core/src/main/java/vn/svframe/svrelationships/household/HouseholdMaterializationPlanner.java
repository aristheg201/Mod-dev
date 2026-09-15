package vn.svframe.svrelationships.household;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class HouseholdMaterializationPlanner {
    public List<UUID> select(Collection<Candidate> candidates, int maximumMaterialized) {
        if (maximumMaterialized <= 0 || candidates.isEmpty()) return List.of();
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparing(Candidate::alreadyMaterialized).reversed()
                .thenComparingDouble(Candidate::distanceSquared)
                .thenComparing(candidate -> candidate.pokemonId().toString()));
        return ordered.stream().limit(maximumMaterialized).map(Candidate::pokemonId).toList();
    }

    public record Candidate(UUID pokemonId, double distanceSquared, boolean alreadyMaterialized) {
    }
}
