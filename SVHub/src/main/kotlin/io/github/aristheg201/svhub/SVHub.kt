package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.api.HubIntegrationDescriptor
import io.github.aristheg201.svhub.api.SVHubApi
import io.github.aristheg201.svhub.command.SVHubCommands
import io.github.aristheg201.svhub.content.HubStore
import io.github.aristheg201.svhub.network.SVHubNetwork
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object SVHub : ModInitializer {
    const val MOD_ID = "svhub"
    val LOGGER = LoggerFactory.getLogger("SVHub")

    override fun onInitialize() {
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:core", "SVHub Core", setOf("content", "editor", "search", "themes", "assets")))
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:cobblemon", "Cobblemon Wiki", setOf("species", "model", "fakemon"), setOf("cobblemon")))
        SVHubRuntime.store = HubStore(FabricLoader.getInstance().configDir.resolve(MOD_ID))
        SVHubNetwork.registerCommon()
        SVHubCommands.register()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            SVHubRuntime.server = server
            SVHubRuntime.store.initializeAsync().whenComplete { snapshot, error ->
                server.execute {
                    if (error != null) {
                        LOGGER.error("Unable to load SVHub content; keeping safe defaults", error)
                    } else {
                        LOGGER.info("SVHub loaded revision {} with {} pages", snapshot.revision, snapshot.content.pages.size)
                        SVHubNetwork.broadcastHello()
                    }
                }
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            SVHubRuntime.server = null
            SVHubRuntime.store.close()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> SVHubNetwork.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> SVHubNetwork.onDisconnect(handler.player) }
    }
}
