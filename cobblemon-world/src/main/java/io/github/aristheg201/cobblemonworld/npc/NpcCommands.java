package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
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
                                .suggests((ctx, builder) -> {
                                    NpcDefinitionRegistry.INSTANCE.all().forEach(d -> builder.suggest(d.id()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> place(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("move")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    NpcDefinitionRegistry.INSTANCE.all().forEach(d -> builder.suggest(d.id()));
                                    return builder.buildFuture();
                                })
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
        NpcDefinitionRegistry.Definition definition = NpcDefinitionRegistry.INSTANCE.get(id);
        if (definition == null) {
            player.sendSystemMessage(Component.literal("Unknown NPC definition '" + id + "'.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (definition.specialActor()) {
            player.sendSystemMessage(Component.literal(
                    "'" + id + "' is a special actor and is spawned by its encounter, not /cworld npc place.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        removeLoadedEntity(player.getServer(), NpcPlacementStore.INSTANCE.get(id));

        NPCEntity npc = TrainerBattleService.createNpc(player, definition);
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        AnchoredNpcService.authoredPose(npc, player.getYRot());
        if (!player.serverLevel().addFreshEntity(npc)) {
            player.sendSystemMessage(Component.literal("Failed to spawn NPC '" + id + "'.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String dimension = player.level().dimension().location().toString();
        NpcPlacement placement = new NpcPlacement(
                id, dimension,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), 0.0F,
                npc.getUUID().toString()
        );
        NpcPlacementStore.INSTANCE.put(placement);

        player.sendSystemMessage(Component.literal("Placed '" + definition.displayName() + "' at " +
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
        NPCEntity npc = getLoadedEntity(player.getServer(), old);
        if (npc != null) {
            AnchoredNpcService.authoredPose(npc, player.getYRot());
        }
        NpcPlacement updated = new NpcPlacement(
                old.id(), old.dimension(), old.x(), old.y(), old.z(),
                player.getYRot(), 0.0F, old.entityUuid());
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
        removeLoadedEntity(source.getServer(), old);
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

    private static NPCEntity getLoadedEntity(net.minecraft.server.MinecraftServer server, NpcPlacement placement) {
        if (placement == null || placement.entityUuid() == null || placement.entityUuid().isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(placement.entityUuid());
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(uuid) instanceof NPCEntity npc) return npc;
            }
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private static void removeLoadedEntity(net.minecraft.server.MinecraftServer server, NpcPlacement placement) {
        NPCEntity npc = getLoadedEntity(server, placement);
        if (npc != null) npc.discard();
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
