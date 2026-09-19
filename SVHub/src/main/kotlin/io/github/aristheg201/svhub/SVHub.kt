package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.api.HubIntegrationDescriptor
import io.github.aristheg201.svhub.api.SVHubApi
import io.github.aristheg201.svhub.command.NativeCommands
import io.github.aristheg201.svhub.command.SVHubCommands
import io.github.aristheg201.svhub.command.TftCommands
import io.github.aristheg201.svhub.companion.VanillaCompanionService
import io.github.aristheg201.svhub.content.HubStore
import io.github.aristheg201.svhub.content.V011ContentPatch
import io.github.aristheg201.svhub.content.V020ContentPatch
import io.github.aristheg201.svhub.content.V030ContentPatch
import io.github.aristheg201.svhub.native.NativePlatform
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import io.github.aristheg201.svhub.server.PokemonRuntimeInfoService
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
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:companions", "Vanilla Companions", setOf("vanilla_entities", "selection", "persistence", "turn_based_arena")))
        SVHubApi.registerIntegration(HubIntegrationDescriptor("svhub:native", "SV Native Platform", setOf("wallet", "gacha", "skins", "matchmaking", "chess", "xiangqi", "ludo", "uno", "cards", "tft", "tower_defense")))
        SVHubApi.registerServerActionType("companion_select") { player, action -> VanillaCompanionService.select(player, action.value) }
        SVHubApi.registerServerActionType("native_open") { player, action -> NativePlatform.open(player, action.value) }
        val configRoot = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        SVHubRuntime.store = HubStore(configRoot)
        VanillaCompanionService.start(configRoot.resolve("companions.json"))
        NativePlatform.start(configRoot.resolve("native"))
        PokemonRuntimeInfoService.start()
        SVHubNetwork.registerCommon(); NativePlatformNetwork.registerCommon(); SVHubCommands.register(); NativeCommands.register(); TftCommands.register()
        ServerTickEvents.END_SERVER_TICK.register(VanillaCompanionService::tick); ServerTickEvents.END_SERVER_TICK.register(NativePlatform::tick)
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            SVHubRuntime.server = server
            SVHubRuntime.store.initializeAsync().whenComplete { snapshot, error -> server.execute {
                if (error != null) { LOGGER.error("Unable to load SVHub content; keeping safe defaults", error); return@execute }
                var candidate=snapshot.content;var changed=false
                V011ContentPatch.apply(candidate)?.let{candidate=it;changed=true};V020ContentPatch.apply(candidate)?.let{candidate=it;changed=true};V030ContentPatch.apply(candidate)?.let{candidate=it;changed=true}
                if(!changed){LOGGER.info("SVHub loaded revision {} with {} pages",snapshot.revision,snapshot.content.pages.size);SVHubNetwork.broadcastHello();return@execute}
                SVHubRuntime.store.commitAsync(snapshot.revision,candidate).whenComplete{result,patchError->server.execute{if(patchError!=null)LOGGER.error("SVHub native content upgrade failed",patchError)else if(!result.ok)LOGGER.error("SVHub native content upgrade rejected: {}",result.message)else LOGGER.info("SVHub native platform installed at revision {}",result.revision);SVHubNetwork.broadcastHello();SVHubNetwork.broadcastPlayerSnapshots()}}
            }}
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, success -> if (success) PokemonRuntimeInfoService.invalidate() }
        ServerLifecycleEvents.SERVER_STOPPING.register { server -> VanillaCompanionService.shutdown(server); NativePlatform.shutdown(); PokemonRuntimeInfoService.shutdown(); SVHubRuntime.server=null; SVHubRuntime.store.close() }
        ServerPlayConnectionEvents.JOIN.register { handler,_,_->SVHubNetwork.onJoin(handler.player);VanillaCompanionService.onJoin(handler.player);NativePlatform.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler,_->NativePlatformNetwork.close(handler.player.uuid);NativePlatform.onDisconnect(handler.player);VanillaCompanionService.onDisconnect(handler.player);SVHubNetwork.onDisconnect(handler.player) }
    }
}
