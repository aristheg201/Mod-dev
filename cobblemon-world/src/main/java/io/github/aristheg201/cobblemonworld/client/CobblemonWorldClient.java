package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.npc.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class CobblemonWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CWorldClientNetworking.register();
        EntityRendererRegistry.register(ModEntities.NPC, CWorldNpcRenderer::new);
    }
}
