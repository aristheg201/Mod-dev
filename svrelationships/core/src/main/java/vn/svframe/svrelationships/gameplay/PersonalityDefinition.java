package vn.svframe.svrelationships.gameplay;

import java.util.Map;
import java.util.Set;

public record PersonalityDefinition(
        String id,
        Set<String> tags,
        Map<String, Double> progressionMultipliers,
        Map<String, Double> rewardWeightMultipliers
) {
    public PersonalityDefinition {
        tags = Set.copyOf(tags);
        progressionMultipliers = Map.copyOf(progressionMultipliers);
        rewardWeightMultipliers = Map.copyOf(rewardWeightMultipliers);
    }
}
