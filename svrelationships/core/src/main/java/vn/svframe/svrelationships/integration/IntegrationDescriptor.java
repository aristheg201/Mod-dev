package vn.svframe.svrelationships.integration;

import java.util.Objects;

public record IntegrationDescriptor(
        String id,
        String kind,
        String modId,
        IntegrationState state,
        String detailKey
) {
    public IntegrationDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detailKey, "detailKey");
    }
}
