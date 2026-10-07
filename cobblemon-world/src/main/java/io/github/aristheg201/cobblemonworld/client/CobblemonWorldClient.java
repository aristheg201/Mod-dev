package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.boss.ModBossEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class CobblemonWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CWorldClientNetworking.register();

        EntityModelLayerRegistry.registerModelLayer(MysteriousFigureModel.LAYER, MysteriousFigureModel::createBodyLayer);
        EntityRendererRegistry.register(ModBossEntities.MYSTERIOUS_FIGURE, MysteriousFigureRenderer::new);

        if (Boolean.getBoolean("cworld.qa.client")) {
            io.github.aristheg201.cobblemonworld.qa.CWorldQaClientHarness.register();
        }
    }
}
