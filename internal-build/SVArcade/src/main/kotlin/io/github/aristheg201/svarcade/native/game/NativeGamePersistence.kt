package io.github.aristheg201.svarcade.native.game

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.native.game.tft.TftSetDefinition
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

internal object NativeGamePersistence {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    fun toJson(value: Any): JsonObject = gson.toJsonTree(value).asJsonObject
    fun <T> fromJson(state: JsonObject, type: Class<T>): T = gson.fromJson(state, type)
    fun recoverySeed(sessionId: String, state: JsonObject): Long {
        val uuid = UUID.nameUUIDFromBytes(("svarcade:resume:" + sessionId + ":" + state.toString()).toByteArray(StandardCharsets.UTF_8))
        return uuid.mostSignificantBits xor uuid.leastSignificantBits
    }
}

object NativeGameRestorer {
    fun restore(gameId: String, seats: List<NativeSeat>, sessionId: String, state: JsonObject): NativeGameSession {
        val seed = NativeGamePersistence.recoverySeed(sessionId, state)
        return when (gameId) {
            "chess" -> ChessSession(seats = seats, sessionId = sessionId, restoreState = state, seed = seed)
            "xiangqi" -> XiangqiSession(seats = seats, sessionId = sessionId, restoreState = state, seed = seed)
            "ludo" -> LudoSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "uno" -> UnoSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "pokecards" -> CardDuelSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "tower_defense" -> TowerDefenseSession(seats = seats, seed = seed, sessionId = sessionId, restoreState = state)
            "tft" -> {
                val setElement = state.get("setDefinition")
                    ?: error("TFT recovery snapshot is missing setDefinition")
                val definition = requireNotNull(NativeGamePersistence.gson.fromJson(setElement, TftSetDefinition::class.java)) {
                    "TFT recovery snapshot has no set definition"
                }
                TftSession(
                    seats = seats,
                    seed = seed,
                    sessionId = sessionId,
                    definition = definition,
                    restoreState = state
                )
            }
            else -> throw IllegalArgumentException("No recovery codec for game: $gameId")
        }
    }
}


class NativeStatefulRandom(seed: Long) : Random() {
    var state: Long = seed
        private set

    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 0..32)
        if (bitCount == 0) return 0
        var z = state + GOLDEN_GAMMA
        state = z
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        z = z xor (z ushr 31)
        return (z ushr (64 - bitCount)).toInt()
    }

    fun restore(checkpoint: Long) {
        state = checkpoint
    }

    companion object {
        private const val GOLDEN_GAMMA: Long = -7046029254386353131L
        private const val MIX1: Long = -4658895280553007687L
        private const val MIX2: Long = -7723592293110705685L
    }
}
