package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.quest.QuestRuntime;

import java.util.Comparator;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Lightweight server-side quest access. Content remains generated/emergent rather than authored through commands. */
public final class QuestCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lively").then(literal("quest")
                        .requires(source -> LivelyApi.permissions().has(source, "lively.quest.use", 0))
                        .executes(ctx -> list(ctx.getSource()))
                        .then(literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(literal("offers").executes(ctx -> offers(ctx.getSource())))
                        .then(literal("info").then(argument("id", StringArgumentType.word())
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(literal("claim").then(argument("id", StringArgumentType.word())
                                .executes(ctx -> claim(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(literal("abandon").then(argument("id", StringArgumentType.word())
                                .executes(ctx -> abandon(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))));
    }

    private static int list(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null) return 0;
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        var quests = LivelyApi.quests().byOwner(owner).stream()
                .filter(q -> q.status() == QuestRuntime.Status.ACTIVE || q.status() == QuestRuntime.Status.COMPLETED)
                .sorted(Comparator.comparing((QuestRuntime.Quest q) -> q.status().name()).thenComparing(QuestRuntime.Quest::createdAt).reversed())
                .limit(20).toList();
        source.sendFeedback(() -> Text.literal("Lively quests • " + quests.size()), false);
        for (QuestRuntime.Quest quest : quests) {
            long done = quest.objectives().stream().filter(objective -> quest.progress().getOrDefault(objective.id(), 0L) >= objective.required()).count();
            source.sendFeedback(() -> Text.literal(shortId(quest.id()) + " • " + quest.status() + " • " + quest.title()
                    + " • " + done + "/" + quest.objectives().size()), false);
        }
        return quests.size();
    }

    private static int offers(ServerCommandSource source) {
        if (player(source) == null) return 0;
        var offers = LivelyApi.quests().publicOffers().stream()
                .sorted(Comparator.comparing(QuestRuntime.Quest::createdAt).reversed()).limit(20).toList();
        source.sendFeedback(() -> Text.literal("Lively public offers • " + offers.size()), false);
        for (QuestRuntime.Quest quest : offers) {
            source.sendFeedback(() -> Text.literal(quest.id() + " • " + quest.title()
                    + (quest.issuer() == null ? "" : " • issuer=" + quest.issuer().uuid())), false);
        }
        return offers.size();
    }

    private static int info(ServerCommandSource source, String raw) {
        UUID id = uuid(source, raw);
        if (id == null) return 0;
        QuestRuntime.Quest quest = LivelyApi.quests().snapshot().quests().get(id);
        if (quest == null || !visibleTo(source, quest)) {
            source.sendError(Text.literal("Unknown quest"));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(quest.title() + " • " + quest.status() + " • id=" + quest.id()), false);
        for (QuestRuntime.Objective objective : quest.objectives()) {
            if (objective.hidden()) continue;
            long progress = quest.progress().getOrDefault(objective.id(), 0L);
            source.sendFeedback(() -> Text.literal("- " + objective.type() + " " + objective.id() + " • "
                    + Math.min(progress, objective.required()) + "/" + objective.required()
                    + (objective.optional() ? " • optional" : "")), false);
        }
        return 1;
    }

    private static int claim(ServerCommandSource source, String raw) {
        ServerPlayerEntity player = player(source);
        if (player == null) return 0;
        UUID id = uuid(source, raw);
        if (id == null) return 0;
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        QuestRuntime.Quest quest = LivelyApi.quests().claim(id, owner).orElse(null);
        if (quest == null) {
            source.sendError(Text.literal("Quest is unavailable, expired, or already claimed."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Đã nhận: " + quest.title()), false);
        return 1;
    }

    private static int abandon(ServerCommandSource source, String raw) {
        ServerPlayerEntity player = player(source);
        if (player == null) return 0;
        UUID id = uuid(source, raw);
        if (id == null) return 0;
        QuestRuntime.Quest quest = LivelyApi.quests().snapshot().quests().get(id);
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        if (quest == null || !owner.equals(quest.owner()) || quest.status() != QuestRuntime.Status.ACTIVE) {
            source.sendError(Text.literal("You do not own an active quest with that id."));
            return 0;
        }
        LivelyApi.quests().cancel(id);
        source.sendFeedback(() -> Text.literal("Đã bỏ nhiệm vụ: " + quest.title()), false);
        return 1;
    }

    private static boolean visibleTo(ServerCommandSource source, QuestRuntime.Quest quest) {
        if (quest.publicOffer()) return true;
        ServerPlayerEntity player = source.getPlayer();
        return player != null && quest.owner() != null && quest.owner().kind() == ActorId.Kind.PLAYER
                && quest.owner().uuid().equals(player.getUuid());
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) source.sendError(Text.literal("Player only."));
        return player;
    }

    private static UUID uuid(ServerCommandSource source, String raw) {
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException error) { source.sendError(Text.literal("Invalid quest UUID.")); return null; }
    }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
}
