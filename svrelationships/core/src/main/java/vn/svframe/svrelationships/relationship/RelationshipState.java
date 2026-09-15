package vn.svframe.svrelationships.relationship;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RelationshipState {
    private final RelationshipKey key;
    private final UUID relationshipId;
    private final Map<String, Long> progression = new HashMap<>();
    private final Map<String, String> routes = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, String> flags = new HashMap<>();
    private boolean partner;
    private long partnerSinceMillis;
    private String personalityId;
    private UUID householdId;

    public RelationshipState(RelationshipKey key) {
        this.key = Objects.requireNonNull(key, "key");
        this.relationshipId = UUID.nameUUIDFromBytes((key.playerId() + ":" + key.pokemonId()).getBytes(StandardCharsets.UTF_8));
    }

    public RelationshipKey key() { return key; }
    public UUID relationshipId() { return relationshipId; }
    public long progression(String trackId) { return progression.getOrDefault(trackId, 0L); }
    public void setProgression(String trackId, long value) { progression.put(Objects.requireNonNull(trackId, "trackId"), value); }
    public Map<String, Long> progressionSnapshot() { return Collections.unmodifiableMap(new HashMap<>(progression)); }
    public void setRouteState(String routeId, String stateId) { routes.put(Objects.requireNonNull(routeId, "routeId"), Objects.requireNonNull(stateId, "stateId")); }
    public String routeState(String routeId) { return routes.get(routeId); }
    public Map<String, String> routeSnapshot() { return Collections.unmodifiableMap(new HashMap<>(routes)); }
    public boolean partner() { return partner; }
    public long partnerSinceMillis() { return partnerSinceMillis; }
    public void setPartner(boolean partner) { setPartner(partner, System.currentTimeMillis()); }
    public void setPartner(boolean partner, long nowMillis) {
        this.partner = partner;
        if (partner && partnerSinceMillis == 0) partnerSinceMillis = nowMillis;
        if (!partner) partnerSinceMillis = 0;
    }
    public String personalityId() { return personalityId; }
    public void setPersonalityId(String personalityId) { this.personalityId = personalityId; }
    public UUID householdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public long cooldownUntil(String id) { return cooldowns.getOrDefault(id, 0L); }
    public void setCooldownUntil(String id, long timestampMillis) { cooldowns.put(id, timestampMillis); }
    public Map<String, Long> cooldownSnapshot() { return Collections.unmodifiableMap(new HashMap<>(cooldowns)); }
    public String flag(String id) { return flags.get(id); }
    public void setFlag(String id, String value) { if (value == null) flags.remove(id); else flags.put(id, value); }
    public Map<String, String> flagSnapshot() { return Collections.unmodifiableMap(new HashMap<>(flags)); }
}
