package io.github.aristheg201.svarcade.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.SVArcadeRuntime
import io.github.aristheg201.svarcade.util.AtomicFiles
import io.github.aristheg201.svarcade.native.store.CosmeticAccount
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class DurableMutationResult { APPLIED, ALREADY_APPLIED, REJECTED }

object NativeProfileStore {
    private data class Waiter(val revision: Long, val result: DurableMutationResult, val callback: (DurableMutationResult) -> Unit)

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val rankIndex = ConcurrentHashMap<UUID, NativeProfile>()
    fun leaderboard(game: String): List<Pair<String, Int>> = rankIndex.values.mapNotNull { p -> p.stats[game]?.takeIf { it.rankedPlayed > 0 }?.let { p.displayName to it.rating } }.sortedByDescending { it.second }.take(20)
    private val profiles = ConcurrentHashMap<UUID, NativeProfile>()
    private val loaded = ConcurrentHashMap.newKeySet<UUID>()
    private val offlineSince = ConcurrentHashMap<UUID, Long>()
    private val persistedRevision = ConcurrentHashMap<UUID, Long>()
    private val latestSnapshots = ConcurrentHashMap<UUID, NativeProfile>()
    private val queued = ConcurrentHashMap.newKeySet<UUID>()
    private val retryAfter = ConcurrentHashMap<UUID, Long>()
    private val waiters = ConcurrentHashMap<UUID, MutableList<Waiter>>()
    private val closed = AtomicBoolean(true)
    @Volatile private var io: ExecutorService? = null
    private lateinit var root: Path

    fun start(path: Path) {
        root = path
        Files.createDirectories(root)
        closed.set(false)
        synchronized(this) {
            if (io == null || io?.isShutdown == true) {
                io = Executors.newSingleThreadExecutor { task -> Thread(task, "SVArcade-NativeProfile-IO").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
            }
        }
        io?.execute {
            Files.list(root).use { files -> files.filter { it.fileName.toString().endsWith(".json") }.forEach { file ->
                runCatching { val id=UUID.fromString(file.fileName.toString().removeSuffix(".json"));rankIndex.putIfAbsent(id,load(id)) }
            } }
        }
    }

    fun onJoin(player: ServerPlayer, ready: (ServerPlayer) -> Unit) {
        val id = player.uuid
        offlineSince.remove(id)
        profiles[id]?.let { loaded += id; ready(player); return }
        val pool = io ?: return
        try {
            pool.execute {
                val profile = runCatching { load(id) }.getOrElse {
                    SVArcade.LOGGER.error("Native profile load failed; refusing to replace existing entitlements for {}", id, it)
                    return@execute
                }
                val server = SVArcadeRuntime.server ?: return@execute
                server.execute {
                    val live = server.playerList.getPlayer(id)
                    if (live != null) {
                        profile.displayName = live.gameProfile.name
                        profiles[id] = sanitize(profile)
                        rankIndex[id] = snapshot(profile)
                        persistedRevision[id] = profile.revision
                        loaded += id
                        offlineSince.remove(id)
                        ready(live)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            SVArcade.LOGGER.warn("Native profile executor rejected join load for {}", id)
        }
    }

    fun onDisconnect(player: ServerPlayer) {
        val id = player.uuid
        profiles[id]?.let { scheduleSave(id, snapshot(it)) }
        loaded.remove(id)
        offlineSince[id] = System.currentTimeMillis()
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        retryAfter.entries.toList().forEach { (id, retryAt) ->
            if (now >= retryAt && latestSnapshots.containsKey(id)) { retryAfter.remove(id, retryAt); queueWriter(id) }
        }
        offlineSince.entries.toList().forEach { (id, since) ->
            if (now - since < OFFLINE_RETENTION_MS || id in loaded) return@forEach
            val profile = profiles.remove(id)
            if (profile != null) scheduleSave(id, snapshot(profile))
            offlineSince.remove(id, since)
        }
    }

    fun isLoaded(id: UUID) = id in loaded
    fun get(id: UUID): NativeProfile? = profiles[id]

    fun mutate(id: UUID, mutation: (NativeProfile) -> Unit): Boolean {
        val p = profiles[id] ?: return false
        mutation(p)
        p.revision = nextRevision(p.revision)
        p.touch()
        scheduleSave(id, snapshot(p))
        return true
    }

    fun hasTransaction(id: UUID, transactionId: String): Boolean = profiles[id]?.appliedTransactions?.containsKey(transactionId) == true

    fun mutateDurable(id: UUID, mutation: (NativeProfile) -> Unit, completion: () -> Unit) {
        val p = requireNotNull(profiles[id]) { "Profile must be loaded" }
        mutation(p)
        p.revision = nextRevision(p.revision)
        p.touch()
        addWaiter(id, Waiter(p.revision, DurableMutationResult.APPLIED) { completion() })
        scheduleSave(id, snapshot(p))
    }

    fun isTransactionDurable(id: UUID, transactionId: String): Boolean {
        val revision = profiles[id]?.appliedTransactions?.get(transactionId) ?: return false
        return (persistedRevision[id] ?: -1L) >= revision
    }

    fun mutateDurableOnce(
        id: UUID,
        transactionId: String,
        mutation: (NativeProfile) -> Boolean,
        completion: (DurableMutationResult) -> Unit
    ): Boolean {
        if (transactionId.isBlank() || transactionId.length > 160) return false
        val p = profiles[id] ?: return false
        val existingRevision = p.appliedTransactions[transactionId]
        if (existingRevision != null) {
            if ((persistedRevision[id] ?: -1L) >= existingRevision) completion(DurableMutationResult.ALREADY_APPLIED)
            else {
                addWaiter(id, Waiter(existingRevision, DurableMutationResult.ALREADY_APPLIED, completion))
                scheduleSave(id, snapshot(p))
            }
            return true
        }
        if (!mutation(p)) { completion(DurableMutationResult.REJECTED); return true }
        p.revision = nextRevision(p.revision)
        p.appliedTransactions[transactionId] = p.revision
        trimTransactions(p)
        p.touch()
        val requiredRevision = p.revision
        addWaiter(id, Waiter(requiredRevision, DurableMutationResult.APPLIED, completion))
        scheduleSave(id, snapshot(p))
        return true
    }

    fun shutdown() {
        if (!closed.compareAndSet(false, true)) return
        profiles.forEach { (id, p) -> latestSnapshots.compute(id) { _, old -> newest(old, snapshot(p)) } }
        latestSnapshots.keys.forEach(::queueWriter)
        val pool = synchronized(this) { val current = io; io = null; current }
        pool?.shutdown()
        try { if (pool != null && !pool.awaitTermination(8, TimeUnit.SECONDS)) pool.shutdownNow() }
        catch (_: InterruptedException) { pool?.shutdownNow(); Thread.currentThread().interrupt() }
        profiles.clear(); loaded.clear(); offlineSince.clear(); persistedRevision.clear(); latestSnapshots.clear(); queued.clear(); retryAfter.clear(); waiters.clear()
    }

    private fun load(id: UUID): NativeProfile {
        val path = root.resolve("$id.json")
        if (!Files.exists(path)) return NativeProfile()
        return runCatching {
            val o = gson.fromJson(Files.readString(path), JsonObject::class.java) ?: return@runCatching NativeProfile()
            val p = NativeProfile(
                schema = 4, displayName = o.get("displayName")?.asString ?: id.toString().take(8),
                revision = o.number("revision"),
                arcadeTokens = o.number("arcadeTokens"),
                gachaTickets = o.number("gachaTickets").toInt(),
                pity = linkedMapOf(), stats = linkedMapOf(), appliedTransactions = linkedMapOf(),
                lastUpdatedEpochMs = o.number("lastUpdatedEpochMs", System.currentTimeMillis())
            )
            o.getAsJsonObject("pity")?.entrySet()?.forEach { (k, v) -> runCatching { v.asInt }.getOrNull()?.let { p.pity[k] = it } }
            o.getAsJsonObject("stats")?.entrySet()?.forEach { (k, v) ->
                val st = runCatching { v.asJsonObject }.getOrNull() ?: return@forEach
                p.stats[k] = NativeGameStats(st.int("played"), st.int("wins"), st.int("losses"), st.int("draws"), st.int("rankedPlayed"), st.int("rating"), st.getAsJsonArray("history")?.map { it.asString }?.take(20)?.toMutableList() ?: mutableListOf())
            }
            o.getAsJsonObject("appliedTransactions")?.entrySet()?.forEach { (k, v) ->
                runCatching { v.asLong }.getOrNull()?.takeIf { it > 0L }?.let { p.appliedTransactions[k.take(160)] = it }
            }
            if (o.has("cosmetics")) p.cosmetics = requireNotNull(gson.fromJson(o.get("cosmetics"), CosmeticAccount::class.java))
            require(p.cosmetics.schema == 1) { "Unsupported cosmetics schema" }
            p
        }.getOrThrow()
    }

    private fun snapshot(p: NativeProfile) = NativeProfile(
        schema = 4, displayName = p.displayName, revision = p.revision, arcadeTokens = p.arcadeTokens, gachaTickets = p.gachaTickets,
        pity = p.pity.toMutableMap(),
        stats = p.stats.mapValuesTo(linkedMapOf()) { (_, s) -> s.copy(history = (s.history ?: mutableListOf()).toMutableList()) },
        appliedTransactions = p.appliedTransactions.toMutableMap(),
        lastUpdatedEpochMs = p.lastUpdatedEpochMs, cosmetics = p.cosmetics.copyDeep()
    )

    private fun scheduleSave(id: UUID, profileSnapshot: NativeProfile) {
        if (closed.get()) return
        rankIndex[id] = profileSnapshot
        latestSnapshots.compute(id) { _, old -> newest(old, profileSnapshot) }
        val retryAt = retryAfter[id]
        if (retryAt == null || System.currentTimeMillis() >= retryAt) queueWriter(id)
    }

    private fun queueWriter(id: UUID) {
        val pool = io ?: return
        if (!queued.add(id)) return
        try {
            pool.execute {
                try {
                    while (true) {
                        val profile = latestSnapshots.remove(id) ?: break
                        val ok = writeSnapshot(id, profile)
                        if (!ok) {
                            latestSnapshots.compute(id) { _, old -> newest(old, profile) }
                            retryAfter[id] = System.currentTimeMillis() + SAVE_RETRY_MS
                            break
                        }
                        retryAfter.remove(id)
                        persistedRevision.compute(id) { _, old -> maxOf(old ?: 0L, profile.revision) }
                        completeWaiters(id, profile.revision)
                        if (!latestSnapshots.containsKey(id)) break
                    }
                } finally {
                    queued.remove(id)
                    val retryAt = retryAfter[id]
                    if (latestSnapshots.containsKey(id) && !closed.get() && (retryAt == null || System.currentTimeMillis() >= retryAt)) queueWriter(id)
                }
            }
        } catch (_: RejectedExecutionException) { queued.remove(id) }
    }

    private fun writeSnapshot(id: UUID, profile: NativeProfile): Boolean {
        val json = runCatching { gson.toJson(profile) }.getOrElse { error -> SVArcade.LOGGER.warn("Unable to serialize native profile {}", id, error); return false }
        repeat(3) { attempt ->
            val ok = runCatching { AtomicFiles.writeUtf8(root.resolve("$id.json"), json); true }.getOrElse {
                SVArcade.LOGGER.warn("Unable to save native profile {} (attempt {})", id, attempt + 1, it); false
            }
            if (ok) return true
            try { Thread.sleep(50L shl attempt) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
        }
        return false
    }

    private fun addWaiter(id: UUID, waiter: Waiter) {
        val list = waiters.computeIfAbsent(id) { mutableListOf() }
        synchronized(list) { list += waiter }
    }

    private fun completeWaiters(id: UUID, revision: Long) {
        val list = waiters[id] ?: return
        val ready = mutableListOf<Waiter>()
        synchronized(list) {
            val iterator = list.iterator()
            while (iterator.hasNext()) { val waiter = iterator.next(); if (waiter.revision <= revision) { ready += waiter; iterator.remove() } }
            if (list.isEmpty()) waiters.remove(id, list)
        }
        if (ready.isEmpty()) return
        SVArcadeRuntime.server?.execute { ready.forEach { runCatching { it.callback(it.result) }.onFailure { error -> SVArcade.LOGGER.warn("Durable profile callback failed for {}", id, error) } } }
    }

    private fun sanitize(p: NativeProfile): NativeProfile {
        p.schema = 4
        p.revision = p.revision.coerceAtLeast(0L)
        p.arcadeTokens = p.arcadeTokens.coerceIn(0, NativeProfile.MAX_BALANCE)
        p.gachaTickets = p.gachaTickets.coerceIn(0, 1_000_000)
        p.pity = p.pity.mapValuesTo(linkedMapOf()) { (_, v) -> v.coerceIn(0, 10_000) }
        p.stats.values.forEach { if (it.history == null) it.history = mutableListOf() }
        trimTransactions(p)
        return p
    }

    private fun trimTransactions(p: NativeProfile) {
        val extra = p.appliedTransactions.size - MAX_TRANSACTION_HISTORY
        if (extra <= 0) return
        p.appliedTransactions.entries.sortedBy { it.value }.take(extra).forEach { p.appliedTransactions.remove(it.key) }
    }

    private fun newest(a: NativeProfile?, b: NativeProfile): NativeProfile = if (a == null || b.revision >= a.revision) b else a
    private fun nextRevision(current: Long) = if (current == Long.MAX_VALUE) current else current + 1L
    private fun JsonObject.number(key: String, fallback: Long = 0L) = runCatching { get(key)?.asLong ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int = 0) = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private const val MAX_TRANSACTION_HISTORY = 4096
    private const val OFFLINE_RETENTION_MS = 10 * 60 * 1000L
    private const val SAVE_RETRY_MS = 5_000L
}
