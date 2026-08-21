package vn.svframe.lively.integration;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vn.svframe.lively.api.ClaimBridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Optional Flan bridge verified against Flan Fabric 1.21.1-1.12.5.
 *
 * Lively deliberately fails closed when a claim-aware interaction cannot be
 * evaluated. AI never receives block break/place permissions from this bridge;
 * those actions remain forbidden by the core WorldMutationPolicy regardless of
 * what Flan would allow.
 */
final class FlanClaimBridge implements ClaimBridge {
    private final Method canInteract;

    private FlanClaimBridge(Method canInteract) {
        this.canInteract = canInteract;
    }

    static ClaimBridge create() {
        try {
            Class<?> handler = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
            Method method = handler.getMethod("canInteract", ServerPlayerEntity.class, BlockPos.class, Identifier.class);
            return new FlanClaimBridge(method);
        } catch (ReflectiveOperationException error) {
            return unavailable();
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean canNpcInteract(ServerPlayerEntity responsiblePlayer, String world, BlockPos pos, String action) {
        if (responsiblePlayer == null || pos == null) return false;
        String playerWorld = responsiblePlayer.getServerWorld().getRegistryKey().getValue().toString();
        if (world != null && !world.isBlank() && !world.equals(playerWorld)) return false;
        Identifier permission = permission(action);
        try {
            return Boolean.TRUE.equals(canInteract.invoke(null, responsiblePlayer, pos, permission));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException error) {
            return false;
        }
    }

    static Identifier permission(String rawAction) {
        String action = rawAction == null ? "" : rawAction.trim().toLowerCase(Locale.ROOT);
        String path = switch (action) {
            case "open_container", "storage" -> "open_container";
            case "door", "entrance", "openable" -> "door";
            case "sleep", "bed" -> "bed";
            case "trade", "trading" -> "trading";
            case "teleport" -> "teleport";
            case "projectile", "projectiles" -> "projectiles";
            case "animal_interact", "pokemon_interact" -> "animal_interact";
            case "break" -> "break";
            case "place" -> "place";
            default -> "interact_block";
        };
        return Identifier.of("flan", path);
    }

    private static ClaimBridge unavailable() {
        return new ClaimBridge() {
            @Override public boolean available() { return false; }
            @Override public boolean canNpcInteract(ServerPlayerEntity responsiblePlayer, String world, BlockPos pos, String action) { return false; }
        };
    }
}
