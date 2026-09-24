package io.github.aristheg201.svhub.client.nativeui

import net.fabricmc.api.ClientModInitializer

class NativePlatformClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        NativePlatformClient.register()
        ArcadeAcceptanceHarness.register()
        VisualSmokeHarness.register()
    }
}
