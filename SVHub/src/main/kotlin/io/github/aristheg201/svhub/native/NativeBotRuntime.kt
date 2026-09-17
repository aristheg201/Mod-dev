package io.github.aristheg201.svhub.native

import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.native.game.NativeBotAction
import io.github.aristheg201.svhub.native.game.NativeBotDifficulty
import io.github.aristheg201.svhub.native.game.NativeBotPlanner
import io.github.aristheg201.svhub.native.game.NativeGameView
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.native.game.TftBotPlanner
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object NativeBotRuntime {
    private data class Key(val sessionId: String, val seatId: String)
    private val pending = ConcurrentHashMap.newKeySet<Key>()
    private val nextThinkAt = ConcurrentHashMap<Key, Long>()
    private val threadCounter = AtomicInteger()
    @Volatile private var executor: ThreadPoolExecutor? = null

    fun start() {
        if (executor != null) return
        synchronized(this) {
            if (executor != null) return
            executor = ThreadPoolExecutor(
                1, 2, 20L, TimeUnit.SECONDS, ArrayBlockingQueue(128),
                { task -> Thread(task, "SVHub-Bot-${threadCounter.incrementAndGet()}").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
                ThreadPoolExecutor.AbortPolicy()
            ).apply { allowCoreThreadTimeOut(true) }
        }
    }

    fun considerSnapshot(
        sessionId: String,
        seats: List<NativeSeat>,
        views: Map<String, NativeGameView>,
        apply: (sessionId: String, seatId: String, candidates: List<NativeBotAction>) -> Unit,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val pool = executor ?: return
        for (seat in seats) {
            if (!seat.anyBot) continue
            val difficulty = seat.botDifficulty ?: NativeBotDifficulty.NORMAL
            val key = Key(sessionId, seat.id)
            val due = nextThinkAt[key] ?: 0L
            if (nowMillis < due || key in pending) continue
            val view = views[seat.id] ?: continue
            val shouldThink = if (view.gameId == "tft") TftBotPlanner.shouldThink(view) else {
                val planningSeat = if (seat.bot) seat else seat.copy(bot = true)
                NativeBotPlanner.shouldThink(view, planningSeat)
            }
            if (!shouldThink) continue

            pending += key
            nextThinkAt[key] = nowMillis + thinkDelayMillis(difficulty)
            try {
                pool.execute {
                    val plan = runCatching {
                        if (view.gameId == "tft") TftBotPlanner.plan(view, difficulty) else NativeBotPlanner.plan(view, difficulty)
                    }
                    pending.remove(key)
                    plan.onSuccess { result -> if (result.candidates.isNotEmpty()) apply(sessionId, seat.id, result.candidates) }
                        .onFailure { error -> SVHub.LOGGER.warn("Native bot planner failed for {} / {}", view.gameId, sessionId, error) }
                }
            } catch (_: RejectedExecutionException) {
                pending.remove(key)
                nextThinkAt[key] = nowMillis + 1_000L
            }
        }
    }

    fun forgetSession(sessionId: String) {
        pending.removeIf { it.sessionId == sessionId }
        nextThinkAt.keys.removeIf { it.sessionId == sessionId }
    }

    fun shutdown() {
        val pool = synchronized(this) { val current = executor; executor = null; current }
        pending.clear(); nextThinkAt.clear(); pool?.shutdownNow()
    }

    private fun thinkDelayMillis(difficulty: NativeBotDifficulty): Long = when (difficulty) {
        NativeBotDifficulty.EASY -> 650L
        NativeBotDifficulty.NORMAL -> 350L
        NativeBotDifficulty.HARD -> 180L
    }
}
