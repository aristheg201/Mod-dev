package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Objects;

public record ProgressionTrackDefinition(
        String id,
        long minimum,
        long maximum,
        List<Rank> ranks
) {
    public ProgressionTrackDefinition {
        Objects.requireNonNull(id, "id");
        ranks = List.copyOf(ranks);
        if (maximum < minimum) {
            throw new IllegalArgumentException("maximum must be >= minimum");
        }
    }

    public long clamp(long value) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Rank(String id, long minimumValue, String displayKey) {
        public Rank {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayKey, "displayKey");
        }
    }
}
