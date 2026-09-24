package io.github.aristheg201.svarcade.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.SVArcadeRuntime
import io.github.aristheg201.svarcade.util.AtomicFiles
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object NativeRewardService {
    private data class JournalEntry(
        val playerId: String, val arcadeTokens: Long, val gachaTickets: Int,
        val outcome: String, val eligible: Boolean, val reason: String
    )
    private data class JournalRecord(
        val schema: Int = 1, val sessionId: String, val gameId: String, val mode: String,
        val createdAtEpochMs: Long, val entries: List<JournalEntry>
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val threadCounter = AtomicInteger()
    private val pending = ConcurrentHashMap<String, JournalRecord>()
    @Volatile private var executor: ThreadPoolExecutor? = null
    @Volatile private var rules: NativeRewardRules = NativeRewardRules()
    private lateinit var configPath: Path
    private lateinit var journalRoot: Path

    fun start(path: Path) {
        configPath = path
        journalRoot = path.parent.resolve("reward-journal")
        Files.createDirectories(path.parent)
        Files.createDirectories(journalRoot)
        if (!Files.exists(path)) runCatching { AtomicFiles.writeUtf8(path, gson.toJson(defaultJson())) }
            .onFailure { SVArcade.LOGGER.warn("Unable to write native arcade reward config", it) }
        rules = loadRules(path)
        if (executor == null) synchronized(this) {
            if (executor == null) executor = ThreadPoolExecutor(
                1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(256),
                { task -> Thread(task, "SVArcade-Reward-${threadCounter.incrementAndGet()}").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
                ThreadPoolExecutor.AbortPolicy()
            )
        }
        loadPending()
    }

    fun enqueue(completion: NativeRewardCompletion, journalDurable: (Boolean) -> Unit): Boolean {
        pending[completion.sessionId]?.let { existing ->
            SVArcadeRuntime.server?.execute { journalDurable(true); applyRecord(existing) }
            return true
        }
        val pool = executor ?: return false
        val snapshotRules = rules
        val awards = runCatching { NativeRewardPolicy.calculate(completion, snapshotRules) }
            .onFailure { SVArcade.LOGGER.warn("Reward calculation failed for {}", completion.sessionId, it) }
            .getOrNull() ?: return false
        val participants = completion.participants.associateBy { it.playerId }
        val record = JournalRecord(
            sessionId = completion.sessionId, gameId = completion.gameId, mode = completion.mode,
            createdAtEpochMs = System.currentTimeMillis(),
            entries = awards.map { award ->
                val participant = participants[award.playerId]
                JournalEntry(award.playerId.toString(), award.arcadeTokens, award.gachaTickets, participant?.outcome?.name ?: NativeRewardOutcome.LOSS.name, award.eligible, award.reason)
            }
        )
        try {
            pool.execute {
                val ok = runCatching { AtomicFiles.writeUtf8(journalPath(record.sessionId), gson.toJson(record)); true }
                    .onFailure { SVArcade.LOGGER.error("Unable to persist reward journal {}", record.sessionId, it) }.getOrDefault(false)
                if (ok) pending[record.sessionId] = record
                SVArcadeRuntime.server?.execute {
                    journalDurable(ok)
                    if (ok) applyRecord(record)
                }
            }
            return true
        } catch (_: RejectedExecutionException) {
            SVArcade.LOGGER.warn("Native reward journal queue full for {}", completion.sessionId)
            return false
        }
    }

    fun recoverPlayer(playerId: UUID) {
        pending.values.filter { record -> record.entries.any { it.playerId == playerId.toString() } }.forEach(::applyRecord)
    }

    fun reload() { if (::configPath.isInitialized) rules = loadRules(configPath) }

    fun shutdown() {
        val pool = synchronized(this) { val p = executor; executor = null; p }
        pool?.shutdown()
        runCatching { pool?.awaitTermination(5, TimeUnit.SECONDS) }
        pool?.shutdownNow()
        pending.clear()
    }

    private fun applyRecord(record: JournalRecord) {
        record.entries.forEach { entry ->
            val id = runCatching { UUID.fromString(entry.playerId) }.getOrNull() ?: return@forEach
            if (NativeProfileStore.get(id) == null) return@forEach
            val tx = "reward:${record.sessionId}"
            if (NativeProfileStore.hasTransaction(id, tx)) { tryFinalize(record); return@forEach }
            NativeCosmeticService.reward(id, tx, if (entry.eligible) entry.arcadeTokens else 0L) {
            NativeProfileStore.mutateDurableOnce(id, tx, mutation = { profile ->
                if (entry.eligible) {
                    profile.credit("ticket", entry.gachaTickets.toLong())
                }
                val stats = profile.stats.getOrPut(record.gameId) { NativeGameStats() }
                stats.played++
                when (runCatching { NativeRewardOutcome.valueOf(entry.outcome) }.getOrDefault(NativeRewardOutcome.LOSS)) {
                    NativeRewardOutcome.WIN -> stats.wins++
                    NativeRewardOutcome.DRAW -> stats.draws++
                    NativeRewardOutcome.LOSS, NativeRewardOutcome.FORFEIT -> stats.losses++
                }
                true
            }) { result ->
                if (result == DurableMutationResult.APPLIED && entry.eligible && (entry.arcadeTokens > 0 || entry.gachaTickets > 0)) notifyReward(id, entry)
                tryFinalize(record)
                recoverPlayer(id)
            }
            }
        }
        tryFinalize(record)
    }

    private fun tryFinalize(record: JournalRecord) {
        val allDurable = record.entries.all { entry ->
            val id = runCatching { UUID.fromString(entry.playerId) }.getOrNull() ?: return@all false
            NativeProfileStore.isTransactionDurable(id, "reward:${record.sessionId}")
        }
        if (!allDurable) return
        val pool = executor ?: return
        try {
            pool.execute {
                val deleted = runCatching { Files.deleteIfExists(journalPath(record.sessionId)); true }
                    .onFailure { SVArcade.LOGGER.warn("Unable to delete completed reward journal {}", record.sessionId, it) }.getOrDefault(false)
                if (deleted) pending.remove(record.sessionId, record)
            }
        } catch (_: RejectedExecutionException) { }
    }

    private fun notifyReward(id: UUID, entry: JournalEntry) {
        val player = SVArcadeRuntime.server?.playerList?.getPlayer(id) ?: return
        val message = when {
            entry.arcadeTokens > 0 && entry.gachaTickets > 0 ->
                Component.translatable("message.svarcade.reward.both", entry.arcadeTokens, entry.gachaTickets)
            entry.arcadeTokens > 0 ->
                Component.translatable("message.svarcade.reward.tokens", entry.arcadeTokens)
            entry.gachaTickets > 0 ->
                Component.translatable("message.svarcade.reward.tickets", entry.gachaTickets)
            else -> null
        }
        if (message != null) player.sendSystemMessage(message)
    }

    private fun loadPending() {
        if (!::journalRoot.isInitialized || !Files.isDirectory(journalRoot)) return
        runCatching {
            Files.list(journalRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }.forEach { file ->
                    runCatching { gson.fromJson(Files.readString(file), JournalRecord::class.java) }
                        .onFailure { SVArcade.LOGGER.warn("Unable to read reward journal {}", file, it) }
                        .getOrNull()?.takeIf { it.sessionId.isNotBlank() && it.entries.isNotEmpty() }?.let { pending[it.sessionId] = it }
                }
            }
        }.onFailure { SVArcade.LOGGER.warn("Unable to scan reward journal directory", it) }
    }

    private fun journalPath(sessionId: String): Path = journalRoot.resolve(sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private fun loadRules(path: Path): NativeRewardRules = runCatching {
        val root = gson.fromJson(Files.readString(path), JsonObject::class.java) ?: JsonObject()
        val base = root.getAsJsonObject("base") ?: JsonObject()
        val anti = root.getAsJsonObject("antiFarm") ?: JsonObject()
        val modes = linkedMapOf<String, Double>()
        root.getAsJsonObject("modeMultipliers")?.entrySet()?.forEach { (k, v) -> runCatching { v.asDouble }.getOrNull()?.let { modes[k] = it.coerceIn(0.0, 10.0) } }
        val placements = linkedMapOf<Int, Double>()
        root.getAsJsonObject("tftPlacementMultipliers")?.entrySet()?.forEach { (k, v) ->
            val place = k.toIntOrNull() ?: return@forEach
            if (place in 1..8) runCatching { v.asDouble }.getOrNull()?.let { placements[place] = it.coerceIn(0.0, 10.0) }
        }
        NativeRewardRules(
            winTokens = base.long("winTokens", 50), drawTokens = base.long("drawTokens", 25), lossTokens = base.long("lossTokens", 15),
            winTickets = base.int("winTickets", 0), drawTickets = base.int("drawTickets", 0), lossTickets = base.int("lossTickets", 0),
            minimumHumanActions = anti.int("minimumHumanActions", 2), minimumDurationSeconds = anti.long("minimumDurationSeconds", 10),
            modeMultipliers = if (modes.isEmpty()) NativeRewardRules().modeMultipliers else modes,
            tftPlacementMultipliers = if (placements.isEmpty()) NativeRewardRules().tftPlacementMultipliers else placements
        )
    }.onFailure { SVArcade.LOGGER.warn("Unable to load native arcade reward config; using safe defaults", it) }.getOrDefault(NativeRewardRules())

    private fun defaultJson() = JsonObject().apply {
        add("base", JsonObject().apply { addProperty("winTokens", 50); addProperty("drawTokens", 25); addProperty("lossTokens", 15); addProperty("winTickets", 0); addProperty("drawTickets", 0); addProperty("lossTickets", 0) })
        add("antiFarm", JsonObject().apply { addProperty("minimumHumanActions", 2); addProperty("minimumDurationSeconds", 10) })
        add("modeMultipliers", JsonObject().apply { addProperty("pvp", 1.0); addProperty("solo", 1.0); addProperty("bot_easy", 0.50); addProperty("bot_normal", 0.75); addProperty("bot_hard", 1.0) })
        add("tftPlacementMultipliers", JsonObject().apply { addProperty("1", 1.50); addProperty("2", 1.25); addProperty("3", 1.10); addProperty("4", 1.00); addProperty("5", 0.80); addProperty("6", 0.70); addProperty("7", 0.60); addProperty("8", 0.50) })
    }

    private fun JsonObject.long(key: String, fallback: Long) = runCatching { get(key)?.asLong ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int) = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
}
