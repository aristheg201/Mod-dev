package dev.svextract.config;

import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;

/** Pure deterministic weighted selection. World and item registry calls remain on the server thread. */
public final class WeightedLoot {
    private WeightedLoot() {}

    public record Entry(String item, int count, int weight) {
        public Entry {
            Objects.requireNonNull(item, "item");
            if (!item.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+") || count < 1 || count > 64 || weight < 1 || weight > 1000)
                throw new IllegalArgumentException("Invalid weighted loot entry");
        }
    }

    public static Entry choose(UUID raid, String cacheId, List<Entry> choices) {
        Objects.requireNonNull(raid, "raid");
        Objects.requireNonNull(cacheId, "cacheId");
        Objects.requireNonNull(choices, "choices");
        if (choices.isEmpty() || choices.size() > 12) throw new IllegalArgumentException("Loot pool must contain 1-12 choices");
        int total = 0;
        for (Entry entry : choices) total = Math.addExact(total, Objects.requireNonNull(entry).weight());
        long seed = raid.getMostSignificantBits() ^ Long.rotateLeft(raid.getLeastSignificantBits(), 31)
            ^ ((long) cacheId.hashCode() * 0x9E3779B97F4A7C15L);
        int roll = new SplittableRandom(seed).nextInt(total);
        for (Entry entry : choices) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        throw new IllegalStateException("Unreachable weighted loot selection");
    }
}
