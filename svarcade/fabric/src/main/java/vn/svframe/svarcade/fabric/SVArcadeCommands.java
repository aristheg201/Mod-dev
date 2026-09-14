package vn.svframe.svarcade.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.svarcade.runtime.*;

import static net.minecraft.server.command.CommandManager.literal;

final class SVArcadeCommands {
    private SVArcadeCommands() { }

    static void register(CommandDispatcher<ServerCommandSource> dispatcher, SVArcadeFabric mod) {
        dispatcher.register(literal("sva").requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload").executes(context -> reload(context.getSource(), mod)))
                .then(literal("debug")
                        .then(literal("session").executes(context -> sessions(context.getSource(), mod)))
                        .then(literal("arena").executes(context -> arenas(context.getSource(), mod)))
                        .then(literal("player").executes(context -> players(context.getSource(), mod)))
                        .then(literal("bot").executes(context -> bots(context.getSource(), mod)))));
    }

    private static int reload(ServerCommandSource source, SVArcadeFabric mod) {
        mod.reload().whenComplete((result, failure) -> source.getServer().execute(() -> {
            if (failure != null) source.sendFeedback(() -> Text.literal("SVArcade reload failed: " + concise(failure.toString())), false);
            else source.sendFeedback(() -> Text.literal("SVArcade reload: " + result.reason() + " (generation " + result.generation() + ")"), false);
        }));
        source.sendFeedback(() -> Text.literal("SVArcade reload submitted."), false); return 1;
    }
    private static int sessions(ServerCommandSource source, SVArcadeFabric mod) {
        GenericGameRuntime runtime = mod.runtime(); if (runtime == null) return unavailable(source);
        long running = runtime.sessions().values().stream().filter(s -> s.status() == GenericSession.Status.RUNNING).count();
        PlatformIntegrations platform = mod.platform(); String adapters = platform == null ? "unbound" : "active=" + platform.capabilities() + " disabled=" + platform.unavailable();
        source.sendFeedback(() -> Text.literal("SVArcade sessions=" + runtime.sessions().size() + " running=" + running + " failures=" + runtime.failures().size()
                + " tickNanos=" + runtime.lastTickNanos() + " integrations={" + concise(adapters) + "}"), false); return 1;
    }
    private static int arenas(ServerCommandSource source, SVArcadeFabric mod) {
        GenericGameRuntime runtime = mod.runtime(); if (runtime == null) return unavailable(source);
        source.sendFeedback(() -> Text.literal("SVArcade arenaLeases=" + runtime.arenas().snapshot().size()), false); return 1;
    }
    private static int players(ServerCommandSource source, SVArcadeFabric mod) {
        GenericGameRuntime runtime = mod.runtime(); if (runtime == null) return unavailable(source);
        long players = runtime.sessions().values().stream().flatMap(s -> s.participants().values().stream()).filter(p -> p.kind() == Participant.Kind.PLAYER).count();
        long spectators = runtime.sessions().values().stream().flatMap(s -> s.participants().values().stream()).filter(p -> p.kind() == Participant.Kind.SPECTATOR).count();
        source.sendFeedback(() -> Text.literal("SVArcade players=" + players + " spectators=" + spectators), false); return 1;
    }
    private static int bots(ServerCommandSource source, SVArcadeFabric mod) {
        if (mod.bots() == null) return unavailable(source); source.sendFeedback(() -> Text.literal("SVArcade botMetrics=" + mod.bots().metrics()), false); return 1;
    }
    private static int unavailable(ServerCommandSource source) { source.sendFeedback(() -> Text.literal("SVArcade runtime is not started."), false); return 0; }
    private static String concise(String value) { return value.length() <= 300 ? value : value.substring(0, 300); }
}
