package vn.svframe.svquest;

import vn.svframe.svquest.server.QuestEventBus;

import java.util.Map;
import java.util.UUID;

/** Compatibility facade used by the proven beta.5 server-only integration mixins. */
public final class QuestApi {
    private QuestApi() {}

    public static void emit(UUID playerId, String type) {
        QuestEventBus.emit(playerId, type);
    }

    public static void emit(UUID playerId, String type, long amount) {
        QuestEventBus.emit(playerId, type, amount, Map.of());
    }

    public static void emit(UUID playerId, String type, long amount, Map<String, String> meta) {
        QuestEventBus.emit(playerId, type, amount, meta == null ? Map.of() : meta);
    }
}
