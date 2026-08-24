package vn.svframe.mmoitemsfabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.UUID;

/** Shared optional LuckPerms bridge with vanilla operator fallback. */
public final class MMOItemsPermissionBridge {
    private MMOItemsPermissionBridge() {}

    public static boolean has(ServerPlayerEntity player, String permission) {
        if (permission == null || permission.isBlank()) return true;
        if (player == null) return false;
        if (FabricLoader.getInstance().isModLoaded("luckperms")) {
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object api = provider.getMethod("get").invoke(null);
                Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
                Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUuid());
                if (user != null) {
                    Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                    Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
                    Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
                    Method asBoolean = result.getClass().getMethod("asBoolean");
                    return Boolean.TRUE.equals(asBoolean.invoke(result));
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return player.hasPermissionLevel(2);
    }
}
