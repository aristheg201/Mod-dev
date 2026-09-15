package vn.svframe.svrelationships.integration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PokemonProvider {
    String id();
    boolean available();
    List<PokemonSnapshot> ownedPokemon(UUID playerId);
    Optional<PokemonSnapshot> findOwned(UUID playerId, UUID pokemonId);

    record PokemonSnapshot(
            UUID pokemonId,
            String speciesId,
            String formId,
            Set<String> aspects,
            Optional<String> gender,
            int level
    ) {
        public PokemonSnapshot {
            aspects = Set.copyOf(aspects);
        }
    }
}
