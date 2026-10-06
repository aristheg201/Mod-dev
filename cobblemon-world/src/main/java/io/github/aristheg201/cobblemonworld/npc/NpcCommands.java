package io.github.aristheg201.cobblemonworld.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NpcCommands {
    private NpcCommands() {}

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("npc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("place")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> place(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("move")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> place(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("rotate")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> rotate(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())));
    }

    private static int place(ServerPlayer player, String id) {
        String dimension = player.level().dimension().location().toString();
        NpcPlacement placement = new NpcPlacement(
                id,
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
        NpcPlacementStore.INSTANCE.put(placement);
        player.sendSystemMessage(Component.literal("NPC '" + id + "' placed at " +
                dimension + " [" + round(player.getX()) + ", " + round(player.getY()) + ", " + round(player.getZ()) + "]")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int rotate(ServerPlayer player, String id) {
        NpcPlacement old = NpcPlacementStore.INSTANCE.get(id);
        if (old == null) {
            player.sendSystemMessage(Component.literal("Unknown NPC placement: " + id).withStyle(ChatFormatting.RED));
            return 0;
        }
        NpcPlacement updated = new NpcPlacement(
                old.id(), old.dimension(), old.x(), old.y(), old.z(), player.getYRot(), player.getXRot());
        NpcPlacementStore.INSTANCE.put(updated);
        player.sendSystemMessage(Component.literal("NPC '" + id + "' now faces your current direction.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        if (!NpcPlacementStore.INSTANCE.remove(id)) {
            source.sendFailure(Component.literal("Unknown NPC placement: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed NPC placement '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var placements = NpcPlacementStore.INSTANCE.all();
        source.sendSuccess(() -> Component.literal("Cobblemon World NPC placements: " + placements.size())
                .withStyle(ChatFormatting.AQUA), false);
        placements.values().stream().limit(50).forEach(p ->
                source.sendSuccess(() -> Component.literal("- " + p.id() + " @ " + p.dimension() +
                        " [" + round(p.x()) + ", " + round(p.y()) + ", " + round(p.z()) + "]"), false));
        return placements.size();
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
