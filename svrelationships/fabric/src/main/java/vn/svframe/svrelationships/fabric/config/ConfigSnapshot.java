package vn.svframe.svrelationships.fabric.config;

import java.util.List;
import java.util.Map;

public record ConfigSnapshot(
        long generation,
        String locale,
        String adminPermission,
        String defaultHouseholdProfile,
        Map<String, HouseholdProfile> householdProfiles,
        List<String> economyPriority,
        Map<String, String> messages
) {
    public ConfigSnapshot {
        householdProfiles = Map.copyOf(householdProfiles);
        economyPriority = List.copyOf(economyPriority);
        messages = Map.copyOf(messages);
    }

    public HouseholdProfile defaultHousehold() {
        return householdProfiles.get(defaultHouseholdProfile);
    }

    public record HouseholdProfile(
            int activeRadius,
            int deactivationRadius,
            int maxMaterializedPartners,
            String boundary
    ) {
    }
}
