package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.action.ServerActionDispatcher
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.permission.EditAuthorization
import io.github.aristheg201.svhub.permission.SVHubPermissions
import io.github.aristheg201.svhub.server.EnvironmentManifest
import io.github.aristheg201.svhub.server.SnapshotProjector
import io.github.aristheg201.svhub.util.Compression
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

object SVHubNetwork {
    private data class ClientState(val protocol: Int, val modIds: Set<String>)
    private data class SnapshotRateKey(val playerId: UUID, val editor: Boolean)

    private val clients = ConcurrentHashMap<UUID, ClientState>()
    private val assemblers = ConcurrentHashMap<UUID, ChunkAssembler>()
    private val snapshotRequests = ConcurrentHashMap<SnapshotRateKey, Long>()

    fun registerCommon() {
        PayloadTypeRegistry.playS2C().register(HubHelloS2C.TYPE, HubHelloS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(HubSnapshotChunkS2C.TYPE, HubSnapshotChunkS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(HubOpenS2C.TYPE, HubOpenS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(HubEditorResultS2C.TYPE, HubEditorResultS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(HubClientManifestC2S.TYPE, HubClientManifestC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(HubActionC2S.TYPE, HubActionC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(HubRequestSnapshotC2S.TYPE, HubRequestSnapshotC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(HubEditorChunkC2S.TYPE, HubEditorChunkC2S.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(HubClientManifestC2S.TYPE) { payload, context ->
            context.server().execute {
                val player = context.player()
                clients[player.uuid] = ClientState(payload.protocol, EnvironmentManifest.modIds(payload.clientManifest))
                if (
                    payload.protocol == HUB_PROTOCOL_VERSION &&
                    SVHubRuntime.store.isReady() &&
                    payload.cachedRevision != SVHubRuntime.store.snapshot().revision &&
                    allowSnapshotRequest(player, false)
                ) {
                    sendSnapshot(player, false)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(HubActionC2S.TYPE) { payload, context ->
            context.server().execute { ServerActionDispatcher.execute(context.player(), payload.actionId) }
        }

        ServerPlayNetworking.registerGlobalReceiver(HubRequestSnapshotC2S.TYPE) { payload, context ->
            context.server().execute {
                val player = context.player()
                if (!SVHubRuntime.store.isReady()) {
                    sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, "SVHub đang tải dữ liệu, hãy thử lại ngay sau đó.")
                    return@execute
                }
                val editor = payload.editor && SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2)
                if (SVHubPermissions.has(player, SVHubPermissions.OPEN, 0) && allowSnapshotRequest(player, editor)) {
                    sendSnapshot(player, editor)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(HubEditorChunkC2S.TYPE) { payload, context ->
            context.server().execute { receiveEditorChunk(context.player(), payload) }
        }
    }

    fun clientHasMod(player: ServerPlayer, modId: String): Boolean = clients[player.uuid]?.modIds?.contains(modId) == true

    fun onJoin(player: ServerPlayer) {
        sendHello(player)
    }

    fun broadcastHello() {
        SVHubRuntime.server?.playerList?.players?.forEach(::sendHello)
    }

    private fun sendHello(player: ServerPlayer) {
        val open = SVHubPermissions.has(player, SVHubPermissions.OPEN, 0)
        val edit = open && SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2)
        ServerPlayNetworking.send(
            player,
            HubHelloS2C(
                HUB_PROTOCOL_VERSION,
                SVHubRuntime.store.snapshot().revision,
                open && SVHubRuntime.store.isReady(),
                edit && SVHubRuntime.store.isReady(),
                EnvironmentManifest.local().toJson()
            )
        )
    }

    fun onDisconnect(player: ServerPlayer) {
        clients.remove(player.uuid)
        assemblers.remove(player.uuid)?.clear()
        snapshotRequests.keys.removeIf { it.playerId == player.uuid }
        ServerActionDispatcher.clear(player)
    }

    fun open(player: ServerPlayer, page: String, editor: Boolean) {
        if (!SVHubRuntime.store.isReady()) return
        if (!SVHubPermissions.has(player, SVHubPermissions.OPEN, 0)) return
        ServerPlayNetworking.send(player, HubOpenS2C(page, editor && SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2)))
    }

    /** Server-initiated send; caller has already decided the delivery is needed. */
    fun sendSnapshot(player: ServerPlayer, editor: Boolean) {
        if (!SVHubRuntime.store.isReady()) return
        val clientMods = clients[player.uuid]?.modIds.orEmpty()
        val projected = SnapshotProjector.forPlayer(SVHubRuntime.store.snapshot().content, player, editor, clientMods)
        val encoded = Compression.encodeUtf8(HubContentCodec.encode(projected))
        val chunks = Compression.chunks(encoded)
        val transfer = ThreadLocalRandom.current().nextLong()
        chunks.forEachIndexed { index, chunk ->
            ServerPlayNetworking.send(player, HubSnapshotChunkS2C(transfer, projected.revision, editor, index, chunks.size, chunk))
        }
    }

    fun broadcastPlayerSnapshots() {
        if (!SVHubRuntime.store.isReady()) return
        SVHubRuntime.server?.playerList?.players?.forEach { player ->
            if (SVHubPermissions.has(player, SVHubPermissions.OPEN, 0)) sendSnapshot(player, false)
        }
    }

    private fun allowSnapshotRequest(player: ServerPlayer, editor: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val key = SnapshotRateKey(player.uuid, editor)
        val previous = snapshotRequests.put(key, now)
        return previous == null || now - previous >= SNAPSHOT_REQUEST_COOLDOWN_MS
    }

    private fun receiveEditorChunk(player: ServerPlayer, payload: HubEditorChunkC2S) {
        if (!SVHubRuntime.store.isReady()) {
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, "SVHub chưa sẵn sàng.")
            return
        }
        if (!SVHubPermissions.has(player, SVHubPermissions.EDITOR_PUBLISH, 2)) return

        val joined = runCatching {
            assemblers.computeIfAbsent(player.uuid) { ChunkAssembler() }
                .accept(payload.transferId, payload.index, payload.total, payload.chunk)
        }.getOrElse {
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, it.message ?: "Invalid editor transfer")
            return
        } ?: return

        val candidate = runCatching { HubContentCodec.decode(Compression.decodeUtf8(joined)) }.getOrElse {
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, it.message ?: "Decode failed")
            return
        }

        val current = SVHubRuntime.store.snapshot().content
        val reason = EditAuthorization.rejectReason(player, current, candidate)
        if (reason != null) {
            sendEditorResult(player, false, current.revision, reason)
            return
        }

        SVHubRuntime.store.commitAsync(payload.baseRevision, candidate).whenComplete { result, error ->
            SVHubRuntime.server?.execute {
                if (error != null) {
                    sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, error.message ?: "Publish failed")
                    return@execute
                }
                sendEditorResult(player, result.ok, result.revision, result.message)
                if (result.ok) broadcastPlayerSnapshots()
            }
        }
    }

    private fun sendEditorResult(player: ServerPlayer, ok: Boolean, revision: Long, message: String) {
        ServerPlayNetworking.send(player, HubEditorResultS2C(ok, revision, message.take(512)))
    }

    private const val SNAPSHOT_REQUEST_COOLDOWN_MS = 750L
}
