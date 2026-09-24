from pathlib import Path
root=Path('src/main/kotlin/io/github/aristheg201/svhub');n=root/'native'
(n/'ArcadeCapabilities.kt').write_text('''package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Shared service contract. The client never invents a mode or game option. */
object ArcadeCapabilities {
    val clocks = listOf("10+0", "10+5", "15+10")
    fun clock(game: String) = game == "chess" || game == "xiangqi"
    fun ranked(game: String) = game in setOf("chess", "xiangqi", "ludo", "tft")
    fun json(game: String, modes: Set<String>) = JsonObject().apply {
        addProperty("supports_normal", "pvp" in modes || "solo" in modes)
        addProperty("supports_ranked", "ranked" in modes)
        addProperty("supports_ai", modes.any { it.startsWith("bot_") })
        addProperty("supports_difficulty", modes.any { it.startsWith("bot_") })
        addProperty("supports_multiplayer", "pvp" in modes)
        addProperty("supports_server_clock", clock(game))
        addProperty("supports_time_control", clock(game))
        addProperty("supports_animation", game in setOf("chess", "xiangqi", "ludo", "tower_defense", "tft"))
        if (clock(game)) add("timeControls", JsonArray().also { a -> clocks.forEach(a::add) })
    }
    fun time(value: String): Pair<Long, Long> {
        require(value in clocks)
        return value.substringBefore('+').toLong() * 60_000L to value.substringAfter('+').toLong() * 1_000L
    }
}
''')
p=n/'NativeArcadeService.kt';s=p.read_text();s=s.replace('val modes: Set<String>)','val modes: Set<String>)')
for game,title in [('chess',''),('xiangqi',''),('ludo',''),('tft','')]:
 import re
 s=re.sub(r'(GameDef\("'+game+r'"[^\n]*), BOT_AND_PVP\)',r'\1, BOT_AND_PVP + "ranked")',s)
s=s.replace('addProperty("queued", queues[d.id]?.contains(player.uuid) == true)','add("capabilities", ArcadeCapabilities.json(d.id, d.modes))\n                    addProperty("queued", queues.filterKeys { it == d.id || it.startsWith(d.id + "|") }.values.any { player.uuid in it })')
s=s.replace('        addProperty("engine",', '        add("ranking", NativeRanking.state(player.uuid))\n        addProperty("engine",')
s=s.replace('requestedMode: String): Result','requestedMode: String, timeControl: String = "10+5"): Result')
s=s.replace('        val existingSession =', '        if (ArcadeCapabilities.clock(gameId) && timeControl !in ArcadeCapabilities.clocks) return Result(false, "Invalid time control")\n        val existingSession =',1)
s=s.replace('        if (mode == "pvp") {\n            val q = queues.getValue(gameId)','''        if (mode == "pvp" || mode == "ranked") {
            val queueKey = if (gameId == "tft" && mode == "pvp") gameId else "$gameId|$mode|${if (ArcadeCapabilities.clock(gameId)) timeControl else "none"}"
            val q = queues.getOrPut(queueKey) { ArrayDeque() }''')
s=s.replace('            if (gameId == "tft") {','            if (gameId == "tft" && mode == "pvp") {',1)
s=s.replace('val handle = register(player.server, create(gameId, listOf(realSeat(player), realSeat(op))), mode)','''val seats = mutableListOf(realSeat(player), realSeat(op))
                if (gameId == "tft") repeat(6) { seats += botSeat("Ranked Bot ${it + 1}", NativeBotDifficulty.HARD) }
                val handle = register(player.server, create(gameId, seats, timeControl), mode)''')
s=s.replace('create(gameId, seats), mode','create(gameId, seats, timeControl), mode')
s=s.replace('private fun create(id: String, seats: List<NativeSeat>): NativeGameSession','private fun create(id: String, seats: List<NativeSeat>, timeControl: String = "10+5"): NativeGameSession')
s=s.replace('"chess" -> ChessSession(seats); "xiangqi" -> XiangqiSession(seats);','"chess" -> ArcadeCapabilities.time(timeControl).let { (base, inc) -> ChessSession(seats, base, inc) }; "xiangqi" -> ArcadeCapabilities.time(timeControl).let { (base, inc) -> XiangqiSession(seats, base, inc) };')
s=s.replace('        val accepted = NativeRewardService.enqueue(', '        if (m.mode == "ranked") NativeRanking.settle(handle.sessionId, handle.gameId, participants)\n        val accepted = NativeRewardService.enqueue(')
p.write_text(s)
p=n/'NativePlatform.kt';s=p.read_text().replace('data.string("mode", "bot_normal"))','data.string("mode", "bot_normal"), data.string("timeControl", "10+5"))');p.write_text(s)
p=n/'NativeModels.kt';s=p.read_text().replace('var draws: Int = 0)','var draws: Int = 0, var rankedPlayed: Int = 0, var rating: Int = 0, var history: MutableList<String> = mutableListOf())');s=s.replace('            "hunter" -> all.filter { it.currency == "huntercoin" }\n','');p.write_text(s)
p=n/'NativeProfileStore.kt';s=p.read_text().replace('NativeGameStats(s.played, s.wins, s.losses, s.draws)','s.copy(history = (s.history ?: mutableListOf()).toMutableList())');s=s.replace('        trimTransactions(p)\n        return p','        p.stats.values.forEach { if (it.history == null) it.history = mutableListOf() }\n        trimTransactions(p)\n        return p');p.write_text(s)
(n/'NativeRanking.kt').write_text('''package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/** Settles independently of the optional economy provider, using the profile transaction journal. */
object NativeRanking {
    fun settle(session: String, game: String, participants: List<NativeRewardParticipant>) {
        participants.forEach { participant ->
            NativeProfileStore.mutateDurableOnce(participant.playerId, "rank:$session", { profile ->
                val stats = profile.stats.getOrPut(game) { NativeGameStats() }
                val delta = when(participant.outcome) {
                    NativeRewardOutcome.WIN -> 25
                    NativeRewardOutcome.DRAW -> 0
                    NativeRewardOutcome.LOSS -> -20
                    NativeRewardOutcome.FORFEIT -> -30
                }
                stats.rating = (stats.rating + delta).coerceAtLeast(0)
                stats.rankedPlayed++
                if (stats.history == null) stats.history = mutableListOf()
                stats.history.add(0, "${participant.outcome.name}  ${if (delta > 0) "+" else ""}$delta LP")
                while(stats.history.size > 20) stats.history.removeLast()
                true
            }) { }
        }
    }
    fun state(id: UUID) = JsonObject().apply {
        NativeArcadeService.games.filter { "ranked" in it.modes }.forEach { game ->
            val stats = NativeProfileStore.get(id)?.stats?.get(game.id) ?: NativeGameStats()
            add(game.id, JsonObject().apply {
                addProperty("played", stats.rankedPlayed)
                addProperty("rating", stats.rating)
                addProperty("tier", if(stats.rankedPlayed == 0) "Unranked" else when { stats.rating >= 1000 -> "Diamond"; stats.rating >= 600 -> "Gold"; stats.rating >= 300 -> "Silver"; else -> "Bronze" })
                add("history", JsonArray().also { a -> stats.history?.forEach(a::add) })
            })
        }
    }
}
''')
# Clock charge occurs before accepting a move; never forgive a server stall.
for name in ['ChessSession.kt','XiangqiSession.kt']:
 p=n/'game'/name;s=p.read_text();s=s.replace('coerceIn(0L, 2000L)','coerceAtLeast(0L)').replace('coerceIn(0L,2000L)','coerceAtLeast(0L)')
 if name.startswith('Chess'):
  s=s.replace('        if (finished) return NativeGameResult(false, message = "Game already finished")','        tick(System.currentTimeMillis())\n        if (finished) return NativeGameResult(false, message = "Game already finished")',1)
  s=s.replace('"whiteClockMs" to whiteClock.toString(),','"activeClock" to if(side == \'w\') "white" else "black",\n                "clockUpdatedAt" to lastClockAt.toString(),\n                "incrementMs" to incrementMillis.toString(),\n                "whiteClockMs" to whiteClock.toString(),')
 else:
  s=s.replace('):NativeGameResult{if(finished)','):NativeGameResult{tick(System.currentTimeMillis());if(finished)',1)
  s=s.replace('"redClockMs" to redClock.toString(),','"activeClock" to if(redTurn) "red" else "black","clockUpdatedAt" to lastClockAt.toString(),"incrementMs" to incrementMillis.toString(),"redClockMs" to redClock.toString(),')
 # Preserve increment with snapshot regardless of recovery defaults.
 s=s.replace('private val incrementMillis:', 'private var incrementMillis:')
 if name.startswith('Chess'):
  s=s.replace('    private fun restoreSnapshot(state: JsonObject) {','    private fun restoreSnapshot(state: JsonObject) {\n        state.get("clockIncrementMs")?.let { incrementMillis = it.asLong.coerceAtLeast(0L) }')
  s=s.replace('rng.state,lastAuxMoveFrom,lastAuxMoveTo)\n    )','rng.state,lastAuxMoveFrom,lastAuxMoveTo)\n    ).apply { addProperty("clockIncrementMs", incrementMillis) }')
 else:
  s=s.replace('private fun restoreSnapshot(state:JsonObject){','private fun restoreSnapshot(state:JsonObject){state.get("clockIncrementMs")?.let{incrementMillis=it.asLong.coerceAtLeast(0L)};')
  s=s.replace('moveSerial,rng.state))','moveSerial,rng.state)).apply{addProperty("clockIncrementMs",incrementMillis)}')
 p.write_text(s)
