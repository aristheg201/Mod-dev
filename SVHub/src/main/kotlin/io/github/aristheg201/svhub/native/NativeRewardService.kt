package io.github.aristheg201.svhub.native

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.util.AtomicFiles
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Computes reward policy off-thread. Only the final in-memory grant is marshalled back to the
 * server thread; NativeProfileStore then persists its immutable snapshot on its own I/O worker.
 */
object NativeRewardService {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val threadCounter = AtomicInteger()
    @Volatile private var executor: ThreadPoolExecutor? = null
    @Volatile private var rules: NativeRewardRules = NativeRewardRules()
    private lateinit var configPath: Path

    fun start(path: Path) {
        configPath = path
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) {
            runCatching { AtomicFiles.writeUtf8(path, gson.toJson(defaultJson())) }
                .onFailure { SVHub.LOGGER.warn("Unable to write native arcade reward config", it) }
        }
        rules = loadRules(path)
        if (executor == null) synchronized(this) {
            if (executor == null) {
                executor = ThreadPoolExecutor(
                    1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(256),
                    { task -> Thread(task, "SVHub-Reward-${threadCounter.incrementAndGet()}").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
                    ThreadPoolExecutor.AbortPolicy()
                )
            }
        }
    }

    fun enqueue(completion: NativeRewardCompletion) {
        val pool = executor ?: return
        val snapshotRules = rules
        try {
            pool.execute {
                val awards = runCatching { NativeRewardPolicy.calculate(completion, snapshotRules) }
                    .onFailure { SVHub.LOGGER.warn("Reward calculation failed for {}", completion.sessionId, it) }
                    .getOrNull() ?: return@execute
                val server = SVHubRuntime.server ?: return@execute
                server.execute {
                    for (award in awards) {
                        if (!award.eligible || (award.arcadeTokens <= 0 && award.gachaTickets <= 0)) continue
                        val changed = NativeProfileStore.mutate(award.playerId) { profile ->
                            profile.credit("arcade", award.arcadeTokens)
                            profile.credit("ticket", award.gachaTickets.toLong())
                        }
                        if (!changed) continue
                        server.playerList.getPlayer(award.playerId)?.let { player ->
                            val parts = buildList {
                                if (award.arcadeTokens > 0) add("+${award.arcadeTokens} Arcade Token")
                                if (award.gachaTickets > 0) add("+${award.gachaTickets} Gacha Ticket")
                            }
                            player.sendSystemMessage(Component.literal("SVHub reward • ${parts.joinToString(" • ")}"))
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Do not move reward calculation to the main thread under pressure.
            SVHub.LOGGER.warn("Native reward queue full; session {} reward was not queued", completion.sessionId)
        }
    }

    fun reload() {
        if (::configPath.isInitialized) rules = loadRules(configPath)
    }

    fun shutdown() {
        val pool = synchronized(this) { val p = executor; executor = null; p }
        pool?.shutdown()
        runCatching { pool?.awaitTermination(2, TimeUnit.SECONDS) }
        pool?.shutdownNow()
    }

    private fun loadRules(path: Path): NativeRewardRules = runCatching {
        val root = gson.fromJson(Files.readString(path), JsonObject::class.java) ?: JsonObject()
        val base = root.getAsJsonObject("base") ?: JsonObject()
        val anti = root.getAsJsonObject("antiFarm") ?: JsonObject()
        val modes = linkedMapOf<String, Double>()
        root.getAsJsonObject("modeMultipliers")?.entrySet()?.forEach { (k, v) ->
            runCatching { v.asDouble }.getOrNull()?.let { modes[k] = it.coerceIn(0.0, 10.0) }
        }
        NativeRewardRules(
            winTokens = base.long("winTokens", 50),
            drawTokens = base.long("drawTokens", 25),
            lossTokens = base.long("lossTokens", 15),
            winTickets = base.int("winTickets", 0),
            drawTickets = base.int("drawTickets", 0),
            lossTickets = base.int("lossTickets", 0),
            minimumHumanActions = anti.int("minimumHumanActions", 2),
            minimumDurationSeconds = anti.long("minimumDurationSeconds", 10),
            modeMultipliers = if (modes.isEmpty()) NativeRewardRules().modeMultipliers else modes
        )
    }.onFailure { SVHub.LOGGER.warn("Unable to load native arcade reward config; using safe defaults", it) }
        .getOrDefault(NativeRewardRules())

    private fun defaultJson() = JsonObject().apply {
        add("base", JsonObject().apply {
            addProperty("winTokens", 50); addProperty("drawTokens", 25); addProperty("lossTokens", 15)
            addProperty("winTickets", 0); addProperty("drawTickets", 0); addProperty("lossTickets", 0)
        })
        add("antiFarm", JsonObject().apply {
            addProperty("minimumHumanActions", 2)
            addProperty("minimumDurationSeconds", 10)
        })
        add("modeMultipliers", JsonObject().apply {
            addProperty("pvp", 1.0); addProperty("solo", 1.0)
            addProperty("bot_easy", 0.50); addProperty("bot_normal", 0.75); addProperty("bot_hard", 1.0)
        })
    }

    private fun JsonObject.long(key: String, fallback: Long) = runCatching { get(key)?.asLong ?: fallback }.getOrDefault(fallback)
    private fun JsonObject.int(key: String, fallback: Int) = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
}
