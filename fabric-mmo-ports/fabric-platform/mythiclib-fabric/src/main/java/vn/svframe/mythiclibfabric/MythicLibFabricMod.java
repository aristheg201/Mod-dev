package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class MythicLibFabricMod implements ModInitializer {
    public static final String ID = "mythiclibfabric";

    private static volatile MinecraftServer server;

    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(instance -> {
            server = instance;
            System.out.println("[MythicLib-Fabric] runtime online");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(instance -> server = null);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mythiclibfabric")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("status").executes(ctx -> {
                            MinecraftServer active = server;
                            String state = active == null ? "starting/stopped" : "online";
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "MythicLib Fabric runtime=" + state + " | " + FabricDamageBridge.summary()), false);
                            return 1;
                        }))));
    }
}
