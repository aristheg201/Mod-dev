package vn.svframe.mythiclibfabric;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native server-tick replacement for MythicLib 1.7.1 CastingDelayHandler scheduling. */
public final class MythicLibCastingDelayManager {
    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();

    private MythicLibCastingDelayManager() { }

    public static boolean isCasting(UUID playerId) { return playerId != null && CASTS.containsKey(playerId); }

    public static boolean begin(UUID playerId, int delayTicks, ScriptContext context, Runnable completion) {
        if (playerId == null || context == null || completion == null || delayTicks <= 0) return false;
        long now = MythicLibFabricMod.currentTick();
        return CASTS.putIfAbsent(playerId, new Cast(playerId, now, now + delayTicks, context, completion)) == null;
    }

    public static boolean cancel(UUID playerId) {
        return playerId != null && CASTS.remove(playerId) != null;
    }

    public static void tick(MinecraftServer server, long currentTick) {
        for (Map.Entry<UUID, Cast> entry : CASTS.entrySet()) {
            Cast cast = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(cast.playerId());
            if (player == null || !player.isAlive()) {
                CASTS.remove(entry.getKey(), cast);
                continue;
            }
            if (currentTick < cast.finishTick()) continue;
            if (CASTS.remove(entry.getKey(), cast)) cast.completion().run();
        }
    }

    public static double progress(UUID playerId, long currentTick) {
        Cast cast = CASTS.get(playerId);
        if (cast == null) return 1.0d;
        long total = Math.max(1L, cast.finishTick() - cast.startTick());
        return Math.max(0.0d, Math.min(1.0d, (currentTick - cast.startTick()) / (double) total));
    }

    public static void clear() { CASTS.clear(); }

    private record Cast(UUID playerId, long startTick, long finishTick, ScriptContext context, Runnable completion) { }
}
