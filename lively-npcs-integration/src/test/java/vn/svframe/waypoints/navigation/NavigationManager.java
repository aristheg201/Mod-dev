package vn.svframe.waypoints.navigation;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Test double for the typed dynamic-target shape accepted by Lively's optional bridge. */
public final class NavigationManager {
    public boolean navigateDynamic(ServerPlayerEntity player, String world, Vec3d target, String label) { return true; }
    public boolean deactivate(ServerPlayerEntity player) { return true; }
}
