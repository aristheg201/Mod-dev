package vn.svframe.lively.integration;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.api.WaypointBridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional bridge to SVF Waypoints 1.2.17.
 *
 * <p>Lively deliberately binds reflectively so SVF Waypoints remains optional. The bridge uses the real transient
 * NavigationManager surface ({@code activate(ServerPlayerEntity, WaypointTarget)} / {@code stop(ServerPlayerEntity,
 * boolean)}) instead of registering persistent server waypoints or dispatching commands. If that typed surface changes,
 * the bridge fails closed and Lively keeps running without waypoint projection.</p>
 */
final class SvfWaypointsBridge implements WaypointBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("livelynpcs_integration");
    private static final String ROOT = "vn.svframe.waypoints.SvfWaypoints";
    private static final String NAVIGATION_MANAGER = "vn.svframe.waypoints.navigation.NavigationManager";
    private static final String WAYPOINT_TARGET = "vn.svframe.waypoints.data.WaypointTarget";

    private final Object manager;
    private final Constructor<?> targetConstructor;
    private final Method activate;
    private final Method stop;

    private SvfWaypointsBridge(Object manager, Constructor<?> targetConstructor, Method activate, Method stop) {
        this.manager = manager;
        this.targetConstructor = targetConstructor;
        this.activate = activate;
        this.stop = stop;
    }

    static WaypointBridge create() {
        try {
            Class<?> root = Class.forName(ROOT);
            Class<?> navigation = Class.forName(NAVIGATION_MANAGER);
            Class<?> target = Class.forName(WAYPOINT_TARGET);

            Method instanceMethod = root.getMethod("instance");
            Object instance = instanceMethod.invoke(null);
            if (instance == null) {
                LOGGER.warn("SVF Waypoints detected but SvfWaypoints.instance() returned null; Lively waypoint projection is disabled safely");
                return WaypointBridge.unavailable();
            }

            Method navigationMethod = root.getMethod("navigation");
            Object manager = navigationMethod.invoke(instance);
            if (manager == null || !navigation.isInstance(manager)) {
                LOGGER.warn("SVF Waypoints detected but NavigationManager was unavailable; Lively waypoint projection is disabled safely");
                return WaypointBridge.unavailable();
            }

            Constructor<?> targetConstructor = target.getConstructor(
                    String.class, String.class, String.class, String.class,
                    double.class, double.class, double.class,
                    UUID.class, UUID.class, boolean.class);
            Method activate = navigation.getMethod("activate", ServerPlayerEntity.class, target);
            Method stop = navigation.getMethod("stop", ServerPlayerEntity.class, boolean.class);

            LOGGER.info("Lively bound SVF Waypoints 1.2.17 transient navigation API");
            return new SvfWaypointsBridge(manager, targetConstructor, activate, stop);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("SVF Waypoints bridge unavailable: {}", error.toString());
            return WaypointBridge.unavailable();
        }
    }

    @Override
    public boolean available() {
        return manager != null && targetConstructor != null && activate != null && stop != null;
    }

    @Override
    public boolean show(ServerPlayerEntity player, String key, String world, Vec3d target, String label) {
        if (player == null || target == null || world == null || world.isBlank()) return false;
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) return false;

        try {
            String display = safeLabel(label);
            Object waypointTarget = targetConstructor.newInstance(
                    safeKey(key),
                    display,
                    display,
                    world,
                    target.x,
                    target.y,
                    target.z,
                    null,
                    null,
                    false);
            activate.invoke(manager, player, waypointTarget);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.debug("SVF Waypoints activate failed", error);
            return false;
        }
    }

    @Override
    public boolean clear(ServerPlayerEntity player, String key) {
        if (player == null) return false;
        try {
            // false avoids emitting a redundant 'navigation stopped' chat message when a Lively objective advances.
            stop.invoke(manager, player, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.debug("SVF Waypoints stop failed", error);
            return false;
        }
    }

    private static String safeKey(String key) {
        if (key == null || key.isBlank()) return "lively:quest";
        String clean = key.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= 128 ? clean : clean.substring(0, 128);
    }

    private static String safeLabel(String label) {
        if (label == null || label.isBlank()) return "Lively";
        String clean = label.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= 96 ? clean : clean.substring(0, 96);
    }
}
