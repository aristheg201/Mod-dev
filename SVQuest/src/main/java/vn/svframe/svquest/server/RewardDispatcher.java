package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.lang.reflect.Method;
import java.math.BigDecimal;

/** Grants fixed server-owned rewards. No reward data is accepted from the client. */
public final class RewardDispatcher {
    public void grant(ServerPlayerEntity player, QuestCatalog.Quest quest) {
        try {
            switch (quest.id()) {
                case "first_partner" -> {
                    command(player, "give " + player.getName().getString() + " cobblemon:poke_ball 8");
                    cobbleDollars(player, 25_000);
                    bEconomy(player, "beastcoin", 20);
                }
                case "build_team" -> {
                    command(player, "give " + player.getName().getString() + " cobblemon:exp_candy_s 6");
                    command(player, "give " + player.getName().getString() + " cobblemon:great_ball 4");
                }
                case "training" -> bEconomy(player, "beastcoin", 40);
                case "trainer_power" -> {
                    bEconomy(player, "beastcoin", 100);
                    skillPoints(player, 1);
                }
                case "first_skill" -> {
                    skillPoints(player, 1);
                    command(player, "give " + player.getName().getString() + " cobblemon:exp_candy_m 8");
                }
                case "economy" -> {
                    cobbleDollars(player, 50_000);
                    bEconomy(player, "huntercoin", 20);
                }
                case "breeding" -> bEconomy(player, "beastcoin", 60);
                case "tera_mega" -> bEconomy(player, "beastcoin", 80);
                case "hunts_raids" -> bEconomy(player, "beastcoin", 100);
                case "competitive" -> bEconomy(player, "huntercoin", 30);
                case "research_collection" -> bEconomy(player, "huntercoin", 40);
                case "server_activities" -> bEconomy(player, "huntercoin", 50);
                case "seasonal" -> bEconomy(player, "beastcoin", 100);
                default -> { }
            }
            player.sendMessage(Text.literal("§a✓ Hoàn thành: §f" + quest.title()), false);
        } catch (Throwable t) {
            // Reward failure must never crash the server. The quest remains marked rewarded to prevent dupes;
            // admins can regrant manually after checking logs.
            SVQuest.LOGGER.error("Reward dispatch failed safely for {} / {}", player.getName().getString(), quest.id(), t);
            player.sendMessage(Text.literal("§eSVQuest đã ghi nhận hoàn thành, nhưng một phần thưởng gặp lỗi. Hãy báo admin."), false);
        }
    }

    private static void command(ServerPlayerEntity player, String command) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), command);
    }

    private static void cobbleDollars(ServerPlayerEntity player, int amount) {
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) return;
        command(player, "cobbledollars give " + player.getName().getString() + " " + Math.max(0, amount));
    }

    private static void bEconomy(ServerPlayerEntity player, String currency, int amount) throws ReflectiveOperationException {
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
