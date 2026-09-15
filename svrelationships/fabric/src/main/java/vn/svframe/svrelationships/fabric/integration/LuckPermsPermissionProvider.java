package vn.svframe.svrelationships.fabric.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import vn.svframe.svrelationships.integration.PermissionProvider;

import java.util.Optional;
import java.util.UUID;

public final class LuckPermsPermissionProvider implements PermissionProvider {
    private final LuckPerms api;

    public LuckPermsPermissionProvider() {
        this.api = LuckPermsProvider.get();
    }

    @Override
    public String id() {
        return "luckperms";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean hasPermission(UUID playerId, String permission) {
        var user = api.getUserManager().getUser(playerId);
        return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    @Override
    public Optional<String> primaryRank(UUID playerId) {
        var user = api.getUserManager().getUser(playerId);
        return user == null ? Optional.empty() : Optional.ofNullable(user.getPrimaryGroup());
    }
}
