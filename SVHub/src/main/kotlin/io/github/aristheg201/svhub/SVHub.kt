package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.api.HubIntegrationDescriptor
import io.github.aristheg201.svhub.api.SVHubApi
import io.github.aristheg201.svhub.command.SVHubCommands
import io.github.aristheg201.svhub.companion.VanillaCompanionService
import io.github.aristheg201.svhub.content.HubStore
import io.github.aristheg201.svhub.content.V011ContentPatch
import io.github.aristheg201.svhub.network.SVHubNetwork
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object SVHub : ModInitializer {
    const val MOD_ID = "svhub"
    val LOGGER = LoggerFactory.getLogger("SVHub")

    override fun onInitialize() {
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:core", "SVHub Core", setOf("content", "editor", "search", "themes", "assets")))
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:cobblemon", "Cobblemon Wiki", setOf("species", "model", "fakemon"), setOf("cobblemon")))
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:companions", "Vanilla Companions", setOf("vanilla_entities", "selection", "persistence")))
        SVHubApi.registerServerActionType("companion_select") { player, action ->
            VanillaCompanionService.select(player, action.value)
        }

        val configRoot = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        SVHubRuntime.store = HubStore(configRoot)
        VanillaCompanionService.start(configRoot.resolve("companions.json"))
        SVHubNetwork.registerCommon()
        SVHubCommands.register()
        ServerTickEvents.END_SERVER_TICK.register(VanillaCompanionService::tick)

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            SVHubRuntime.server = server
            SVHubRuntime.store.initializeAsync().whenComplete { snapshot, error ->
                server.execute {
                    if (error != null) {
                        LOGGER.error("Unable to load SVHub content; keeping safe defaults", error)
                        return@execute
                    }

                    val candidate = V011ContentPatch.apply(snapshot.content)
                    if (candidate == null) {
                        LOGGER.info("SVHub loaded revision {} with {} pages", snapshot.revision, snapshot.content.pages.size)
                        SVHubNetwork.broadcastHello()
                        return@execute
                    }

                    // Publish through HubStore instead of writing content.json directly:
                    // validation, revision conflict protection and history all stay intact.
                    SVHubRuntime.store.commitAsync(snapshot.revision, candidate).whenComplete { result, patchError ->
                        server.execute {
                            if (patchError != null) {
                                LOGGER.error("SVHub 0.1.1 content upgrade failed", patchError)
                            } else if (!result.ok) {
                                LOGGER.error("SVHub 0.1.1 content upgrade rejected: {}", result.message)
                            } else {
                                val upgraded = SVHubRuntime.store.snapshot()
                                LOGGER.info("SVHub 0.1.1 modules installed at revision {} with {} pages", upgraded.revision, upgraded.content.pages.size)
                            }
                            SVHubNetwork.broadcastHello()
                            SVHubNetwork.broadcastPlayerSnapshots()
                        }
                    }
                }
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            VanillaCompanionService.shutdown(server)
            SVHubRuntime.server = null
            SVHubRuntime.store.close()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            SVHubNetwork.onJoin(handler.player)
            VanillaCompanionService.onJoin(handler.player)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            VanillaCompanionService.onDisconnect(handler.player)
            SVHubNetwork.onDisconnect(handler.player)
        }
    }
}
