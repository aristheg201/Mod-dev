package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Objects;

public record RewardProfileDefinition(
        String id,
        String triggerType,
        String period,
        String scope,
        List<Entry> entries
) {
    public RewardProfileDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(scope, "scope");
        entries = List.copyOf(entries);
    }

    public record Entry(
            String id,
            int weight,
            String rewardType,
            String value,
            long minimumAmount,
            long maximumAmount
    ) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rewardType, "rewardType");
            Objects.requireNonNull(value, "value");
            if (weight <= 0 || minimumAmount < 0 || maximumAmount < minimumAmount) {
                throw new IllegalArgumentException("invalid reward entry: " + id);
            }
        }
    }
}
