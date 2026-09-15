package vn.svframe.svrelationships.household;

import java.util.Objects;
import java.util.UUID;

public record HouseholdState(
        UUID householdId,
        UUID ownerId,
        HouseholdAnchor anchor,
        String profileId
) {
    public HouseholdState {
        Objects.requireNonNull(householdId, "householdId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(profileId, "profileId");
    }
}
