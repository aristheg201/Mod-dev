package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.content.HubStore
import net.minecraft.server.MinecraftServer

object SVArcadeRuntime {
    lateinit var store: HubStore
    @Volatile var server: MinecraftServer? = null
}
