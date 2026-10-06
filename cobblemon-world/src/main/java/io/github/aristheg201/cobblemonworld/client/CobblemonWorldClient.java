package io.github.aristheg201.cobblemonworld.client;

import net.fabricmc.api.ClientModInitializer;

public final class CobblemonWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CWorldClientNetworking.register();
    }
}
