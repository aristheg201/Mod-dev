package vn.svframe.svrelationships.reward;

public record RewardResolution(
        String entryId,
        String rewardType,
        String value,
        long amount
) {
    public RewardResolution {
        if (entryId == null || entryId.isBlank()) throw new IllegalArgumentException("entryId");
        if (rewardType == null || rewardType.isBlank()) throw new IllegalArgumentException("rewardType");
        if (value == null) throw new IllegalArgumentException("value");
        if (amount < 0) throw new IllegalArgumentException("amount");
    }
}
