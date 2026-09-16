package io.github.aristheg201.svhub.client

import net.fabricmc.api.ClientModInitializer

/** Public no-arg bridge for Fabric's default entrypoint language adapter. */
class SVHubClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        SVHubClient.onInitializeClient()
    }
}
