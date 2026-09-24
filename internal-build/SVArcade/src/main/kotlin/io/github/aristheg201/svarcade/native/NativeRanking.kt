package io.github.aristheg201.svarcade.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/** Settles independently of the optional economy provider, using the profile transaction journal. */
object NativeRanking {
    private var rules = JsonObject()
    fun start(path: java.nio.file.Path) {
        if (!java.nio.file.Files.exists(path)) io.github.aristheg201.svarcade.util.AtomicFiles.writeUtf8(path, javaClass.getResourceAsStream("/data/svarcade/ranking.json")!!.bufferedReader().use { it.readText() })
        rules = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).asJsonObject
    }
    private fun points(outcome: NativeRewardOutcome) = rules.getAsJsonObject("points")?.get(outcome.name)?.asInt ?: 0
    private fun tier(stats: NativeGameStats): String = if (stats.rankedPlayed == 0) "Unranked" else rules.getAsJsonObject("tiers")?.entrySet()?.filter { stats.rating >= it.value.asInt }?.maxByOrNull { it.value.asInt }?.key ?: "Unranked"

    fun settle(session: String, game: String, participants: List<NativeRewardParticipant>) {
        participants.forEach { participant ->
            NativeProfileStore.mutateDurableOnce(participant.playerId, "rank:$session", { profile ->
                val stats = profile.stats.getOrPut(game) { NativeGameStats() }
                val delta = points(participant.outcome)
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
                addProperty("tier", tier(stats))
                add("leaderboard", JsonArray().also { a -> NativeProfileStore.leaderboard(game.id).forEach { (name, rating) -> a.add("$name  $rating LP") } })
                add("history", JsonArray().also { a -> stats.history?.forEach(a::add) })
            })
        }
    }
}
