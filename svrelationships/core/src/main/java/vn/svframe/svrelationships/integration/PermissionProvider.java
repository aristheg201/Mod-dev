package vn.svframe.svrelationships.integration;

import java.util.Optional;
import java.util.UUID;

public interface PermissionProvider {
    String id();
    boolean available();
    boolean hasPermission(UUID playerId, String permission);
    Optional<String> primaryRank(UUID playerId);
}
