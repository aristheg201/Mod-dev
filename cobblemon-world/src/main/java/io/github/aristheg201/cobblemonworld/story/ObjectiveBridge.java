package io.github.aristheg201.cobblemonworld.story;

import net.minecraft.server.level.ServerPlayer;

/**
 * Stable integration surface for Card World, chess, structures and future side modes.
 * External modes do not need to know how quest persistence is stored.
 */
public final class ObjectiveBridge {
    private ObjectiveBridge() {}

    public static int record(ServerPlayer player, String type, String target) {
        return record(player, type, target, 1);
    }

    public static int record(ServerPlayer player, String type, String target, int amount) {
        if (player == null || type == null || target == null) return 0;
        return CampaignService.recordObjective(player, normalize(type), normalize(target), amount);
    }

    public static int cobblemonBattle(ServerPlayer player, String encounterId) {
        return record(player, "cobblemon_battle", encounterId);
    }

    public static int cardWorldVisit(ServerPlayer player, String encounterId) {
        return record(player, "card_world_visit", encounterId);
    }

    public static int cardWorldWin(ServerPlayer player, String encounterId) {
        return record(player, "card_world_battle", encounterId);
    }

    public static int chessWin(ServerPlayer player, String encounterId) {
        return record(player, "chess", encounterId);
    }

    public static int visit(ServerPlayer player, String locationId) {
        return record(player, "visit", locationId);
    }

    public static int interact(ServerPlayer player, String objectId) {
        return record(player, "interact", objectId);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
