package io.github.aristheg201.cobblemonworld.notification;

import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class NotificationService {
    private NotificationService() {}

    public static void message(ServerPlayer player, String sender) {
        CWorldNetworking.toast(player, "message", "New Message", sender);
    }

    public static void contact(ServerPlayer player, String contact) {
        CWorldNetworking.toast(player, "contact", "New Contact", contact);
    }

    public static void objective(ServerPlayer player, String objective) {
        CWorldNetworking.toast(player, "objective", "Objective Updated", objective);
    }

    public static void story(ServerPlayer player, String story) {
        CWorldNetworking.toast(player, "story", "Story Updated", story);
    }

    public static void badge(ServerPlayer player, String badge) {
        CWorldNetworking.toast(player, "badge", "Badge Acquired", humanize(badge));
    }

    public static void levelCap(ServerPlayer player, int cap) {
        CWorldNetworking.toast(player, "level_cap", "Level Cap Increased", "Lv." + cap);
    }

    public static void faction(ServerPlayer player, String text) {
        CWorldNetworking.toast(player, "faction", "Faction Event", text);
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
