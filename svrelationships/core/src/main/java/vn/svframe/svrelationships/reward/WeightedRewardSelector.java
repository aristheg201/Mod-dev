package vn.svframe.svrelationships.reward;

import vn.svframe.svrelationships.gameplay.RewardProfileDefinition;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class WeightedRewardSelector {
    public RewardResolution select(RewardProfileDefinition profile, long seed) {
        return select(profile, seed, Map.of());
    }

    public RewardResolution select(RewardProfileDefinition profile, long seed, Map<String, Double> weightMultipliers) {
        List<RewardProfileDefinition.Entry> entries = profile.entries();
        if (entries.isEmpty()) throw new IllegalStateException("Reward profile has no entries: " + profile.id());

        long[] effectiveWeights = new long[entries.size()];
        long totalWeight = 0L;
        for (int i = 0; i < entries.size(); i++) {
            RewardProfileDefinition.Entry entry = entries.get(i);
            double multiplier = weightMultipliers.getOrDefault(entry.id(), 1.0D);
            if (!Double.isFinite(multiplier) || multiplier < 0.0D) {
                throw new IllegalArgumentException("Invalid reward weight multiplier for " + entry.id());
            }
            long effective = multiplier == 0.0D ? 0L : Math.max(1L, Math.round(entry.weight() * multiplier));
            effectiveWeights[i] = effective;
            totalWeight = Math.addExact(totalWeight, effective);
        }
        if (totalWeight <= 0L) throw new IllegalStateException("Reward profile has no positive effective weights: " + profile.id());

        SplittableRandom random = new SplittableRandom(seed);
        long roll = random.nextLong(totalWeight);
        RewardProfileDefinition.Entry selected = null;
        long cursor = 0L;
        for (int i = 0; i < entries.size(); i++) {
            long effective = effectiveWeights[i];
            if (effective <= 0L) continue;
            cursor = Math.addExact(cursor, effective);
            if (roll < cursor) {
                selected = entries.get(i);
                break;
            }
        }
        if (selected == null) throw new IllegalStateException("Unable to resolve reward selection for " + profile.id());

        long min = selected.minimumAmount();
        long max = selected.maximumAmount();
        if (min < 0 || max < min) throw new IllegalStateException("Invalid amount range for reward entry " + selected.id());
        long amount = min == max ? min : random.nextLong(min, Math.addExact(max, 1));
        return new RewardResolution(selected.id(), selected.rewardType(), selected.value(), amount);
    }
}
