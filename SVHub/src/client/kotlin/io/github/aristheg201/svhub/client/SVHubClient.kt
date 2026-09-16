package io.github.aristheg201.svhub.client

import com.mojang.blaze3d.platform.InputConstants
import io.github.aristheg201.svhub.client.editor.HubEditorScreen
import io.github.aristheg201.svhub.client.gui.HubScreen
import io.github.aristheg201.svhub.content.HUB_PROTOCOL_VERSION
import io.github.aristheg201.svhub.network.*
import io.github.aristheg201.svhub.server.EnvironmentManifest
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.concurrent.Executors

object SVHubClient : ClientModInitializer {
    private lateinit var openHubKey: KeyMapping
    @Volatile private var pendingRoute: String? = null
    @Volatile private var pendingEditor = false
    private val snapshotExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SVHub-Client-IO").apply { isDaemon = true }
    }

    override fun onInitializeClient() {
        openHubKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping("key.svhub.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.svhub")
        )

        ClientPlayNetworking.registerGlobalReceiver(HubHelloS2C.TYPE) { payload, context ->
            context.client().execute {
                ClientHubState.serverRevision = payload.revision
                ClientHubState.canOpen = payload.canOpen
                ClientHubState.canEdit = payload.canEdit
                ClientHubState.serverManifest = payload.serverManifest
                val cachedRevision = if (payload.protocol == HUB_PROTOCOL_VERSION) ClientHubState.loadCachedIfRevision(payload.revision) else -1L
                val manifest = EnvironmentManifest.clientAdvertisement(setOf("gui", "pixel-renderer", "cobblemon-model", "asset-cache", "editor"))
                ClientPlayNetworking.send(HubClientManifestC2S(HUB_PROTOCOL_VERSION, cachedRevision, manifest.toJson()))
                if (payload.protocol != HUB_PROTOCOL_VERSION) {
                    context.player().sendSystemMessage(Component.literal("SVHub protocol không tương thích: client=$HUB_PROTOCOL_VERSION, server=${payload.protocol}"))
                } else if (cachedRevision == payload.revision) {
                    openPendingIfReady()
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(HubSnapshotChunkS2C.TYPE) { payload, context ->
            context.client().execute {
                val encoded = ClientHubState.acceptSnapshotChunk(payload.transferId, payload.editor, payload.index, payload.total, payload.chunk) ?: return@execute
                snapshotExecutor.execute decode@{
                    val decoded = runCatching { ClientHubState.decodeSnapshot(encoded) }.getOrElse { error ->
                        context.client().execute { ClientHubState.lastEditorMessage = "Không thể đọc snapshot SVHub: ${error.message}" }
                        return@decode
                    }
                    context.client().execute apply@{
                        if (!ClientHubState.applySnapshot(decoded, payload.revision, payload.editor)) return@apply
                        if (payload.editor && pendingEditor) {
                            pendingEditor = false
                            Minecraft.getInstance().setScreen(HubEditorScreen())
                        } else if (!payload.editor) {
                            openPendingIfReady()
                        }
                    }
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(HubOpenS2C.TYPE) { payload, context ->
            context.client().execute openPacket@{
                if (payload.editor) {
                    if (!ClientHubState.canEdit) return@openPacket
                    if (ClientHubState.editorContent?.revision == ClientHubState.serverRevision) {
                        Minecraft.getInstance().setScreen(HubEditorScreen())
                    } else {
                        pendingEditor = true
                        ClientPlayNetworking.send(HubRequestSnapshotC2S(true))
                    }
                } else {
                    openHub(payload.page)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(HubEditorResultS2C.TYPE) { payload, context ->
            context.client().execute {
                ClientHubState.lastEditorMessage = payload.message
                ClientHubState.serverRevision = payload.revision
                val screen = Minecraft.getInstance().screen
                if (screen is HubEditorScreen) {
                    screen.onPublishResult(payload.ok, payload.message)
                    if (payload.ok) {
                        pendingEditor = true
                        ClientPlayNetworking.send(HubRequestSnapshotC2S(true))
                    }
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openHubKey.consumeClick()) {
                if (client.player != null) openHub("home")
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            pendingRoute = null
            pendingEditor = false
            ClientHubState.reset()
        }
    }

    fun openHub(route: String = "home") {
        val client = Minecraft.getInstance()
        if (client.player == null || !ClientHubState.canOpen) return
        if (ClientHubState.playerContent != null) {
            pendingRoute = null
            client.setScreen(HubScreen(route))
        } else {
            pendingRoute = route
            ClientPlayNetworking.send(HubRequestSnapshotC2S(false))
        }
    }

    fun requestEditor() {
        if (!ClientHubState.canEdit || Minecraft.getInstance().player == null) return
        pendingEditor = true
        ClientPlayNetworking.send(HubRequestSnapshotC2S(true))
    }

    private fun openPendingIfReady() {
        val route = pendingRoute ?: return
        if (ClientHubState.playerContent == null) return
        pendingRoute = null
        Minecraft.getInstance().setScreen(HubScreen(route))
    }
}
