package vn.svframe.svrelationships.gameplay;

import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ProgressionEngine {
    private final Map<String, ProgressionTrackDefinition> tracks;

    public ProgressionEngine(Map<String, ProgressionTrackDefinition> tracks) {
        this.tracks = Map.copyOf(tracks);
    }

    public long set(RelationshipState state, String trackId, long requestedValue) {
        Objects.requireNonNull(state, "state");
        ProgressionTrackDefinition track = requireTrack(trackId);
        long clamped = Math.max(track.minimum(), Math.min(track.maximum(), requestedValue));
        state.setProgression(trackId, clamped);
        return clamped;
    }

    public long add(RelationshipState state, String trackId, long delta) {
        return set(state, trackId, Math.addExact(state.progression(trackId), delta));
    }

    public Optional<ProgressionTrackDefinition.Rank> rank(String trackId, long value) {
        ProgressionTrackDefinition track = requireTrack(trackId);
        return track.ranks().stream()
                .filter(rank -> value >= rank.minimumValue())
                .max(Comparator.comparingLong(ProgressionTrackDefinition.Rank::minimumValue));
    }

    public boolean atLeastRank(String trackId, long value, String rankId) {
        ProgressionTrackDefinition track = requireTrack(trackId);
        ProgressionTrackDefinition.Rank required = track.ranks().stream()
                .filter(rank -> rank.id().equals(rankId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown rank " + rankId + " for track " + trackId));
        return value >= required.minimumValue();
    }

    public ProgressionTrackDefinition requireTrack(String trackId) {
        ProgressionTrackDefinition track = tracks.get(trackId);
        if (track == null) {
            throw new IllegalArgumentException("Unknown progression track: " + trackId);
        }
        return track;
    }
}
