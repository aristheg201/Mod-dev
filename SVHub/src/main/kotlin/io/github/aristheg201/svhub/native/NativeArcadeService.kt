package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.*
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NativeArcadeService {
    private val BOT_AND_PVP = setOf("bot_easy", "bot_normal", "bot_hard", "pvp")

    data class GameDef(val id: String, val title: String, val icon: String, val modes: Set<String>)
    data class Result(val ok: Boolean, val message: String, val changedPlayers: Set<UUID> = emptySet())

    private data class SessionMeta(
        val mode: String,
        val createdAtEpochMs: Long = System.currentTimeMillis(),
        val humanActions: MutableMap<UUID, Int> = linkedMapOf(),
        val forfeited: MutableSet<UUID> = linkedSetOf()
    )

    val games = listOf(
        GameDef("chess", "Pokémon Chess", "chess", BOT_AND_PVP),
        GameDef("xiangqi", "Cờ Tướng", "xiangqi", BOT_AND_PVP),
        GameDef("ludo", "Cờ Cá Ngựa", "ludo", BOT_AND_PVP),
        GameDef("uno", "UNO", "uno", BOT_AND_PVP),
        GameDef("pokecards", "PokéDraft Cards", "cards", BOT_AND_PVP),
        GameDef("tft", "Pokémon TFT", "tft", BOT_AND_PVP),
        GameDef("tower_defense", "Pokémon Tower Defense", "tower_defense", setOf("solo", "bot_easy", "bot_normal", "bot_hard"))
    )

    private val sessions = linkedMapOf<String, NativeGameEngineRuntime.Handle>()
    private val active = linkedMapOf<UUID, String>()
    private val queues = games.associate { it.id to ArrayDeque<UUID>() }.toMutableMap()
    private val rewarded = hashSetOf<String>()
    private val finishedAt = hashMapOf<String, Long>()
    private val meta = hashMapOf<String, SessionMeta>()
    private val asyncMessages = ConcurrentHashMap<UUID, String>()

    fun start(root: Path) {
        TftSetRegistry.start(root.resolve("tft"))
        NativeBotRuntime.start()
        NativeGameEngineRuntime.start()
        NativeRewardService.start(root.resolve("rewards.json"))
    }

    fun lobbyState(player: ServerPlayer) = JsonObject().apply {
        addProperty("module", "arcade")
        add("wallet", NativeSkinService.walletJson(NativeProfileStore.get(player.uuid) ?: NativeProfile()))
        add("games", JsonArray().also { arr ->
            games.forEach { d ->
                arr.add(JsonObject().apply {
                    addProperty("id", d.id); addProperty("title", d.title); addProperty("icon", d.icon)
                    add("modes", JsonArray().also { a -> d.modes.forEach(a::add) })
                    addProperty("queued", queues[d.id]?.contains(player.uuid) == true)
                    addProperty("queueSize", queues[d.id]?.size ?: 0)
                    addProperty("queueTarget", if (d.id == "tft") 8 else if ("pvp" in d.modes) 2 else 1)
                })
            }
        })
        active[player.uuid]?.let { sid -> sessions[sid]?.let { s -> addProperty("activeGame", s.gameId); addProperty("activeSession", s.sessionId) } }
        add("stats", JsonObject().also { out ->
            NativeProfileStore.get(player.uuid)?.stats?.forEach { (id, s) ->
                out.add(id, JsonObject().apply {
                    addProperty("played", s.played); addProperty("wins", s.wins); addProperty("losses", s.losses); addProperty("draws", s.draws)
                })
            }
        })
        addProperty("engine", "async-session-actors")
        addProperty("bots", "async-worker")
        addProperty("rewards", "async-policy")
    }

    fun gameState(player: ServerPlayer) = JsonObject().apply {
        addProperty("module", "game")
        add("wallet", NativeSkinService.walletJson(NativeProfileStore.get(player.uuid) ?: NativeProfile()))
        val handle = active[player.uuid]?.let(sessions::get)
        val view = handle?.viewJsonFor(player.uuid.toString())
        if (handle == null || view == null) addProperty("empty", true) else {
            addProperty("empty", false)
            add("view", view)
            meta[handle.sessionId]?.let { addProperty("mode", it.mode) }
        }
    }

    fun start(player: ServerPlayer, gameId: String, requestedMode: String): Result {
        val def = games.firstOrNull { it.id == gameId } ?: return Result(false, "Game không tồn tại.")
        if (sessions.size >= MAX_SESSIONS) return Result(false, "Arcade đang đạt giới hạn session; hãy thử lại sau.")
        val mode = if (requestedMode == "bot") "bot_normal" else requestedMode
        if (mode !in def.modes) return Result(false, "Mode không hợp lệ.")
        if (active.containsKey(player.uuid)) return Result(false, "Bạn đang có một ván chưa kết thúc.")
        queues.values.forEach { it.remove(player.uuid) }

        if (mode == "pvp") {
            val q = queues.getValue(gameId)
            if (gameId == "tft") {
                if (!q.contains(player.uuid)) q.addLast(player.uuid)
                val ready = mutableListOf<ServerPlayer>()
                val retained = ArrayDeque<UUID>()
                while (q.isNotEmpty()) {
                    val id = q.removeFirst()
                    val live = player.server.playerList.getPlayer(id)
                    if (live == null || active.containsKey(id) || ready.any { it.uuid == id }) continue
                    if (ready.size < 8) ready += live else retained += id
                }
                q.addAll(retained)
                if (ready.size < 8) {
                    ready.forEach { if (!q.contains(it.uuid)) q.addLast(it.uuid) }
                    return Result(true, "Pokémon TFT PvP: ${ready.size}/8 trainers trong hàng chờ.", ready.map { it.uuid }.toSet())
                }
                val handle = register(player.server, create(gameId, ready.map(::realSeat)), mode)
                return Result(true, "Pokémon TFT đã đủ 8 trainers.", realPlayers(handle))
            }
            while (q.isNotEmpty()) {
                val oid = q.removeFirst()
                val op = player.server.playerList.getPlayer(oid) ?: continue
                if (oid == player.uuid || active.containsKey(oid)) continue
                val handle = register(player.server, create(gameId, listOf(realSeat(player), realSeat(op))), mode)
                return Result(true, "Đã ghép trận với ${op.gameProfile.name}.", realPlayers(handle))
            }
            q.addLast(player.uuid)
            return Result(true, "Đã vào hàng chờ ${def.title} PvP.", setOf(player.uuid))
        }

        val difficulty = difficultyFor(mode)
        val seats = when (gameId) {
            "ludo" -> listOf(realSeat(player), botSeat("Blue", difficulty), botSeat("Green", difficulty), botSeat("Yellow", difficulty))
            "uno" -> listOf(realSeat(player), botSeat("UNO Bot A", difficulty), botSeat("UNO Bot B", difficulty), botSeat("UNO Bot C", difficulty))
            "tft" -> buildList {
                add(realSeat(player))
                repeat(7) { index -> add(botSeat("TFT Bot ${index + 1}", difficulty)) }
            }
            "tower_defense" -> if (mode == "solo") listOf(realSeat(player)) else listOf(realSeat(player), botSeat("Defense Assistant", difficulty))
            else -> listOf(realSeat(player), botSeat("SV Bot", difficulty))
        }
        val handle = register(player.server, create(gameId, seats), mode)
        return Result(true, "Đã tạo ${def.title}${if (mode.startsWith("bot_")) " • ${difficulty.name}" else ""}.", realPlayers(handle))
    }

    fun cancelQueue(player: ServerPlayer): Result {
        var removed = false
        queues.values.forEach { removed = it.remove(player.uuid) || removed }
        return Result(removed, if (removed) "Đã rời hàng chờ." else "Bạn không ở hàng chờ.", setOf(player.uuid))
    }

    fun act(player: ServerPlayer, action: String, args: Map<String, String>): Result {
        val sid = active[player.uuid] ?: return Result(false, "Không có ván đang hoạt động.")
        val handle = sessions[sid] ?: return Result(false, "Session đã hết hạn.")
        if (action == "resign") meta[sid]?.forfeited?.add(player.uuid)
        val queued = handle.submitAction(player.uuid.toString(), action, args, bot = false)
        return if (queued) Result(true, "Đang xử lý…") else Result(false, "Game worker đang bận; hãy thử lại.", setOf(player.uuid))
    }

    fun leave(player: ServerPlayer): Result {
        cancelQueue(player)
        val sid = active.remove(player.uuid) ?: return Result(true, "Đã rời Arcade.", setOf(player.uuid))
        val handle = sessions[sid]
        meta[sid]?.forfeited?.add(player.uuid)
        handle?.submitAction(player.uuid.toString(), "resign", emptyMap(), bot = false)
        return Result(true, "Đã rời ván.", setOf(player.uuid))
    }

    fun tick(server: MinecraftServer, now: Long = System.currentTimeMillis()): Map<UUID, String> {
        sessions.values.toList().forEach { handle -> if (!handle.finished) handle.submitTick(now) }
        finishedAt.filterValues { now - it > TERMINAL_DEDUP_MS }.keys.toList().forEach { sid ->
            rewarded.remove(sid); finishedAt.remove(sid)
        }
        queues.values.forEach { q -> q.removeIf { server.playerList.getPlayer(it) == null || active.containsKey(it) } }
        if (asyncMessages.isEmpty()) return emptyMap()
        val out = linkedMapOf<UUID, String>()
        asyncMessages.entries.toList().forEach { entry -> if (asyncMessages.remove(entry.key, entry.value)) out[entry.key] = entry.value }
        return out
    }

    fun onDisconnect(player: ServerPlayer) {
        queues.values.forEach { it.remove(player.uuid) }
        val sid = active.remove(player.uuid) ?: return
        meta[sid]?.forfeited?.add(player.uuid)
        sessions[sid]?.submitAction(player.uuid.toString(), "resign", emptyMap(), bot = false)
        asyncMessages.remove(player.uuid)
    }

    fun shutdown() {
        NativeGameEngineRuntime.shutdown(); NativeBotRuntime.shutdown(); NativeRewardService.shutdown()
        sessions.clear(); active.clear(); queues.values.forEach { it.clear() }; rewarded.clear(); finishedAt.clear(); meta.clear(); asyncMessages.clear()
    }

    private fun onEngineUpdate(update: NativeGameEngineRuntime.Update) {
        val handle = sessions[update.sessionId] ?: return
        val m = meta[update.sessionId]
        val sourceUuid = update.sourceSeatId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (!update.sourceBot && sourceUuid != null && update.accepted && update.changed && update.action != "resign") {
            m?.humanActions?.let { actions -> actions[sourceUuid] = (actions[sourceUuid] ?: 0) + 1 }
        }
        if (update.changed || update.finished || update.message.isNotBlank()) {
            realPlayers(handle).forEach { id -> if (active[id] == update.sessionId) asyncMessages[id] = update.message }
        }
        finishIfNeeded(handle)
    }

    private fun register(server: MinecraftServer, session: NativeGameSession, mode: String): NativeGameEngineRuntime.Handle {
        val handle = NativeGameEngineRuntime.register(server, session, ::onEngineUpdate)
        sessions[handle.sessionId] = handle
        val players = realPlayers(handle)
        players.forEach { active[it] = handle.sessionId }
        meta[handle.sessionId] = SessionMeta(mode = mode, humanActions = players.associateWith { 0 }.toMutableMap())
        return handle
    }

    private fun create(id: String, seats: List<NativeSeat>): NativeGameSession = when (id) {
        "chess" -> ChessSession(seats); "xiangqi" -> XiangqiSession(seats); "ludo" -> LudoSession(seats)
        "uno" -> UnoSession(seats); "pokecards" -> CardDuelSession(seats); "tft" -> TftSession(seats)
        "tower_defense" -> TowerDefenseSession(seats); else -> error("Unknown native game $id")
    }

    private fun finishIfNeeded(handle: NativeGameEngineRuntime.Handle) {
        if (!handle.finished || !rewarded.add(handle.sessionId)) return
        val now = System.currentTimeMillis(); finishedAt[handle.sessionId] = now; NativeBotRuntime.forgetSession(handle.sessionId)
        val m = meta[handle.sessionId] ?: SessionMeta("unknown", now)
        val players = realPlayers(handle)
        val participants = players.map { id ->
            val placement = if (handle.gameId == "tft") handle.viewFor(id.toString())?.fields?.get("placement")?.toIntOrNull()?.takeIf { it in 1..8 } else null
            val outcome = when {
                id in m.forfeited -> NativeRewardOutcome.FORFEIT
                placement != null -> if (placement <= 4) NativeRewardOutcome.WIN else NativeRewardOutcome.LOSS
                handle.winnerSeatId == null -> NativeRewardOutcome.DRAW
                handle.winnerSeatId == id.toString() -> NativeRewardOutcome.WIN
                else -> NativeRewardOutcome.LOSS
            }
            NativeProfileStore.mutate(id) { p ->
                val st = p.stats.getOrPut(handle.gameId) { NativeGameStats() }; st.played++
                when (outcome) { NativeRewardOutcome.WIN -> st.wins++; NativeRewardOutcome.DRAW -> st.draws++; NativeRewardOutcome.LOSS, NativeRewardOutcome.FORFEIT -> st.losses++ }
            }
            NativeRewardParticipant(id, outcome, m.humanActions[id] ?: 0, id in m.forfeited, placement)
        }
        NativeRewardService.enqueue(NativeRewardCompletion(handle.sessionId, handle.gameId, m.mode, (now - m.createdAtEpochMs).coerceAtLeast(0L), participants))
        players.forEach { id -> if (active[id] == handle.sessionId) active.remove(id) }
        sessions.remove(handle.sessionId)
        meta.remove(handle.sessionId)
        NativeGameEngineRuntime.unregister(handle.sessionId)
    }

    private fun realPlayers(handle: NativeGameEngineRuntime.Handle) = handle.seats.asSequence().filterNot { it.anyBot }.mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }.toSet()
    private fun realSeat(p: ServerPlayer) = NativeSeat(id = p.uuid.toString(), name = p.gameProfile.name)
    private fun botSeat(name: String, difficulty: NativeBotDifficulty) = NativeSeat(id = "bot:${UUID.randomUUID()}", name = name, bot = false, managedBot = true, botDifficulty = difficulty)
    private fun difficultyFor(mode: String) = when (mode) { "bot_easy" -> NativeBotDifficulty.EASY; "bot_hard" -> NativeBotDifficulty.HARD; else -> NativeBotDifficulty.NORMAL }

    private const val TERMINAL_DEDUP_MS = 10 * 60 * 1000L
    private const val MAX_SESSIONS = 512
}
