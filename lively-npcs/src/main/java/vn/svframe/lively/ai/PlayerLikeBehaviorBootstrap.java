package vn.svframe.lively.ai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import vn.svframe.lively.api.LivelyApi;

/** Session-aware wiring for physical ambient behaviour. */
public final class PlayerLikeBehaviorBootstrap implements ModInitializer {
    private volatile MinecraftServer boundServer;
    private volatile PlayerLikeBehaviorService service;

    @Override
    public void onInitialize() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            PlayerLikeBehaviorService current = service;
            if (!world.isClient && current != null && player instanceof ServerPlayerEntity serverPlayer) {
                current.onPlayerAttack(serverPlayer, entity.getUuid());
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PlayerLikeBehaviorService current = ensureService(server);
            if (current != null) current.tick(server, server.getTicks());
        });
    }

    private PlayerLikeBehaviorService ensureService(MinecraftServer server) {
        if (boundServer == server && service != null) return service;
        if (LivelyApi.npcs() == null || LivelyApi.states() == null || LivelyApi.worldNavigation() == null) return null;
        boundServer = server;
        service = new PlayerLikeBehaviorService(LivelyApi.npcs(), LivelyApi.states(), LivelyApi.worldNavigation());
        return service;
    }
}
