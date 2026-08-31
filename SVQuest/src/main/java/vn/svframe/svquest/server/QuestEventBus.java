package vn.svframe.svquest.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.util.Map;
import java.util.UUID;

/** Static bridge for optional runtime hooks/mixins. All mutation still lands on the server-authoritative engine. */
public final class QuestEventBus {
    private static volatile MinecraftServer server;
    private static volatile QuestEngine engine;
    private QuestEventBus() {}

    public static void install(MinecraftServer minecraftServer, QuestEngine questEngine) {
        server = minecraftServer;
        engine = questEngine;
    }

    public static void clear() { server = null; engine = null; }

    public static void emit(UUID playerId, String type) { emit(playerId, type, 1, Map.of()); }
    public static void emit(UUID playerId, String type, long amount, Map<String, String> meta) {
        MinecraftServer s = server;
        QuestEngine e = engine;
        if (s == null || e == null || playerId == null) return;
        Runnable action = () -> {
            try {
                ServerPlayerEntity player = s.getPlayerManager().getPlayer(playerId);
                if (player != null) e.emit(player, type, amount, meta == null ? Map.of() : meta);
            } catch (Throwable t) {
                SVQuest.LOGGER.debug("Optional quest hook {} failed safely: {}", type, t.toString());
            }
        };
        if (s.isOnThread()) action.run(); else s.execute(action);
    }

    public static void emit(ServerPlayerEntity player, String type) {
        if (player != null) emit(player.getUuid(), type, 1, Map.of());
    }
}
