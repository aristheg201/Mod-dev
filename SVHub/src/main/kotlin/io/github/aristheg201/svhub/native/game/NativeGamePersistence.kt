package io.github.aristheg201.svhub.native.game

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object NativeGamePersistence {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    fun toJson(value: Any): JsonObject = gson.toJsonTree(value).asJsonObject
    fun <T> fromJson(state: JsonObject, type: Class<T>): T = gson.fromJson(state, type)
    fun recoverySeed(sessionId: String, state: JsonObject): Long {
        val uuid = UUID.nameUUIDFromBytes(("svhub:resume:" + sessionId + ":" + state.toString()).toByteArray(StandardCharsets.UTF_8))
        return uuid.mostSignificantBits xor uuid.leastSignificantBits
    }
}

object NativeGameRestorer {
    fun restore(gameId: String, seats: List<NativeSeat>, sessionId: String, state: JsonObject): NativeGameSession {
        val seed = NativeGamePersistence.recoverySeed(sessionId, state)
        return when (gameId) {
            "chess" -> ChessSession(seats = seats, sessionId = sessionId, restoreState = state)
            "xiangqi" -> XiangqiSession(seats = seats, sessionId = sessionId, restoreState = state)
            "ludo" -> LudoSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "uno" -> UnoSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "pokecards" -> CardDuelSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "tower_defense" -> TowerDefenseSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            else -> throw IllegalArgumentException("No recovery codec for game: $gameId")
        }
    }
}
