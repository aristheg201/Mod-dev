package io.github.aristheg201.cobblemonworld.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

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
        removeLoadedEntity(player, NpcPlacementStore.INSTANCE.get(id));

        CWorldNpcEntity npc = new CWorldNpcEntity(ModEntities.NPC, player.serverLevel());
        npc.setNpcId(id);
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        if (!player.serverLevel().addFreshEntity(npc)) {
            player.sendSystemMessage(Component.literal("Failed to spawn NPC '" + id + "'.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String dimension = player.level().dimension().location().toString();
        NpcPlacement placement = new NpcPlacement(
                id,
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                npc.getUUID().toString()
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

        CWorldNpcEntity npc = getLoadedEntity(player, old);
        if (npc != null) {
            npc.setYRot(player.getYRot());
            npc.setYHeadRot(player.getYRot());
            npc.setXRot(player.getXRot());
        }

        NpcPlacement updated = new NpcPlacement(
                old.id(), old.dimension(), old.x(), old.y(), old.z(),
                player.getYRot(), player.getXRot(), old.entityUuid());
        NpcPlacementStore.INSTANCE.put(updated);
        player.sendSystemMessage(Component.literal("NPC '" + id + "' now faces your current direction.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        NpcPlacement old = NpcPlacementStore.INSTANCE.get(id);
        if (old == null) {
            source.sendFailure(Component.literal("Unknown NPC placement: " + id));
            return 0;
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            removeLoadedEntity(player, old);
        } else {
            removeLoadedEntity(source.getServer(), old);
        }

        NpcPlacementStore.INSTANCE.remove(id);
        source.sendSuccess(() -> Component.literal("Removed NPC '" + id + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var placements = NpcPlacementStore.INSTANCE.all();
        source.sendSuccess(() -> Component.literal("Cobblemon World NPCs: " + placements.size())
                .withStyle(ChatFormatting.AQUA), false);
        placements.values().stream().limit(50).forEach(p ->
                source.sendSuccess(() -> Component.literal("- " + p.id() + " @ " + p.dimension() +
                        " [" + round(p.x()) + ", " + round(p.y()) + ", " + round(p.z()) + "]"), false));
        return placements.size();
    }

    private static CWorldNpcEntity getLoadedEntity(ServerPlayer player, NpcPlacement placement) {
        if (placement == null || placement.entityUuid() == null || placement.entityUuid().isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(placement.entityUuid());
            for (ServerLevel level : player.getServer().getAllLevels()) {
                if (level.getEntity(uuid) instanceof CWorldNpcEntity npc) return npc;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static void removeLoadedEntity(ServerPlayer player, NpcPlacement placement) {
        CWorldNpcEntity npc = getLoadedEntity(player, placement);
        if (npc != null) npc.discard();
    }

    private static void removeLoadedEntity(net.minecraft.server.MinecraftServer server, NpcPlacement placement) {
        if (placement == null || placement.entityUuid() == null || placement.entityUuid().isBlank()) return;
        try {
            UUID uuid = UUID.fromString(placement.entityUuid());
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(uuid) instanceof CWorldNpcEntity npc) {
                    npc.discard();
                    return;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
