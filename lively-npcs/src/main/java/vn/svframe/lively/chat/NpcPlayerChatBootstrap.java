package vn.svframe.lively.chat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import vn.svframe.lively.api.LivelyApi;

/** Session-aware hook from normal player chat into nearby Lively NPC replies. */
public final class NpcPlayerChatBootstrap implements ModInitializer {
    private static volatile NpcPlayerChatService active;
    private static volatile MinecraftServer activeServer;

    @Override
    public void onInitialize() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            NpcPlayerChatService service = ensure(sender.getServer());
            if (service != null) service.onPlayerChat(sender, message.getContent().getString());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NpcPlayerChatService service = ensure(server);
            if (service != null) service.tick(server, server.getTicks());
        });
    }

    public static boolean say(MinecraftServer server, java.util.UUID npcId, String text) {
        NpcPlayerChatService service = ensure(server);
        return service != null && service.sayNow(server, npcId, text);
    }

    private static NpcPlayerChatService ensure(MinecraftServer server) {
        if (server == null || LivelyApi.npcs() == null || LivelyApi.states() == null) return null;
        if (activeServer == server && active != null) return active;
        activeServer = server;
        active = new NpcPlayerChatService(LivelyApi.npcs(), LivelyApi.states());
        return active;
    }
}
