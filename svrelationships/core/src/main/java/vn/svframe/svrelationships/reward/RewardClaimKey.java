package vn.svframe.svrelationships.reward;

import java.util.Objects;
import java.util.UUID;

public record RewardClaimKey(UUID relationshipId, String rewardProfileId, String periodId) {
    public RewardClaimKey {
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(rewardProfileId, "rewardProfileId");
        Objects.requireNonNull(periodId, "periodId");
    }
}
