package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.SVHub
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object SVHubNetwork {
    private data class ClientState(val protocol: Int, val modIds: Set<String>)
    private data class SnapshotRateKey(val playerId: UUID, val editor: Boolean)
    private data class EditorSession(
        val baseRevision: Long,
        val baseline: HubContent,
        val createdAtEpochMs: Long
    )

    private val clients = ConcurrentHashMap<UUID, ClientState>()
    private val assemblers = ConcurrentHashMap<UUID, ChunkAssembler>()
    private val snapshotRequests = ConcurrentHashMap<SnapshotRateKey, Long>()
    private val editorSessions = ConcurrentHashMap<UUID, EditorSession>()
    private val pendingSnapshotJobs = ConcurrentHashMap.newKeySet<SnapshotRateKey>()
    private val pendingEditorDecodes = ConcurrentHashMap.newKeySet<UUID>()
    private val codecThreadCounter = AtomicInteger()
    private val codecExecutor = ThreadPoolExecutor(
        1,
        2,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(CODEC_QUEUE_CAPACITY),
        { task ->
            Thread(task, "SVHub-Codec-${codecThreadCounter.incrementAndGet()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        },
        ThreadPoolExecutor.AbortPolicy()
    ).apply {
        allowCoreThreadTimeOut(true)
    }

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
        editorSessions.clear()
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
                EnvironmentManifest.publicAdvertisement(setOf("player-hub")).toJson()
            )
        )
    }

    fun onDisconnect(player: ServerPlayer) {
        clients.remove(player.uuid)
        assemblers.remove(player.uuid)?.clear()
        editorSessions.remove(player.uuid)
        snapshotRequests.keys.removeIf { it.playerId == player.uuid }
        pendingSnapshotJobs.removeIf { it.playerId == player.uuid }
        pendingEditorDecodes.remove(player.uuid)
        ServerActionDispatcher.clear(player)
    }

    fun open(player: ServerPlayer, page: String, editor: Boolean) {
        if (!SVHubRuntime.store.isReady()) return
        if (!SVHubPermissions.has(player, SVHubPermissions.OPEN, 0)) return
        ServerPlayNetworking.send(player, HubOpenS2C(page, editor && SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2)))
    }

    /**
     * Projects permissions on the server thread, then performs JSON/gzip/chunk work
     * on a bounded codec pool. Packet sends return to the server thread.
     */
    fun sendSnapshot(player: ServerPlayer, editor: Boolean) {
        if (!SVHubRuntime.store.isReady()) return
        val server = SVHubRuntime.server ?: return
        val key = SnapshotRateKey(player.uuid, editor)
        if (!pendingSnapshotJobs.add(key)) return

        val clientMods = clients[player.uuid]?.modIds.orEmpty()
        val projected = SnapshotProjector.forPlayer(SVHubRuntime.store.snapshot().content, player, editor, clientMods)
        if (editor) {
            editorSessions[player.uuid] = EditorSession(projected.revision, projected, System.currentTimeMillis())
        }
        val playerId = player.uuid

        try {
            codecExecutor.execute {
                val result = runCatching {
                    Compression.chunks(Compression.encodeUtf8(HubContentCodec.encode(projected)))
                }
                server.execute {
                    pendingSnapshotJobs.remove(key)
                    val livePlayer = server.playerList.getPlayer(playerId) ?: return@execute
                    result.onSuccess { chunks ->
                        if (SVHubRuntime.store.snapshot().revision != projected.revision) {
                            if (editor) editorSessions.remove(playerId)
                            sendSnapshot(livePlayer, editor)
                            return@onSuccess
                        }
                        val transfer = ThreadLocalRandom.current().nextLong()
                        chunks.forEachIndexed { index, chunk ->
                            ServerPlayNetworking.send(livePlayer, HubSnapshotChunkS2C(transfer, projected.revision, editor, index, chunks.size, chunk))
                        }
                    }.onFailure { error ->
                        if (editor) editorSessions.remove(playerId)
                        SVHub.LOGGER.warn("Failed to encode SVHub snapshot for {}", playerId, error)
                        if (editor) {
                            sendEditorResult(livePlayer, false, SVHubRuntime.store.snapshot().revision, "Không thể chuẩn bị editor snapshot.")
                        }
                    }
                }
            }
        } catch (rejected: RejectedExecutionException) {
            pendingSnapshotJobs.remove(key)
            if (editor) editorSessions.remove(player.uuid)
            SVHub.LOGGER.warn("SVHub codec queue full while preparing snapshot for {}", player.uuid)
            if (editor) sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, "SVHub đang bận xử lý dữ liệu; hãy thử lại.")
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
        if (!SVHubPermissions.has(player, SVHubPermissions.EDITOR, 2) ||
            !SVHubPermissions.has(player, SVHubPermissions.EDITOR_PUBLISH, 2)) return

        val joined = runCatching {
            assemblers.computeIfAbsent(player.uuid) { ChunkAssembler() }
                .accept(payload.transferId, payload.index, payload.total, payload.chunk)
        }.getOrElse {
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, it.message ?: "Invalid editor transfer")
            return
        } ?: return

        val playerId = player.uuid
        if (!pendingEditorDecodes.add(playerId)) {
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, "Một publish khác đang được xử lý.")
            return
        }
        val server = SVHubRuntime.server ?: run {
            pendingEditorDecodes.remove(playerId)
            return
        }
        val baseRevision = payload.baseRevision

        try {
            codecExecutor.execute {
                val decoded = runCatching { HubContentCodec.decode(Compression.decodeUtf8(joined)) }
                server.execute {
                    pendingEditorDecodes.remove(playerId)
                    val livePlayer = server.playerList.getPlayer(playerId) ?: return@execute
                    decoded.onSuccess { candidate ->
                        processEditorCandidate(livePlayer, baseRevision, candidate)
                    }.onFailure { error ->
                        sendEditorResult(livePlayer, false, SVHubRuntime.store.snapshot().revision, error.message ?: "Decode failed")
                    }
                }
            }
        } catch (rejected: RejectedExecutionException) {
            pendingEditorDecodes.remove(playerId)
            sendEditorResult(player, false, SVHubRuntime.store.snapshot().revision, "SVHub đang bận xử lý dữ liệu; hãy thử lại.")
        }
    }

    private fun processEditorCandidate(player: ServerPlayer, baseRevision: Long, candidate: HubContent) {
        val current = SVHubRuntime.store.snapshot().content
        val publishCandidate = if (SVHubPermissions.has(player, SVHubPermissions.EDITOR_ALL, 2)) {
            candidate
        } else {
            val session = editorSessions[player.uuid]
            if (session == null || session.baseRevision != baseRevision) {
                sendEditorResult(player, false, current.revision, "Editor session không còn hợp lệ; hãy tải lại editor.")
                return
            }
            if (System.currentTimeMillis() - session.createdAtEpochMs > EDITOR_SESSION_TTL_MS) {
                editorSessions.remove(player.uuid, session)
                sendEditorResult(player, false, current.revision, "Editor session đã hết hạn; hãy tải lại editor.")
                return
            }

            val merge = ScopedEditMerge.merge(
                current = current,
                baseline = session.baseline,
                candidate = candidate,
                permissions = ScopedEditPermissions(
                    editSettings = SVHubPermissions.has(player, SVHubPermissions.EDITOR_SETTINGS, 2),
                    editAssets = SVHubPermissions.has(player, SVHubPermissions.EDITOR_ASSETS, 2),
                    createPages = SVHubPermissions.has(player, SVHubPermissions.EDITOR_CREATE, 2),
                    editPage = { id -> SVHubPermissions.has(player, SVHubPermissions.pageEditor(id), 2) }
                )
            )
            if (!merge.ok) {
                sendEditorResult(player, false, current.revision, merge.error ?: "Scoped editor merge failed")
                return
            }
            requireNotNull(merge.content)
        }

        val reason = EditAuthorization.rejectReason(player, current, publishCandidate)
        if (reason != null) {
            sendEditorResult(player, false, current.revision, reason)
            return
        }

        val playerId = player.uuid
        val playerName = player.gameProfile.name
        SVHubRuntime.store.commitAsync(baseRevision, publishCandidate).whenComplete { result, error ->
            SVHubRuntime.server?.execute {
                val livePlayer = SVHubRuntime.server?.playerList?.getPlayer(playerId) ?: return@execute
                if (error != null) {
                    SVHub.LOGGER.warn(
                        "Hub publish failed: player={} uuid={} baseRevision={} currentRevision={}",
                        playerName,
                        playerId,
                        baseRevision,
                        SVHubRuntime.store.snapshot().revision,
                        error
                    )
                    sendEditorResult(livePlayer, false, SVHubRuntime.store.snapshot().revision, error.message ?: "Publish failed")
                    return@execute
                }
                sendEditorResult(livePlayer, result.ok, result.revision, result.message)
                if (result.ok) {
                    SVHub.LOGGER.info(
                        "Hub publish: player={} uuid={} baseRevision={} newRevision={} pages={} assets={}",
                        playerName,
                        playerId,
                        baseRevision,
                        result.revision,
                        publishCandidate.pages.size,
                        publishCandidate.assets.size
                    )
                    editorSessions.remove(playerId)
                    broadcastPlayerSnapshots()
                } else {
                    SVHub.LOGGER.warn(
                        "Hub publish rejected: player={} uuid={} baseRevision={} currentRevision={} reason={}",
                        playerName,
                        playerId,
                        baseRevision,
                        result.revision,
                        result.message
                    )
                }
            }
        }
    }

    private fun sendEditorResult(player: ServerPlayer, ok: Boolean, revision: Long, message: String) {
        ServerPlayNetworking.send(player, HubEditorResultS2C(ok, revision, message.take(512)))
    }

    private const val SNAPSHOT_REQUEST_COOLDOWN_MS = 750L
    private const val EDITOR_SESSION_TTL_MS = 15 * 60 * 1000L
    private const val CODEC_QUEUE_CAPACITY = 32
}
