package vn.svframe.svquest.server;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.network.ActionPayload;
import vn.svframe.svquest.network.StatePayload;

import java.util.Map;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.command.argument.EntityArgumentType.player;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ServerRuntime {
    private final QuestStateStore store = new QuestStateStore();
    private final QuestEngine engine = new QuestEngine(store, new RewardDispatcher());
    private volatile ReflectionIntegrationBridge integrations;
    private volatile ProductionProgressPoller productionPoller;
    private volatile SeasonProgressPoller seasonPoller;
    private volatile ResearchProgressMirror researchMirror;

    public void register() {
        engine.setSync(this::sendState);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload.action())));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            QuestEventBus.install(server, engine);

            ReflectionIntegrationBridge bridge = new ReflectionIntegrationBridge(server, engine);
            integrations = bridge;
            bridge.install();
            new CobblemonMilestoneBridge(server, engine).install();

            ProductionProgressPoller poller = new ProductionProgressPoller(server, engine);
            productionPoller = poller;
            SeasonProgressPoller seasonal = new SeasonProgressPoller(server, engine);
            seasonPoller = seasonal;
            researchMirror = new ResearchProgressMirror(server, engine);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                bridge.onJoin(player);
                poller.onJoin(player);
                seasonal.onJoin(player);
                engine.reconcile(player);
            }
            SVQuest.LOGGER.info("SVQuest full quest runtime loaded: {} quests.", vn.svframe.svquest.quest.QuestCatalog.QUESTS.size());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.tick();
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.tick();
            ResearchProgressMirror research = researchMirror;
            if (research != null) research.tick();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ReflectionIntegrationBridge bridge = integrations;
            if (bridge != null) bridge.onJoin(handler.player);
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.onJoin(handler.player);
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.onJoin(handler.player);
            engine.reconcile(handler.player);
            sendState(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ReflectionIntegrationBridge bridge = integrations;
            if (bridge != null) bridge.onQuit(handler.player);
            ProductionProgressPoller poller = productionPoller;
            if (poller != null) poller.onQuit(handler.player);
            SeasonProgressPoller seasonal = seasonPoller;
            if (seasonal != null) seasonal.onQuit(handler.player);
            store.unload(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            QuestEventBus.clear();
            store.saveAll();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("svquest")
                        .executes(ctx -> { sendState(ctx.getSource().getPlayerOrThrow()); return 1; })
                        .then(literal("sync").executes(ctx -> { sendState(ctx.getSource().getPlayerOrThrow()); return 1; }))
                        .then(literal("progress").requires(src -> src.hasPermissionLevel(2))
                                .then(argument("player", player())
                                        .then(argument("key", word())
                                                .then(argument("amount", integer())
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity target = getPlayer(ctx, "player");
                                                            engine.adminAdd(target, getString(ctx, "key"), getInteger(ctx, "amount"));
                                                            ctx.getSource().sendFeedback(() -> Text.literal("SVQuest progress updated for " + target.getName().getString()), false);
                                                            return 1;
                                                        })))))
                        .then(literal("set").requires(src -> src.hasPermissionLevel(2))
                                .then(argument("player", player())
                                        .then(argument("key", word())
                                                .then(argument("value", integer(0))
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity target = getPlayer(ctx, "player");
                                                            engine.adminSet(target, getString(ctx, "key"), getInteger(ctx, "value"));
                                                            return 1;
                                                        })))))
        ));
        SVQuest.LOGGER.info("SVQuest dedicated-server runtime registered.");
    }

    private void handle(ServerPlayerEntity player, String action) {
        if (action == null || action.length() > 128) return;
        if (action.equals("sync")) { sendState(player); return; }
        if (!action.startsWith("feature:")) return;

        String id = action.substring("feature:".length());
        if (FeatureOpeners.handle(player, id)) {
            engine.emit(player, "FEATURE_OPEN", 1, Map.of("target", id));
            return;
        }
        String command = FeatureCatalog.COMMANDS.get(id);
        if (command == null) {
            player.sendMessage(Text.literal("§cTính năng này chưa được cấu hình trên server."), false);
            return;
        }
        try {
            // Yarn 1.21.1 executeWithPrefix returns void. Reaching this line without an exception means
            // the server accepted and dispatched the whitelisted launcher command.
            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), command);
            engine.emit(player, "FEATURE_OPEN", 1, Map.of("target", id));
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Feature action '{}' failed safely for {}: {}", id, player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông thể mở tính năng này lúc này."), false);
        }
    }

    private void sendState(ServerPlayerEntity player) {
        try { ServerPlayNetworking.send(player, new StatePayload(store.get(player.getUuid()).encode())); }
        catch (Throwable t) { SVQuest.LOGGER.debug("Could not send SVQuest state to {}: {}", player.getName().getString(), t.toString()); }
    }
}
