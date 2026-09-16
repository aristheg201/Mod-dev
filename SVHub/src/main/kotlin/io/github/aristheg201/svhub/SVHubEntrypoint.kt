package io.github.aristheg201.svhub

import net.fabricmc.api.ModInitializer

/**
 * Public no-arg Fabric entrypoint bridge.
 *
 * Keep the implementation singleton in [SVHub], but never expose a Kotlin
 * object directly to Fabric's default language adapter: Kotlin object
 * constructors are private and therefore cannot be reflectively instantiated.
 */
class SVHubEntrypoint : ModInitializer {
    override fun onInitialize() {
        SVHub.onInitialize()
    }
}
