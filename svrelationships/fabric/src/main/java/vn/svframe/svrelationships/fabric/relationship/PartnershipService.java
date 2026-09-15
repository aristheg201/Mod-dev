package vn.svframe.svrelationships.fabric.relationship;

import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.RelationshipRuleService;
import vn.svframe.svrelationships.fabric.household.HouseholdService;
import vn.svframe.svrelationships.gameplay.PartnershipDefinition;
import vn.svframe.svrelationships.gameplay.ProgressionEngine;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.util.UUID;

public final class PartnershipService {
    private final GameplayDefinitionService gameplay;
    private final RelationshipRuleService rules;
    private final RelationshipService relationships;
    private final HouseholdService households;
    private final ProviderHub providers;

    public PartnershipService(GameplayDefinitionService gameplay, RelationshipRuleService rules, RelationshipService relationships,
                              HouseholdService households, ProviderHub providers) {
        this.gameplay = gameplay;
        this.rules = rules;
        this.relationships = relationships;
        this.households = households;
        this.providers = providers;
    }

    public Result advance(UUID playerId, UUID pokemonId, String milestoneId) {
        PartnershipDefinition definition = rules.snapshot().partnership();
        PartnershipDefinition.Milestone milestone = definition.milestones().get(milestoneId);
        if (milestone == null) return Result.UNKNOWN_MILESTONE;
        var pokemonProvider = providers.pokemonProvider();
        if (pokemonProvider.isEmpty()) return Result.POKEMON_PROVIDER_UNAVAILABLE;
        var pokemon = pokemonProvider.get().findOwned(playerId, pokemonId);
        if (pokemon.isEmpty()) return Result.NOT_OWNED;
        var selector = rules.snapshot().selectors().get(milestone.selectorId());
        if (selector == null || !selector.matches(pokemon.get())) return Result.SELECTOR_REJECTED;
        if (milestone.requireHousehold() && households.get(playerId).isEmpty()) return Result.HOUSEHOLD_REQUIRED;

        ProgressionEngine progression = new ProgressionEngine(gameplay.snapshot().progressionTracks());
        for (var requirement : milestone.requiredRanks().entrySet()) {
            long value = relationships.progression(playerId, pokemonId, requirement.getKey());
            if (!progression.atLeastRank(requirement.getKey(), value, requirement.getValue())) return Result.PROGRESSION_REQUIRED;
        }
        if (milestone.createsPartnership() && !relationships.state(playerId, pokemonId).partner() && !relationships.canAddPartner(playerId)) {
            return Result.CAPACITY_FULL;
        }
        try {
            relationships.transition(playerId, pokemonId, definition.routeId(), milestone.targetState());
        } catch (IllegalStateException exception) {
            return Result.INVALID_ROUTE_TRANSITION;
        }
        if (milestone.createsPartnership()) {
            if (!relationships.setPartner(playerId, pokemonId, true, false)) return Result.CAPACITY_FULL;
            households.get(playerId).ifPresent(household -> relationships.assignHousehold(playerId, pokemonId, household.householdId()));
        }
        return Result.SUCCESS;
    }

    public enum Result {
        SUCCESS, UNKNOWN_MILESTONE, POKEMON_PROVIDER_UNAVAILABLE, NOT_OWNED, SELECTOR_REJECTED,
        HOUSEHOLD_REQUIRED, PROGRESSION_REQUIRED, CAPACITY_FULL, INVALID_ROUTE_TRANSITION
    }
}
