package io.github.aristheg201.cobblemonworld.notification;

import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class NotificationService {
    private NotificationService() {}

    public static void message(ServerPlayer player, String sender) {
        CWorldNetworking.toast(player, "message", "toast.cobblemonworld.message", sender);
    }

    public static void contact(ServerPlayer player, String contact) {
        CWorldNetworking.toast(player, "contact", "toast.cobblemonworld.contact", contact);
    }

    public static void objective(ServerPlayer player, String objective) {
        CWorldNetworking.toast(player, "objective", "toast.cobblemonworld.objective", objective);
    }

    public static void story(ServerPlayer player, String story) {
        CWorldNetworking.toast(player, "story", "toast.cobblemonworld.story", story);
    }

    public static void badge(ServerPlayer player, String badge) {
        CWorldNetworking.toast(player, "badge", "toast.cobblemonworld.badge", "badge.cobblemonworld." + badge);
    }

    public static void levelCap(ServerPlayer player, int cap) {
        CWorldNetworking.toast(player, "level_cap", "toast.cobblemonworld.level_cap", "Lv." + cap);
    }

    public static void faction(ServerPlayer player, String text) {
        CWorldNetworking.toast(player, "faction", "toast.cobblemonworld.faction", text);
    }

    private static String humanize(String id) {
        if (id == null || id.isBlank()) return "";
        String[] words = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
