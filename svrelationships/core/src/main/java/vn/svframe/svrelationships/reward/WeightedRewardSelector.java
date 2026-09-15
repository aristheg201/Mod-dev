package vn.svframe.svrelationships.reward;

import vn.svframe.svrelationships.gameplay.RewardProfileDefinition;

import java.util.List;
import java.util.SplittableRandom;

public final class WeightedRewardSelector {
    public RewardResolution select(RewardProfileDefinition profile, long seed) {
        List<RewardProfileDefinition.Entry> entries = profile.entries();
        if (entries.isEmpty()) {
            throw new IllegalStateException("Reward profile has no entries: " + profile.id());
        }
        long totalWeight = 0;
        for (RewardProfileDefinition.Entry entry : entries) {
            if (entry.weight() <= 0) continue;
            totalWeight = Math.addExact(totalWeight, entry.weight());
        }
        if (totalWeight <= 0) {
            throw new IllegalStateException("Reward profile has no positive weights: " + profile.id());
        }
        SplittableRandom random = new SplittableRandom(seed);
        long roll = random.nextLong(totalWeight);
        RewardProfileDefinition.Entry selected = null;
        long cursor = 0;
        for (RewardProfileDefinition.Entry entry : entries) {
            if (entry.weight() <= 0) continue;
            cursor += entry.weight();
            if (roll < cursor) {
                selected = entry;
                break;
            }
        }
        if (selected == null) selected = entries.get(entries.size() - 1);
        long min = selected.minimumAmount();
        long max = selected.maximumAmount();
        if (min < 0 || max < min) {
            throw new IllegalStateException("Invalid amount range for reward entry " + selected.id());
        }
        long amount = min == max ? min : random.nextLong(min, Math.addExact(max, 1));
        return new RewardResolution(selected.id(), selected.rewardType(), selected.value(), amount);
    }
}
