package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.HubStore
import net.minecraft.server.MinecraftServer

object SVHubRuntime {
    lateinit var store: HubStore
    @Volatile var server: MinecraftServer? = null
}
