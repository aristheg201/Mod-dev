package vn.svframe.svrelationships.household;

import java.util.Objects;

public record HouseholdAnchor(String dimensionId, int x, int y, int z) {
    public HouseholdAnchor {
        Objects.requireNonNull(dimensionId, "dimensionId");
    }
}
