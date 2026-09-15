package vn.svframe.svrelationships.gameplay;

import vn.svframe.svrelationships.family.DaycareDefinition;
import vn.svframe.svrelationships.family.InheritanceDefinition;

import java.util.Map;

public record GameplayDefinitions(
        long generation,
        Map<String, ProgressionTrackDefinition> progressionTracks,
        Map<String, RouteDefinition> routes,
        PartnerCapacityDefinition partnerCapacity,
        Map<String, RewardProfileDefinition> rewardProfiles,
        Map<String, InteractionDefinition> interactions,
        Map<String, GiftDefinition> gifts,
        Map<String, PersonalityDefinition> personalities,
        Map<String, DaycareDefinition> daycareDefinitions,
        Map<String, InheritanceDefinition> inheritanceDefinitions
) {
    public GameplayDefinitions {
        progressionTracks = Map.copyOf(progressionTracks);
        routes = Map.copyOf(routes);
        rewardProfiles = Map.copyOf(rewardProfiles);
        interactions = Map.copyOf(interactions);
        gifts = Map.copyOf(gifts);
        personalities = Map.copyOf(personalities);
        daycareDefinitions = Map.copyOf(daycareDefinitions);
        inheritanceDefinitions = Map.copyOf(inheritanceDefinitions);
    }
}
