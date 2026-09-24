package io.github.aristheg201.svarcade.native

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
