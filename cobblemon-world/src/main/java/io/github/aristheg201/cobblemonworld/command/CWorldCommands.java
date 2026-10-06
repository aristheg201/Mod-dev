package io.github.aristheg201.cobblemonworld.command;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CWorldCommands {
    private CWorldCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("cworld")
                        .then(io.github.aristheg201.cobblemonworld.npc.NpcCommands.node())
                        .then(Commands.literal("cap")
                                .then(Commands.literal("get")
                                        .executes(ctx -> showCap(ctx.getSource().getPlayerOrException(), ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .requires(source -> source.hasPermission(2))
                                                .executes(ctx -> showCap(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))))
                                .then(Commands.literal("set")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, CWorldConfig.INSTANCE.maxLevelCap))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int level = IntegerArgumentType.getInteger(ctx, "level");
                                                            int applied = LevelCapService.setCap(target, level);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                                                    "command.cobblemonworld.cap.set",
                                                                    target.getDisplayName(), applied).withStyle(ChatFormatting.GREEN), true);
                                                            target.sendSystemMessage(Component.translatable(
                                                                    "message.cobblemonworld.cap.changed", applied).withStyle(ChatFormatting.GOLD));
                                                            return applied;
                                                        }))))
                                .then(Commands.literal("add")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, CWorldConfig.INSTANCE.maxLevelCap))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            int applied = LevelCapService.setCap(target, LevelCapService.getCap(target) + amount);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                                                    "command.cobblemonworld.cap.set",
                                                                    target.getDisplayName(), applied).withStyle(ChatFormatting.GREEN), true);
                                                            target.sendSystemMessage(Component.translatable(
                                                                    "message.cobblemonworld.cap.changed", applied).withStyle(ChatFormatting.GOLD));
                                                            return applied;
                                                        }))))
                                .then(Commands.literal("check")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            Pokemon blocked = LevelCapService.firstOverCapPartyPokemon(player);
                                            if (blocked == null) {
                                                ctx.getSource().sendSuccess(() -> Component.translatable(
                                                        "command.cobblemonworld.cap.party_ok",
                                                        LevelCapService.getCap(player)).withStyle(ChatFormatting.GREEN), false);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.translatable(
                                                    "command.cobblemonworld.cap.party_blocked",
                                                    blocked.getDisplayName(false), blocked.getLevel(), LevelCapService.getCap(player)));
                                            return 0;
                                        })))
        ));
    }

    private static int showCap(ServerPlayer viewer, ServerPlayer target) {
        int cap = LevelCapService.getCap(target);
        viewer.sendSystemMessage(Component.translatable(
                "command.cobblemonworld.cap.get",
                target.getDisplayName(), cap).withStyle(ChatFormatting.AQUA));
        return cap;
    }
}
