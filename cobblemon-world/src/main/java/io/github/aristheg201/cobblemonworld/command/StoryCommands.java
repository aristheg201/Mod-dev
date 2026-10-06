package io.github.aristheg201.cobblemonworld.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;
import io.github.aristheg201.cobblemonworld.league.LeagueService;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.story.CampaignService;
import io.github.aristheg201.cobblemonworld.story.ObjectiveBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class StoryCommands {
    private StoryCommands() {}

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("story")
                .then(Commands.literal("rpg")
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var rpg = SvFrameRpgBridge.snapshot(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            rpg.available()
                                                    ? "SVFrame: " + rpg.classId() + " Lv." + rpg.level()
                                                        + " | STR " + rpg.strength()
                                                        + " DEX " + rpg.dexterity()
                                                        + " INT " + rpg.intelligence()
                                                        + " | STA " + (int)Math.round(rpg.stamina()) + "/" + (int)Math.round(rpg.maxStamina())
                                                        + " | MANA " + (int)Math.round(rpg.mana()) + "/" + (int)Math.round(rpg.maxMana())
                                                        + " | CDR " + String.format(java.util.Locale.ROOT, "%.1f%%", rpg.cooldownReduction())
                                                        + " | SVFrameLib damage=" + rpg.libAvailable()
                                                    : "SVFrameMMO not available; TOBA uses fallback resources/damage."
                                    ), false);
                                    return rpg.available() ? 1 : 0;
                                })))
                .then(Commands.literal("final")
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    var cfg = CWorldConfig.INSTANCE;
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Final encounter: " + (cfg.finalEncounterEnabled ? "ENABLED" : "DISABLED")
                                                    + " @ " + cfg.finalEncounterDimension
                                                    + " [" + String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f",
                                                    cfg.finalEncounterX, cfg.finalEncounterY, cfg.finalEncounterZ) + "]"
                                    ), false);
                                    return cfg.finalEncounterEnabled ? 1 : 0;
                                }))
                        .then(Commands.literal("sethere")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var cfg = CWorldConfig.INSTANCE;
                                    cfg.finalEncounterDimension = player.level().dimension().location().toString();
                                    cfg.finalEncounterX = player.getX();
                                    cfg.finalEncounterY = player.getY();
                                    cfg.finalEncounterZ = player.getZ();
                                    cfg.finalEncounterYaw = player.getYRot();
                                    cfg.finalEncounterEnabled = true;
                                    CWorldConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Final encounter meeting point saved and enabled."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("enable")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    CWorldConfig.INSTANCE.finalEncounterEnabled = true;
                                    CWorldConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Final encounter enabled."), true);
                                    return 1;
                                }))
                        .then(Commands.literal("disable")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    CWorldConfig.INSTANCE.finalEncounterEnabled = false;
                                    CWorldConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Final encounter disabled."), true);
                                    return 1;
                                })))
                .then(Commands.literal("phone")
                        .executes(ctx -> {
                            CWorldNetworking.openPhone(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("objective")
                        .then(Commands.literal("record")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("target", StringArgumentType.word())
                                                        .executes(ctx -> record(ctx, 1))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> record(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))))))
                .then(Commands.literal("flag")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            String flag = StringArgumentType.getString(ctx, "flag");
                                            boolean changed = CampaignService.setFlag(player, flag);
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    changed ? "Set story flag " + flag : "Story flag already set: " + flag), true);
                                            return changed ? 1 : 0;
                                        }))))
                .then(Commands.literal("league")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("points", IntegerArgumentType.integer(-100000, 100000))
                                        .executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                            int points = IntegerArgumentType.getInteger(ctx, "points");
                                            LeagueService.addPoints(player, points);
                                            return 1;
                                        }))));
    }

    private static int record(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String type = StringArgumentType.getString(ctx, "type");
        String target = StringArgumentType.getString(ctx, "target");
        int changed = ObjectiveBridge.record(player, type, target, amount);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Recorded " + changed + " quest progress for " + player.getGameProfile().getName()), true);
        return changed;
    }
}
