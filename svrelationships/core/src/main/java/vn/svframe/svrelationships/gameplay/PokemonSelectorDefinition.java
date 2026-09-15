package vn.svframe.svrelationships.gameplay;

import vn.svframe.svrelationships.integration.PokemonProvider;

import java.util.Set;

public record PokemonSelectorDefinition(
        String id,
        Set<String> species,
        Set<String> forms,
        Set<String> requiredAspects,
        Set<String> genders,
        int minimumLevel,
        int maximumLevel
) {
    public PokemonSelectorDefinition {
        species = Set.copyOf(species);
        forms = Set.copyOf(forms);
        requiredAspects = Set.copyOf(requiredAspects);
        genders = Set.copyOf(genders);
        if (minimumLevel < 0 || maximumLevel < minimumLevel) throw new IllegalArgumentException("invalid selector level range: " + id);
    }

    public boolean matches(PokemonProvider.PokemonSnapshot pokemon) {
        if (!species.isEmpty() && !species.contains(pokemon.speciesId())) return false;
        if (!forms.isEmpty() && !forms.contains(pokemon.formId())) return false;
        if (!requiredAspects.isEmpty() && !pokemon.aspects().containsAll(requiredAspects)) return false;
        if (!genders.isEmpty() && (pokemon.gender().isEmpty() || !genders.contains(pokemon.gender().get()))) return false;
        return pokemon.level() >= minimumLevel && pokemon.level() <= maximumLevel;
    }
}
