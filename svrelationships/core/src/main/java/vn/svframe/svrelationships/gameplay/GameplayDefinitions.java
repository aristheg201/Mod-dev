package vn.svframe.svrelationships.gameplay;

import java.util.Map;

public record GameplayDefinitions(
        long generation,
        Map<String, ProgressionTrackDefinition> progressionTracks,
        Map<String, RouteDefinition> routes,
        PartnerCapacityDefinition partnerCapacity,
        Map<String, RewardProfileDefinition> rewardProfiles
) {
    public GameplayDefinitions {
        progressionTracks = Map.copyOf(progressionTracks);
        routes = Map.copyOf(routes);
        rewardProfiles = Map.copyOf(rewardProfiles);
    }
}
