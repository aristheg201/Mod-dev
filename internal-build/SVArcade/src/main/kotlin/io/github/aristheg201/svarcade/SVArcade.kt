package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.api.HubIntegrationDescriptor
import io.github.aristheg201.svarcade.api.SVArcadeApi
import io.github.aristheg201.svarcade.command.NativeCommands
import io.github.aristheg201.svarcade.command.SVArcadeCommands
import io.github.aristheg201.svarcade.command.TftCommands
import io.github.aristheg201.svarcade.companion.VanillaCompanionService
import io.github.aristheg201.svarcade.content.HubStore
import io.github.aristheg201.svarcade.content.V011ContentPatch
import io.github.aristheg201.svarcade.content.V020ContentPatch
import io.github.aristheg201.svarcade.content.V030ContentPatch
import io.github.aristheg201.svarcade.native.NativeCosmeticService
import io.github.aristheg201.svarcade.native.NativePlatform
import io.github.aristheg201.svarcade.native.network.NativePlatformNetwork
import io.github.aristheg201.svarcade.server.PokemonRuntimeInfoService
import io.github.aristheg201.svarcade.network.SVArcadeNetwork
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object SVArcade : ModInitializer {
    const val MOD_ID = "svarcade"
    val LOGGER = LoggerFactory.getLogger("SVArcade")
    override fun onInitialize() {
        SVArcadeApi.registerIntegration(HubIntegrationDescriptor("svarcade:core", "SVArcade Core", setOf("content", "editor", "search", "themes", "assets")))
        SVArcadeApi.registerIntegration(HubIntegrationDescriptor("svarcade:cobblemon", "Cobblemon Wiki", setOf("species", "model", "fakemon"), setOf("cobblemon")))
        SVArcadeApi.registerIntegration(HubIntegrationDescriptor("svarcade:companions", "Vanilla Companions", setOf("vanilla_entities", "selection", "persistence", "turn_based_arena")))
        SVArcadeApi.registerIntegration(HubIntegrationDescriptor("svarcade:native", "SV Native Platform", setOf("wallet", "gacha", "skins", "matchmaking", "chess", "xiangqi", "ludo", "uno", "cards", "tft", "tower_defense")))
        SVArcadeApi.registerServerActionType("companion_select") { player, action -> VanillaCompanionService.select(player, action.value) }
        SVArcadeApi.registerServerActionType("native_open") { player, action -> NativePlatform.open(player, action.value) }
        val configRoot = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        io.github.aristheg201.svarcade.content.ServerHelpText.start(configRoot.resolve("server-help.json"))
        SVArcadeRuntime.store = HubStore(configRoot)
        VanillaCompanionService.start(configRoot.resolve("companions.json"))
        NativePlatform.start(configRoot.resolve("native"))
        PokemonRuntimeInfoService.start()
        SVArcadeNetwork.registerCommon(); NativePlatformNetwork.registerCommon(); SVArcadeCommands.register(); NativeCommands.register(); io.github.aristheg201.svarcade.command.ArcadeCommands.register(); TftCommands.register()
        ServerTickEvents.END_SERVER_TICK.register(VanillaCompanionService::tick); ServerTickEvents.END_SERVER_TICK.register(NativePlatform::tick)
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            SVArcadeRuntime.server = server
            // Run after SERVER_STARTED listeners have returned so BEconomy gets its own
            // lifecycle callback first regardless of mod initialization order.
            server.execute { NativeCosmeticService.verifyProvider() }
            SVArcadeRuntime.store.initializeAsync().whenComplete { snapshot, error -> server.execute {
                if (error != null) { LOGGER.error("Unable to load SVArcade content; keeping safe defaults", error); return@execute }
                var candidate=snapshot.content;var changed=false
                V011ContentPatch.apply(candidate)?.let{candidate=it;changed=true};V020ContentPatch.apply(candidate)?.let{candidate=it;changed=true};V030ContentPatch.apply(candidate)?.let{candidate=it;changed=true}
                if(!changed){LOGGER.info("SVArcade loaded revision {} with {} pages",snapshot.revision,snapshot.content.pages.size);SVArcadeNetwork.broadcastHello();return@execute}
                SVArcadeRuntime.store.commitAsync(snapshot.revision,candidate).whenComplete{result,patchError->server.execute{if(patchError!=null)LOGGER.error("SVArcade native content upgrade failed",patchError)else if(!result.ok)LOGGER.error("SVArcade native content upgrade rejected: {}",result.message)else LOGGER.info("SVArcade native platform installed at revision {}",result.revision);SVArcadeNetwork.broadcastHello();SVArcadeNetwork.broadcastPlayerSnapshots()}}
            }}
        }
        io.github.aristheg201.svarcade.native.InternalArcadeAcceptance.register()
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, success -> if (success) PokemonRuntimeInfoService.invalidate() }
        ServerLifecycleEvents.SERVER_STOPPING.register { server -> VanillaCompanionService.shutdown(server); NativePlatform.shutdown(); PokemonRuntimeInfoService.shutdown(); SVArcadeRuntime.server=null; SVArcadeRuntime.store.close() }
        ServerPlayConnectionEvents.JOIN.register { handler,_,_->SVArcadeNetwork.onJoin(handler.player);VanillaCompanionService.onJoin(handler.player);NativePlatform.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler,_->NativePlatformNetwork.close(handler.player.uuid);NativePlatform.onDisconnect(handler.player);VanillaCompanionService.onDisconnect(handler.player);SVArcadeNetwork.onDisconnect(handler.player) }
    }
}
