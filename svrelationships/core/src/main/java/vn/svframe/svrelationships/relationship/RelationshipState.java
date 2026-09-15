package vn.svframe.svrelationships.relationship;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class RelationshipState {
    private final RelationshipKey key;
    private final Map<String, Long> progression = new HashMap<>();
    private final Map<String, String> routes = new HashMap<>();
    private boolean partner;

    public RelationshipState(RelationshipKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public RelationshipKey key() {
        return key;
    }

    public long progression(String trackId) {
        return progression.getOrDefault(trackId, 0L);
    }

    public void setProgression(String trackId, long value) {
        progression.put(Objects.requireNonNull(trackId, "trackId"), value);
    }

    public Map<String, Long> progressionSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(progression));
    }

    public void setRouteState(String routeId, String stateId) {
        routes.put(Objects.requireNonNull(routeId, "routeId"), Objects.requireNonNull(stateId, "stateId"));
    }

    public String routeState(String routeId) {
        return routes.get(routeId);
    }

    public Map<String, String> routeSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(routes));
    }

    public boolean partner() {
        return partner;
    }

    public void setPartner(boolean partner) {
        this.partner = partner;
    }
}
