package vn.svframe.lively.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable world facts visible to one NPC at one revision. */
public record WorldSnapshot(
        long revision,
        String world,
        long gameTime,
        List<ObservedEntity> entities,
        Map<String, Double> signals
) {
    public WorldSnapshot {
        Objects.requireNonNull(world);
        entities = List.copyOf(entities);
        signals = Map.copyOf(signals);
    }

    public record ObservedEntity(UUID id, String kind, double threat) {
        public ObservedEntity {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            threat = Math.max(0D, Math.min(1D, threat));
        }
    }
}
