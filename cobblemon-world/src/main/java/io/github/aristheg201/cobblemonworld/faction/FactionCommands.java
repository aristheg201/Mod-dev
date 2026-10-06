package io.github.aristheg201.cobblemonworld.faction;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class FactionCommands {
    private FactionCommands() {}

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("faction")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    var result = FactionBridge.create(player, StringArgumentType.getString(ctx, "name"));
                                    player.sendSystemMessage(Component.literal(result.message())
                                            .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
                                    return result.success() ? 1 : 0;
                                })))
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
}
