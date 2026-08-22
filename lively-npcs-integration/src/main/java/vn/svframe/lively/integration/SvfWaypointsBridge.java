package vn.svframe.lively.integration;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.api.WaypointBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Optional SVF Waypoints bridge. It binds only to a small set of strongly typed navigation signatures and never
 * executes commands or writes SVF waypoint storage. This keeps Lively compatible with 1.2.x visual-only revisions
 * while failing closed if a future NavigationManager changes its public integration surface.
 */
final class SvfWaypointsBridge implements WaypointBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("livelynpcs_integration");
    private static final String NAVIGATION_MANAGER = "vn.svframe.waypoints.navigation.NavigationManager";
    private static final String ROOT = "vn.svframe.waypoints.SvfWaypoints";

    private final Object manager;
    private final Method show;
    private final Signature showSignature;
    private final Method clear;
    private final ClearSignature clearSignature;

    private SvfWaypointsBridge(Object manager, Method show, Signature showSignature,
                               Method clear, ClearSignature clearSignature) {
        this.manager = manager;
        this.show = show;
        this.showSignature = showSignature;
        this.clear = clear;
        this.clearSignature = clearSignature;
    }

    static WaypointBridge create() {
        try {
            Class<?> navigation = Class.forName(NAVIGATION_MANAGER);
            Object manager = resolveManager(navigation);
            BoundShow show = findShow(navigation, manager);
            BoundClear clear = findClear(navigation, manager);
            if (show == null || clear == null) {
                LOGGER.warn("SVF Waypoints detected but no compatible typed NavigationManager API was found; Lively waypoint projection is disabled safely");
                return WaypointBridge.unavailable();
            }
            show.method().trySetAccessible();
            clear.method().trySetAccessible();
            LOGGER.info("Lively bound SVF Waypoints API: show={}{} clear={}{}",
                    show.method().getName(), show.signature(), clear.method().getName(), clear.signature());
            return new SvfWaypointsBridge(manager, show.method(), show.signature(), clear.method(), clear.signature());
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("SVF Waypoints bridge unavailable: {}", error.toString());
            return WaypointBridge.unavailable();
        }
    }

    @Override public boolean available() { return show != null && clear != null; }

    @Override
    public boolean show(ServerPlayerEntity player, String key, String world, Vec3d target, String label) {
        if (player == null || target == null || world == null || world.isBlank()) return false;
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) return false;
        try {
            Object result = show.invoke(Modifier.isStatic(show.getModifiers()) ? null : manager,
                    showSignature.args(player, key, world, target, label));
            return success(show.getReturnType(), result);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.debug("SVF Waypoints show failed", error);
            return false;
        }
    }

    @Override
    public boolean clear(ServerPlayerEntity player, String key) {
        if (player == null) return false;
        try {
            Object result = clear.invoke(Modifier.isStatic(clear.getModifiers()) ? null : manager,
                    clearSignature.args(player, key));
            return success(clear.getReturnType(), result);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LOGGER.debug("SVF Waypoints clear failed", error);
            return false;
        }
    }

    private static Object resolveManager(Class<?> navigation) throws ReflectiveOperationException {
        Object direct = staticSingleton(navigation, navigation);
        if (direct != null) return direct;
        Class<?> root = Class.forName(ROOT);
        return staticSingleton(root, navigation);
    }

    private static Object staticSingleton(Class<?> owner, Class<?> wanted) throws ReflectiveOperationException {
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !wanted.isAssignableFrom(field.getType())) continue;
            field.trySetAccessible();
            Object value = field.get(null);
            if (value != null) return value;
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || !wanted.isAssignableFrom(method.getReturnType())) continue;
            method.trySetAccessible();
            Object value = method.invoke(null);
            if (value != null) return value;
        }
        return null;
    }

    private static BoundShow findShow(Class<?> navigation, Object manager) {
        return Arrays.stream(navigation.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()) || manager != null)
                .map(method -> new BoundShow(method, Signature.match(method)))
                .filter(bound -> bound.signature() != null)
                .sorted(Comparator.comparingInt((BoundShow bound) -> methodScore(bound.method().getName())).reversed()
                        .thenComparing(bound -> bound.method().toGenericString()))
                .findFirst().orElse(null);
    }

    private static BoundClear findClear(Class<?> navigation, Object manager) {
        return Arrays.stream(navigation.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()) || manager != null)
                .map(method -> new BoundClear(method, ClearSignature.match(method)))
                .filter(bound -> bound.signature() != null)
                .sorted(Comparator.comparingInt((BoundClear bound) -> clearScore(bound.method().getName())).reversed()
                        .thenComparing(bound -> bound.method().toGenericString()))
                .findFirst().orElse(null);
    }

    private static int methodScore(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.contains("dynamic")) score += 50;
        if (name.contains("navigate")) score += 45;
        if (name.contains("target")) score += 35;
        if (name.contains("activate")) score += 30;
        if (name.contains("start")) score += 25;
        if (name.contains("show")) score += 20;
        if (name.contains("set")) score += 8;
        return score;
    }

    private static int clearScore(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.contains("deactivate")) score += 50;
        if (name.contains("stop")) score += 45;
        if (name.contains("clear")) score += 40;
        if (name.contains("remove")) score += 20;
        if (name.contains("hide")) score += 15;
        return score;
    }

    private static boolean success(Class<?> returnType, Object result) {
        if (returnType == void.class) return true;
        if (returnType == boolean.class || returnType == Boolean.class) return Boolean.TRUE.equals(result);
        return result != null;
    }

    private enum Signature {
        PLAYER_WORLD_VEC_LABEL(new Class<?>[]{ServerPlayerEntity.class, String.class, Vec3d.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, world, v, safeLabel(label)}; }
        },
        PLAYER_VEC_LABEL(new Class<?>[]{ServerPlayerEntity.class, Vec3d.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, v, safeLabel(label)}; }
        },
        PLAYER_WORLD_VEC(new Class<?>[]{ServerPlayerEntity.class, String.class, Vec3d.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, world, v}; }
        },
        PLAYER_VEC(new Class<?>[]{ServerPlayerEntity.class, Vec3d.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, v}; }
        },
        PLAYER_WORLD_XYZ_LABEL(new Class<?>[]{ServerPlayerEntity.class, String.class, double.class, double.class, double.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, world, v.x, v.y, v.z, safeLabel(label)}; }
        },
        PLAYER_XYZ_LABEL(new Class<?>[]{ServerPlayerEntity.class, double.class, double.class, double.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p, v.x, v.y, v.z, safeLabel(label)}; }
        },
        UUID_WORLD_VEC_LABEL(new Class<?>[]{UUID.class, String.class, Vec3d.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p.getUuid(), world, v, safeLabel(label)}; }
        },
        UUID_VEC_LABEL(new Class<?>[]{UUID.class, Vec3d.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity p, String key, String world, Vec3d v, String label) { return new Object[]{p.getUuid(), v, safeLabel(label)}; }
        };

        private final Class<?>[] parameters;
        Signature(Class<?>[] parameters) { this.parameters = parameters; }
        abstract Object[] args(ServerPlayerEntity player, String key, String world, Vec3d target, String label);

        static Signature match(Method method) {
            if (methodScore(method.getName()) <= 0) return null;
            return Arrays.stream(values()).filter(signature -> same(method.getParameterTypes(), signature.parameters)).findFirst().orElse(null);
        }
    }

    private enum ClearSignature {
        PLAYER(new Class<?>[]{ServerPlayerEntity.class}) {
            @Override Object[] args(ServerPlayerEntity player, String key) { return new Object[]{player}; }
        },
        PLAYER_KEY(new Class<?>[]{ServerPlayerEntity.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity player, String key) { return new Object[]{player, key == null ? "" : key}; }
        },
        UUID_ONLY(new Class<?>[]{UUID.class}) {
            @Override Object[] args(ServerPlayerEntity player, String key) { return new Object[]{player.getUuid()}; }
        },
        UUID_KEY(new Class<?>[]{UUID.class, String.class}) {
            @Override Object[] args(ServerPlayerEntity player, String key) { return new Object[]{player.getUuid(), key == null ? "" : key}; }
        };

        private final Class<?>[] parameters;
        ClearSignature(Class<?>[] parameters) { this.parameters = parameters; }
        abstract Object[] args(ServerPlayerEntity player, String key);

        static ClearSignature match(Method method) {
            if (clearScore(method.getName()) <= 0) return null;
            return Arrays.stream(values()).filter(signature -> same(method.getParameterTypes(), signature.parameters)).findFirst().orElse(null);
        }
    }

    private static boolean same(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i].isPrimitive() || expected[i].isPrimitive()) {
                if (actual[i] != expected[i]) return false;
            } else if (!actual[i].isAssignableFrom(expected[i]) && !expected[i].isAssignableFrom(actual[i])) {
                return false;
            }
        }
        return true;
    }

    private static String safeLabel(String label) {
        if (label == null || label.isBlank()) return "Lively";
        String clean = label.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= 96 ? clean : clean.substring(0, 96);
    }

    private record BoundShow(Method method, Signature signature) {}
    private record BoundClear(Method method, ClearSignature signature) {}
}
