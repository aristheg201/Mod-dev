package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.WorldChronicleEngine;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Read-only player/admin access to the semantic world history. */
public final class ChronicleCommandsBootstrap implements ModInitializer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("lively")
                        .then(literal("chronicle")
                                .requires(source -> LivelyApi.permissions().has(source, "lively.chronicle.use", 0))
                                .executes(ctx -> latest(ctx.getSource(), 10))
                                .then(literal("latest")
                                        .executes(ctx -> latest(ctx.getSource(), 10))
                                        .then(argument("limit", IntegerArgumentType.integer(1, 50))
                                                .executes(ctx -> latest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "limit")))))
                                .then(literal("eras").executes(ctx -> eras(ctx.getSource())))
                                .then(literal("era").then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> era(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"))))))));
    }

    private static int latest(net.minecraft.server.command.ServerCommandSource source, int limit) {
        var entries = LivelyApi.chronicle().latest(limit);
        source.sendFeedback(() -> Text.literal("Lively Chronicle • " + entries.size() + " recent entries"), false);
        for (WorldChronicleEngine.ChronicleEntry entry : entries) {
            source.sendFeedback(() -> Text.literal("#" + entry.sequence() + " [" + DATE.format(entry.at()) + "] " + entry.title()
                    + " • significance=" + Math.round(entry.significance() * 100D) + "%"), false);
        }
        return entries.size();
    }

    private static int eras(net.minecraft.server.command.ServerCommandSource source) {
        var eras = LivelyApi.chronicle().eras();
        source.sendFeedback(() -> Text.literal("Lively eras: " + eras.size()), false);
        for (WorldChronicleEngine.Era era : eras) {
            source.sendFeedback(() -> Text.literal(era.index() + ". " + era.name() + " • entries=" + era.entries().size()
                    + " • from=" + DATE.format(era.startedAt())), false);
        }
        return eras.size();
    }

    private static int era(net.minecraft.server.command.ServerCommandSource source, int index) {
        WorldChronicleEngine.Era era = LivelyApi.chronicle().eras().stream().filter(value -> value.index() == index).findFirst().orElse(null);
        if (era == null) { source.sendError(Text.literal("Unknown era")); return 0; }
        source.sendFeedback(() -> Text.literal(era.name() + " • " + DATE.format(era.startedAt())
                + (era.endedAt() == null ? " • current" : " → " + DATE.format(era.endedAt()))
                + " • " + era.facts()), false);
        return 1;
    }
}
