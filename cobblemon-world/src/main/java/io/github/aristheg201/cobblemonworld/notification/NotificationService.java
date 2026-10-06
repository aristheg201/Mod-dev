package io.github.aristheg201.cobblemonworld.notification;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NotificationService {
    private NotificationService() {}

    public static void message(ServerPlayer player, String sender) {
        player.sendSystemMessage(Component.translatable("toast.cobblemonworld.message", sender), true);
    }

    public static void contact(ServerPlayer player, String contact) {
        player.sendSystemMessage(Component.translatable("toast.cobblemonworld.contact", contact), true);
    }

    public static void objective(ServerPlayer player, String objective) {
        player.sendSystemMessage(Component.translatable("toast.cobblemonworld.objective", objective), true);
    }

    public static void levelCap(ServerPlayer player, int cap) {
        player.sendSystemMessage(Component.translatable("toast.cobblemonworld.level_cap", cap), true);
    }

    public static void faction(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.translatable("toast.cobblemonworld.faction", text), true);
    }
}
