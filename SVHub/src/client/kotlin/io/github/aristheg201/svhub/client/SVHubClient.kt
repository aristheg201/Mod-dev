package io.github.aristheg201.svhub.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.ClientPokemonRuntimeInfo
import io.github.aristheg201.svhub.client.cobblemon.PokemonInfoProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.editor.HubEditorScreen
import io.github.aristheg201.svhub.client.gui.HubScreen
import io.github.aristheg201.svhub.client.nativeui.NativeGameVisualRegistry
import io.github.aristheg201.svhub.client.render.GeneratedBackgroundRenderer
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.content.HUB_PROTOCOL_VERSION
import io.github.aristheg201.svhub.network.*
import io.github.aristheg201.svhub.server.EnvironmentManifest
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.lwjgl.glfw.GLFW
import java.util.concurrent.Executors

object SVHubClient : ClientModInitializer {
    private lateinit var openHubKey: KeyMapping
    @Volatile private var pendingRoute: String? = null
    @Volatile private var pendingEditor = false
    private val snapshotExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "SVHub-Client-IO").apply { isDaemon = true } }

    override fun onInitializeClient() {
        openHubKey = KeyBindingHelper.registerKeyBinding(KeyMapping("key.svhub.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.svhub"))
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("svhub", "client_render_caches")
            override fun onResourceManagerReload(resourceManager: ResourceManager) {
                PokemonModelRenderer.clear(); NativeGameVisualRegistry.clear(); PokemonInfoProvider.clear(); ClientPokemonRuntimeInfo.clear(); CobblemonWikiProvider.clearCaches(); GeneratedBackgroundRenderer.clear(); MiniMessageText.clear()
            }
        })
        ClientPlayNetworking.registerGlobalReceiver(HubHelloS2C.TYPE) { payload, context -> context.client().execute {
            ClientHubState.serverRevision=payload.revision;ClientHubState.canOpen=payload.canOpen;ClientHubState.canEdit=payload.canEdit;ClientHubState.serverManifest=payload.serverManifest;ClientHubState.setCachePolicy(payload.cacheable)
            if(!payload.canOpen){pendingRoute=null;pendingEditor=false}
            val cachedRevision=if(payload.protocol==HUB_PROTOCOL_VERSION&&payload.cacheable)ClientHubState.loadCachedIfRevision(payload.revision)else -1L
            val manifest=EnvironmentManifest.clientAdvertisement(setOf("gui","cobblemon-model","asset-cache","editor","minimessage","placeholder-api"));ClientPlayNetworking.send(HubClientManifestC2S(HUB_PROTOCOL_VERSION,cachedRevision,manifest.toJson()))
            if(payload.protocol!=HUB_PROTOCOL_VERSION)context.player().sendSystemMessage(Component.literal("SVHub protocol không tương thích: client=$HUB_PROTOCOL_VERSION, server=${payload.protocol}"))else if(cachedRevision==payload.revision)openPendingIfReady()
        }}
        ClientPlayNetworking.registerGlobalReceiver(HubSnapshotChunkS2C.TYPE){payload,context->context.client().execute{val encoded=ClientHubState.acceptSnapshotChunk(payload.transferId,payload.editor,payload.index,payload.total,payload.chunk)?:return@execute;snapshotExecutor.execute decode@{val decoded=runCatching{ClientHubState.decodeSnapshot(encoded)}.getOrElse{error->context.client().execute{ClientHubState.lastEditorMessage="Không thể đọc snapshot SVHub: ${error.message}"};return@decode};context.client().execute apply@{if(!ClientHubState.applySnapshot(decoded,payload.revision,payload.editor))return@apply;if(payload.editor&&pendingEditor){pendingEditor=false;Minecraft.getInstance().setScreen(HubEditorScreen())}else if(!payload.editor)openPendingIfReady()}}}}
        ClientPlayNetworking.registerGlobalReceiver(HubOpenS2C.TYPE){payload,context->context.client().execute openPacket@{if(payload.editor){if(!ClientHubState.canEdit)return@openPacket;if(ClientHubState.editorContent?.revision==ClientHubState.serverRevision)Minecraft.getInstance().setScreen(HubEditorScreen())else{pendingEditor=true;ClientPlayNetworking.send(HubRequestSnapshotC2S(true))}}else openHub(payload.page,serverAuthorized=true)}}
        ClientPlayNetworking.registerGlobalReceiver(PokemonRuntimeInfoS2C.TYPE){payload,context->context.client().execute{ClientPokemonRuntimeInfo.accept(payload)}}
        ClientPlayNetworking.registerGlobalReceiver(HubEditorResultS2C.TYPE){payload,context->context.client().execute{ClientHubState.lastEditorMessage=payload.message;ClientHubState.serverRevision=payload.revision;val screen=Minecraft.getInstance().screen;if(screen is HubEditorScreen){screen.onPublishResult(payload.ok,payload.message);if(payload.ok){pendingEditor=true;ClientPlayNetworking.send(HubRequestSnapshotC2S(true))}}}}
        ClientTickEvents.END_CLIENT_TICK.register{client->while(openHubKey.consumeClick()){if(client.player!=null)openHub("home")}}
        ClientPlayConnectionEvents.DISCONNECT.register{_,_->pendingRoute=null;pendingEditor=false;ClientHubState.reset();ClientPokemonRuntimeInfo.clear()}
    }
    fun openHub(route:String="home"){openHub(route,false)}
    private fun openHub(route:String,serverAuthorized:Boolean){val client=Minecraft.getInstance();if(client.player==null)return;if(!serverAuthorized&&!ClientHubState.canOpen){pendingRoute=route;ClientPlayNetworking.send(HubRequestSnapshotC2S(false));return};if(ClientHubState.playerContent!=null){pendingRoute=null;client.setScreen(HubScreen(route));if(!ClientHubState.cacheAllowed)ClientPlayNetworking.send(HubRequestSnapshotC2S(false))}else{pendingRoute=route;ClientPlayNetworking.send(HubRequestSnapshotC2S(false))}}
    fun requestEditor(){if(!ClientHubState.canEdit||Minecraft.getInstance().player==null)return;pendingEditor=true;ClientPlayNetworking.send(HubRequestSnapshotC2S(true))}
    private fun openPendingIfReady(){val route=pendingRoute?:return;if(!ClientHubState.canOpen||ClientHubState.playerContent==null)return;pendingRoute=null;Minecraft.getInstance().setScreen(HubScreen(route))}
}
