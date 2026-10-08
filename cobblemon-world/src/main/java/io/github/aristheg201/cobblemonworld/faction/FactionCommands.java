package io.github.aristheg201.cobblemonworld.faction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class FactionCommands {
    private FactionCommands() {}

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("faction")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.create(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.invite(ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.accept(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("leave")
                        .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                NativeFactionService.leave(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.kick(ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("promote")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.setOfficer(ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"), true)))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.setOfficer(ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"), false)))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                        NativeFactionService.transfer(ctx.getSource().getPlayerOrException(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("disband")
                        .executes(ctx -> send(ctx.getSource().getPlayerOrException(),
                                NativeFactionService.disband(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.sendSystemMessage(Component.literal(IslandWarService.statusFor(player)));
                            return 1;
                        }))
                .then(Commands.literal("island")
                        .then(Commands.literal("join")
                                .executes(ctx -> IslandWarService.join(ctx.getSource().getPlayerOrException()) ? 1 : 0)));
    }

    private static int send(ServerPlayer player, NativeFactionService.Result result) {
        player.sendSystemMessage(result.component()
                .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return result.success() ? 1 : 0;
    }
}
