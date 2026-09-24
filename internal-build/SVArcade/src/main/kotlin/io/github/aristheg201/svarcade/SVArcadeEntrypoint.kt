package io.github.aristheg201.svarcade

import net.fabricmc.api.ModInitializer

/**
 * Public no-arg Fabric entrypoint bridge.
 *
 * Keep the implementation singleton in [SVArcade], but never expose a Kotlin
 * object directly to Fabric's default language adapter: Kotlin object
 * constructors are private and therefore cannot be reflectively instantiated.
 */
class SVArcadeEntrypoint : ModInitializer {
    override fun onInitialize() {
        SVArcade.onInitialize()
    }
}
