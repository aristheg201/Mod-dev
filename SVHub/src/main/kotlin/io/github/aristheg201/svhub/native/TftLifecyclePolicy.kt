package io.github.aristheg201.svhub.native

/** Pure production policies shared by matchmaking, networking and regression tests. */
object TftLifecyclePolicy {
    const val PLAYER_SLOTS = 8
    const val MINIMUM_HUMANS = 2

    fun botFillCount(humans: Int): Int? = when {
        humans < MINIMUM_HUMANS -> null
        humans > PLAYER_SLOTS -> 0
        else -> PLAYER_SLOTS - humans
    }

    fun closeResigns(gameId: String?): Boolean = gameId != "tft"
    fun disconnectExpiresToBot(gameId: String?): Boolean = gameId == "tft"
}
