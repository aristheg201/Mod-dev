package io.github.aristheg201.svhub.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.util.AtomicFiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object NativeArcadeSessionStore {
    data class StoredSession(
        val schema: Int = 1,
        val sessionId: String,
        val gameId: String,
        val mode: String,
        val createdAtEpochMs: Long,
        val seats: List<NativeSeat>,
        val humanActions: Map<String, Int> = emptyMap(),
        val forfeited: Set<String> = emptySet(),
        val controllers: Map<String, NativeBotRuntime.ControllerState> = emptyMap(),
        val reconnectRemainingMs: Map<String, Long> = emptyMap(),
        val state: JsonObject,
        val savedAtEpochMs: Long = System.currentTimeMillis()
    )

    private data class Pending(val record: StoredSession?, val delete: Boolean)

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val loaded = ConcurrentLinkedQueue<StoredSession>()
    private val latest = ConcurrentHashMap<String, Pending>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val retryAfter = ConcurrentHashMap<String, Long>()
    private val closed = AtomicBoolean(true)
    private val loadComplete = AtomicBoolean(false)
    @Volatile private var io: ExecutorService? = null
    private lateinit var root: Path

    fun start(path: Path) {
        root = path
        loaded.clear()
        latest.clear()
        queued.clear()
        retryAfter.clear()
        loadComplete.set(false)
        closed.set(false)
        val pool = synchronized(this) {
            val existing = io
            if (existing != null && !existing.isShutdown) existing else {
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "SVHub-ArcadeSession-IO").apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY - 1
                    }
                }.also { io = it }
            }
        }
        try {
            pool.execute(::loadAll)
        } catch (error: RejectedExecutionException) {
            loadComplete.set(true)
            SVHub.LOGGER.error("Arcade session store rejected startup load", error)
        }
    }

    fun isLoadComplete(): Boolean = loadComplete.get()

    fun drainLoaded(): List<StoredSession> = buildList {
        while (true) add(loaded.poll() ?: break)
    }

    fun save(record: StoredSession) {
        if (closed.get() || !valid(record)) return
        val encodedSize = runCatching { gson.toJson(record).length }.getOrDefault(Int.MAX_VALUE)
        if (encodedSize > MAX_JSON_CHARS) {
            SVHub.LOGGER.error("Refusing oversized arcade session snapshot {}", record.sessionId)
            return
        }
        latest[record.sessionId] = Pending(record, false)
        val retryAt = retryAfter[record.sessionId]
        if (retryAt == null || System.currentTimeMillis() >= retryAt) queueWriter(record.sessionId)
    }

    fun delete(sessionId: String) {
        if (closed.get() || sessionId.isBlank()) return
        latest[sessionId] = Pending(null, true)
        retryAfter.remove(sessionId)
        queueWriter(sessionId)
    }

    fun tick(nowMillis: Long = System.currentTimeMillis()) {
        retryAfter.entries.toList().forEach { (sessionId, retryAt) ->
            if (nowMillis >= retryAt && latest.containsKey(sessionId)) {
                retryAfter.remove(sessionId, retryAt)
                queueWriter(sessionId)
            }
        }
    }

    fun shutdown() {
        if (closed.get()) return
        latest.keys.toList().forEach(::queueWriter)
        closed.set(true)
        val pool = synchronized(this) {
            val current = io
            io = null
            current
        }
        pool?.shutdown()
        try {
            if (pool != null && !pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow()
        } catch (_: InterruptedException) {
            pool?.shutdownNow()
            Thread.currentThread().interrupt()
        }
        loaded.clear()
        latest.clear()
        queued.clear()
        retryAfter.clear()
        loadComplete.set(false)
    }

    internal fun encode(record: StoredSession): String = gson.toJson(record)

    internal fun decode(text: String): StoredSession? {
        if (text.length > MAX_JSON_CHARS) return null
        return runCatching {
            val json = gson.fromJson(text, JsonObject::class.java)
            if (!json.has("controllers")) json.add("controllers", JsonObject())
            if (!json.has("reconnectRemainingMs")) json.add("reconnectRemainingMs", JsonObject())
            gson.fromJson(json, StoredSession::class.java)
        }.getOrNull()?.takeIf(::valid)
    }

    private fun loadAll() {
        try {
            Files.createDirectories(root)
            Files.list(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .limit(MAX_SESSION_FILES.toLong())
                    .forEach { path ->
                        runCatching {
                            if (Files.size(path) > MAX_JSON_CHARS.toLong() * 4L) {
                                SVHub.LOGGER.warn("Skipping oversized arcade session file {}", path.fileName)
                                return@runCatching
                            }
                            val record = decode(Files.readString(path, StandardCharsets.UTF_8))
                            if (record == null) {
                                SVHub.LOGGER.warn("Skipping invalid arcade session file {}", path.fileName)
                            } else {
                                loaded.add(record)
                            }
                        }.onFailure { error ->
                            SVHub.LOGGER.warn("Unable to load arcade session file {}", path.fileName, error)
                        }
                    }
            }
        } catch (error: Exception) {
            SVHub.LOGGER.error("Unable to scan arcade session store", error)
        } finally {
            loadComplete.set(true)
        }
    }

    private fun queueWriter(sessionId: String) {
        val pool = io ?: return
        if (!queued.add(sessionId)) return
        try {
            pool.execute {
                try {
                    while (true) {
                        val pending = latest.remove(sessionId) ?: break
                        val ok = if (pending.delete) {
                            runCatching {
                                Files.deleteIfExists(pathFor(sessionId))
                                true
                            }.getOrElse {
                                SVHub.LOGGER.warn("Unable to delete arcade session {}", sessionId, it)
                                false
                            }
                        } else {
                            pending.record?.let(::writeSnapshot) ?: true
                        }
                        if (!ok) {
                            latest[sessionId] = pending
                            retryAfter[sessionId] = System.currentTimeMillis() + SAVE_RETRY_MS
                            break
                        }
                        retryAfter.remove(sessionId)
                        if (!latest.containsKey(sessionId)) break
                    }
                } finally {
                    queued.remove(sessionId)
                    val retryAt = retryAfter[sessionId]
                    if (!closed.get() && latest.containsKey(sessionId) &&
                        (retryAt == null || System.currentTimeMillis() >= retryAt)
                    ) {
                        queueWriter(sessionId)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            queued.remove(sessionId)
        }
    }

    private fun writeSnapshot(record: StoredSession): Boolean {
        val json = runCatching { gson.toJson(record) }.getOrElse { error ->
            SVHub.LOGGER.warn("Unable to serialize arcade session {}", record.sessionId, error)
            return false
        }
        if (json.length > MAX_JSON_CHARS) return false
        repeat(3) { attempt ->
            val ok = runCatching {
                AtomicFiles.writeUtf8(pathFor(record.sessionId), json)
                true
            }.getOrElse {
                SVHub.LOGGER.warn(
                    "Unable to save arcade session {} (attempt {})",
                    record.sessionId,
                    attempt + 1,
                    it
                )
                false
            }
            if (ok) return true
            try {
                Thread.sleep(50L shl attempt)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun pathFor(sessionId: String): Path {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sessionId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return root.resolve(digest + ".json")
    }

    private fun valid(record: StoredSession): Boolean {
        if (record.schema != 1) return false
        if (!record.sessionId.matches(Regex("^[A-Za-z0-9_.:-]{1,160}$"))) return false
        if (!record.gameId.matches(Regex("^[a-z0-9_]{1,64}$"))) return false
        if (!record.mode.matches(Regex("^[a-z0-9_]{1,64}$"))) return false
        if (record.createdAtEpochMs < 0L || record.savedAtEpochMs < 0L) return false
        if (record.seats.isEmpty() || record.seats.size > 8) return false
        if (record.seats.map { it.id }.toSet().size != record.seats.size) return false
        if (record.seats.any { it.id.isBlank() || it.id.length > 160 || it.name.length > 96 }) return false
        if (record.humanActions.any { (id, count) -> id.length > 160 || count !in 0..1_000_000 }) return false
        if (record.forfeited.any { it.length > 160 }) return false
        if (record.controllers.any { (id, state) -> id.length > 160 || state.generation < 0 }) return false
        if (record.reconnectRemainingMs.any { (id, remaining) -> id.length > 160 || remaining < 0 }) return false
        return record.state.size() > 0
    }

    private const val MAX_JSON_CHARS = 8 * 1024 * 1024
    private const val MAX_SESSION_FILES = 1024
    private const val SAVE_RETRY_MS = 5_000L
}
