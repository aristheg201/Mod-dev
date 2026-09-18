package io.github.aristheg201.svhub.native

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.native.game.NativeBotAction
import io.github.aristheg201.svhub.native.game.NativeGameResult
import io.github.aristheg201.svhub.native.game.NativeGameSession
import io.github.aristheg201.svhub.native.game.NativeGameView
import io.github.aristheg201.svhub.native.game.NativeSeat
import net.minecraft.server.MinecraftServer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-confines every live minigame session to a serial actor running on a bounded worker pool.
 * Minecraft's server thread never calls NativeGameSession.act/tick/viewFor after registration.
 */
object NativeGameEngineRuntime {
    data class Update(
        val sessionId: String,
        val sourceSeatId: String?,
        val sourceBot: Boolean,
        val action: String?,
        val accepted: Boolean,
        val changed: Boolean,
        val message: String,
        val finished: Boolean,
        val winnerSeatId: String?
    )

    class Handle internal constructor(private val actor: Actor) {
        val sessionId: String get() = actor.sessionId
        val gameId: String get() = actor.gameId
        val seats: List<NativeSeat> get() = actor.seats
        val finished: Boolean get() = actor.finished
        val winnerSeatId: String? get() = actor.winnerSeatId
        fun viewFor(viewerId: String): NativeGameView? = actor.views[viewerId]
        fun viewJsonFor(viewerId: String): JsonElement? = actor.viewJson[viewerId]
        fun submitAction(seatId: String, action: String, args: Map<String, String>, bot: Boolean = false): Boolean =
            actor.submitAction(seatId, action, args, bot)
        fun submitBotCandidates(seatId: String, candidates: List<NativeBotAction>): Boolean =
            actor.submitBotCandidates(seatId, candidates)
        fun submitTick(nowMillis: Long): Boolean = actor.submitTick(nowMillis)
        fun snapshotState(): JsonObject = actor.stateJson.deepCopy()
        val snapshotEpochMs: Long get() = actor.stateEpochMs
        fun close() = actor.close()
    }

    private sealed interface Command {
        data class Act(val seatId: String, val action: String, val args: Map<String, String>, val bot: Boolean) : Command
        data class BotCandidates(val seatId: String, val candidates: List<NativeBotAction>) : Command
        data class Tick(val nowMillis: Long) : Command
    }

    internal class Actor(
        private val server: MinecraftServer,
        private val session: NativeGameSession,
        private val callback: (Update) -> Unit
    ) {
        val sessionId = session.sessionId
        val gameId = session.gameId
        val seats = session.seats.toList()
        private val queue = ArrayBlockingQueue<Command>(64)
        private val scheduled = AtomicBoolean(false)
        private val tickQueued = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        @Volatile var views: Map<String, NativeGameView> = captureViews()
        @Volatile var viewJson: Map<String, JsonElement> = serializeViews(views)
        @Volatile var finished: Boolean = session.finished
        @Volatile var winnerSeatId: String? = session.winnerSeatId
        @Volatile var stateJson: JsonObject = captureState(System.currentTimeMillis())
        @Volatile var stateEpochMs: Long = System.currentTimeMillis()
        @Volatile private var lastSnapshotAt: Long = stateEpochMs

        fun startBots() = scheduleBots()

        fun submitAction(seatId: String, action: String, args: Map<String, String>, bot: Boolean): Boolean =
            enqueue(Command.Act(seatId, action, args.toMap(), bot))

        fun submitBotCandidates(seatId: String, candidates: List<NativeBotAction>): Boolean =
            enqueue(Command.BotCandidates(seatId, candidates.toList()))

        private fun enqueue(command: Command): Boolean {
            if (closed.get() || !queue.offer(command)) return false
            scheduleDrain()
            return true
        }

        fun submitTick(nowMillis: Long): Boolean {
            if (closed.get()) return false
            if (!tickQueued.compareAndSet(false, true)) {
                scheduleDrain()
                return false
            }
            if (!queue.offer(Command.Tick(nowMillis))) {
                tickQueued.set(false)
                return false
            }
            scheduleDrain()
            return true
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            queue.clear(); tickQueued.set(false); NativeBotRuntime.forgetSession(sessionId)
        }

        private fun scheduleDrain() {
            if (!scheduled.compareAndSet(false, true)) return
            val pool = executor ?: run { scheduled.set(false); return }
            try {
                pool.execute(::drain)
            } catch (_: RejectedExecutionException) {
                scheduled.set(false)
            }
        }

        private fun drain() {
            try {
                var processed = 0
                while (!closed.get() && processed++ < MAX_COMMANDS_PER_SLICE) {
                    val command = queue.poll() ?: break
                    when (command) {
                        is Command.Act -> processAct(command)
                        is Command.BotCandidates -> processBotCandidates(command)
                        is Command.Tick -> { tickQueued.set(false); processTick(command) }
                    }
                }
            } catch (error: Throwable) {
                SVHub.LOGGER.error("Native session actor crashed: {} / {}", gameId, sessionId, error)
            } finally {
                scheduled.set(false)
                if (!closed.get() && queue.isNotEmpty()) scheduleDrain()
            }
        }

        private fun processAct(command: Command.Act) {
            val result = runCatching { session.act(command.seatId, command.action, command.args) }
                .getOrElse { NativeGameResult(false, false, "Game action failed") }
            afterMutation(command.seatId, command.bot, command.action, result)
        }

        private fun processBotCandidates(command: Command.BotCandidates) {
            var final = NativeGameResult(false, false, "Bot has no valid action")
            var action: String? = null
            for (candidate in command.candidates.take(MAX_BOT_CANDIDATES_TO_VALIDATE)) {
                val result = runCatching { session.act(command.seatId, candidate.action, candidate.args) }
                    .getOrElse { NativeGameResult(false, false, "Bot action failed") }
                if (!result.accepted) continue
                final = result; action = candidate.action; break
            }
            afterMutation(command.seatId, true, action, final)
        }

        private fun processTick(command: Command.Tick) {
            val changed = runCatching { session.tick(command.nowMillis) }.getOrElse {
                SVHub.LOGGER.warn("Native game tick failed for {} / {}", gameId, sessionId, it); false
            }
            val finishChanged = session.finished != finished
            val refreshForClock = command.nowMillis - lastSnapshotAt >= SNAPSHOT_REFRESH_MS
            if (changed || finishChanged || refreshForClock) refreshSnapshot()
            if (changed || finishChanged) {
                val update = Update(sessionId, null, false, null, true, changed, if (finished) "gui.svhub.game.finished" else "", finished, winnerSeatId)
                server.execute { callback(update) }
            }
            scheduleBots()
        }

        private fun afterMutation(sourceSeatId: String?, sourceBot: Boolean, action: String?, result: NativeGameResult) {
            if (result.changed || result.accepted || session.finished != finished) refreshSnapshot()
            scheduleBots()
            val update = Update(sessionId, sourceSeatId, sourceBot, action, result.accepted, result.changed, publicMessage(result), finished, winnerSeatId)
            server.execute { callback(update) }
        }

        private fun publicMessage(result: NativeGameResult): String = when {
            result.message.startsWith("gui.") -> result.message
            result.message.isBlank() -> ""
            result.accepted -> "gui.svhub.game.action_applied"
            else -> "gui.svhub.game.action_rejected"
        }

        private fun refreshSnapshot() {
            val now = System.currentTimeMillis()
            val captured = captureViews()
            val state = captureState(now)
            views = captured
            viewJson = serializeViews(captured)
            if (state.size() > 0) stateJson = state
            stateEpochMs = now
            finished = session.finished
            winnerSeatId = session.winnerSeatId
            lastSnapshotAt = now
        }

        private fun captureState(nowMillis: Long): JsonObject =
            runCatching { session.snapshotState(nowMillis) }
                .onFailure { error -> SVHub.LOGGER.warn("Native game snapshot failed for {} / {}", gameId, sessionId, error) }
                .getOrDefault(JsonObject())

        private fun captureViews(): Map<String, NativeGameView> = seats.associate { seat -> seat.id to session.viewFor(seat.id) }
        private fun serializeViews(captured: Map<String, NativeGameView>): Map<String, JsonElement> =
            captured.mapValues { (_, view) -> gson.toJsonTree(view) }

        private fun scheduleBots() {
            if (closed.get() || finished) return
            NativeBotRuntime.considerSnapshot(sessionId, seats, views, { _, seatId, candidates ->
                submitBotCandidates(seatId, candidates)
            })
        }
    }

    private val gson = Gson()
    private val actors = ConcurrentHashMap<String, Actor>()
    private val threadCounter = AtomicInteger()
    @Volatile private var executor: ThreadPoolExecutor? = null

    fun start() {
        if (executor != null) return
        synchronized(this) {
            if (executor != null) return
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val max = minOf(4, maxOf(2, cores / 2))
            executor = ThreadPoolExecutor(
                2, max, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(512),
                { task -> Thread(task, "SVHub-Game-${threadCounter.incrementAndGet()}").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
                ThreadPoolExecutor.AbortPolicy()
            ).apply { allowCoreThreadTimeOut(true) }
        }
    }

    fun register(server: MinecraftServer, session: NativeGameSession, callback: (Update) -> Unit): Handle {
        val actor = Actor(server, session, callback)
        check(actors.putIfAbsent(session.sessionId, actor) == null) { "Duplicate native session ${session.sessionId}" }
        actor.startBots()
        return Handle(actor)
    }

    fun unregister(sessionId: String) {
        actors.remove(sessionId)?.close()
    }

    fun shutdown() {
        actors.values.forEach(Actor::close); actors.clear()
        val pool = synchronized(this) { val current = executor; executor = null; current }
        pool?.shutdownNow()
    }

    private const val MAX_COMMANDS_PER_SLICE = 32
    private const val MAX_BOT_CANDIDATES_TO_VALIDATE = 16
    private const val SNAPSHOT_REFRESH_MS = 500L
}
