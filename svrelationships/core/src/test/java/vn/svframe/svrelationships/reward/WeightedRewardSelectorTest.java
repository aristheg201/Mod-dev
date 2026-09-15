package vn.svframe.svrelationships.reward;

import org.junit.jupiter.api.Test;
import vn.svframe.svrelationships.gameplay.RewardProfileDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRewardSelectorTest {
    @Test
    void sameSeedAlwaysResolvesSameReward() {
        RewardProfileDefinition profile = new RewardProfileDefinition(
                "daily", "periodic", "1d", "per_partner",
                List.of(
                        new RewardProfileDefinition.Entry("common", 90, "item", "minecraft:apple", 1, 3),
                        new RewardProfileDefinition.Entry("rare", 10, "item", "minecraft:diamond", 1, 1)
                )
        );
        WeightedRewardSelector selector = new WeightedRewardSelector();
        RewardResolution first = selector.select(profile, 123456789L);
        RewardResolution second = selector.select(profile, 123456789L);
        assertEquals(first, second);
        assertTrue(first.amount() >= 1 && first.amount() <= 3);
    }
}
