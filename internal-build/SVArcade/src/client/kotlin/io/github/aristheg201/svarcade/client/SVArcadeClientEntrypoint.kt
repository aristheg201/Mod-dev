package io.github.aristheg201.svarcade.client

import net.fabricmc.api.ClientModInitializer

/** Public no-arg bridge for Fabric's default entrypoint language adapter. */
class SVArcadeClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        SVArcadeClient.onInitializeClient()
    }
}
