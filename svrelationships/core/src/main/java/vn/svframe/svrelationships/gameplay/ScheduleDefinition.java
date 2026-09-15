package vn.svframe.svrelationships.gameplay;

import java.util.List;
import java.util.Objects;

public record ScheduleDefinition(String id, List<Entry> entries) {
    public ScheduleDefinition {
        Objects.requireNonNull(id, "id");
        entries = List.copyOf(entries);
    }

    public record Entry(String id, int startTick, int endTick, String activityId, boolean materialize, String messageKey) {
        public Entry {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(activityId, "activityId"); Objects.requireNonNull(messageKey, "messageKey");
            if (startTick < 0 || startTick >= 24000 || endTick < 0 || endTick >= 24000) throw new IllegalArgumentException("schedule tick must be within 0..23999");
        }
        public boolean contains(int tick) { return startTick <= endTick ? tick >= startTick && tick < endTick : tick >= startTick || tick < endTick; }
    }
}
