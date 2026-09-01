package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.lang.reflect.Method;
import java.math.BigDecimal;

/** Grants rewards defined by quest config. A non-empty command is the generic, no-code-path reward mechanism. */
public final class RewardDispatcher {
    public void grant(ServerPlayerEntity player, QuestCatalog.Quest quest) {
        boolean failed = false;
        for (QuestCatalog.Reward reward : quest.rewards()) {
            try {
                grantOne(player, reward);
            } catch (Throwable t) {
                failed = true;
                SVQuest.LOGGER.error("SVQuest reward failed safely for {} / {} / {}", player.getName().getString(), quest.id(), reward.type(), t);
            }
        }
        player.sendMessage(Text.literal("§a✓ Hoàn thành: §f" + quest.title()), false);
        if (failed) player.sendMessage(Text.literal("§eMột phần thưởng gặp lỗi và đã được ghi log. Báo admin để kiểm tra."), false);
    }

    private static void grantOne(ServerPlayerEntity player, QuestCatalog.Reward reward) throws ReflectiveOperationException {
        if (!reward.command().isBlank()) {
            command(player, expand(player, reward.command(), reward));
            return;
        }

        // Legacy convenience adapters remain for old data. New rewards can always use command directly.
        switch (reward.type()) {
            case "ITEM" -> {
                if (!reward.item().isBlank()) command(player, "give " + player.getName().getString() + " " + reward.item() + " " + Math.max(1, reward.count()));
            }
            case "COBBLEDOLLARS" -> cobbleDollars(player, reward.amount());
            case "BEASTCOIN" -> bEconomy(player, "beastcoin", reward.amount());
            case "HUNTERCOIN" -> bEconomy(player, "huntercoin", reward.amount());
            case "SKILL_POINT" -> skillPoints(player, (int) reward.amount());
            default -> SVQuest.LOGGER.debug("Ignoring reward without command/known legacy adapter: {}", reward.type());
        }
    }

    private static String expand(ServerPlayerEntity player, String template, QuestCatalog.Reward reward) {
        return template
                .replace("%player%", player.getName().getString())
                .replace("%uuid%", player.getUuidAsString())
                .replace("%amount%", Long.toString(reward.amount()))
                .replace("%count%", Integer.toString(Math.max(1, reward.count())))
                .replace("%item%", reward.item());
    }

    private static void command(ServerPlayerEntity player, String command) {
        MinecraftServer server = player.getServer();
        if (server == null || command == null || command.isBlank()) return;
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), command);
    }

    private static void cobbleDollars(ServerPlayerEntity player, long amount) {
        if (amount <= 0) return;
        if (FabricLoader.getInstance().isModLoaded("cobbledollars")) {
            command(player, "cobbledollars give " + player.getName().getString() + " " + amount);
            return;
        }
        try { bEconomy(player, "cobbledollars", amount); } catch (Throwable ignored) { }
    }

    private static void bEconomy(ServerPlayerEntity player, String currency, long amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("beconomy") || amount <= 0) return;
        Class<?> root = Class.forName("org.krripe.beconomy.api.BEconomy");
        Object instance = root.getField("INSTANCE").get(null);
        Object api = root.getMethod("getAPI").invoke(instance);
        if (api == null) return;
        Method addBalance = api.getClass().getMethod("addBalance", java.util.UUID.class, BigDecimal.class, String.class);
        addBalance.invoke(api, player.getUuid(), BigDecimal.valueOf(amount), currency);
    }

    private static void skillPoints(ServerPlayerEntity player, int amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("svframemmo") || amount <= 0) return;
        Class<?> root = Class.forName("vn.svframe.svframemmo.SVFrameMMO");
        Object manager = root.getMethod("playerData").invoke(null);
        Object data = manager.getClass().getMethod("get", ServerPlayerEntity.class).invoke(manager, player);
        if (data != null) data.getClass().getMethod("giveSkillPoints", int.class).invoke(data, amount);
    }
}
