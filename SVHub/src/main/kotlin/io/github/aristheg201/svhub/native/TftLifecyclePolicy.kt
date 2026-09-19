package io.github.aristheg201.svhub.native

/** Pure production policies shared by matchmaking, networking and regression tests. */
object TftLifecyclePolicy {
    const val PLAYER_SLOTS = 8
    const val MINIMUM_HUMANS = 2
    const val COLLECTION_WINDOW_MS = 10_000L

    data class MatchmakingConfig(
        val playerSlots: Int = PLAYER_SLOTS,
        val minimumHumans: Int = MINIMUM_HUMANS,
        val botFill: Boolean = true,
        val collectionWindowMs: Long = COLLECTION_WINDOW_MS
    ) {
        init {
            require(playerSlots >= 2)
            require(minimumHumans in 2..playerSlots)
            require(collectionWindowMs >= 0)
        }
    }

    enum class CollectionDecision { WAITING_FOR_MINIMUM, COLLECTING, START }

    fun decision(humans: Int, collectionDeadlineMs: Long?, nowMs: Long, config: MatchmakingConfig = MatchmakingConfig()): CollectionDecision = when {
        humans < config.minimumHumans -> CollectionDecision.WAITING_FOR_MINIMUM
        humans >= config.playerSlots -> CollectionDecision.START
        collectionDeadlineMs != null && nowMs >= collectionDeadlineMs -> CollectionDecision.START
        else -> CollectionDecision.COLLECTING
    }

    fun botFillCount(humans: Int): Int? = when {
        humans < MINIMUM_HUMANS -> null
        humans > PLAYER_SLOTS -> 0
        else -> PLAYER_SLOTS - humans
    }

    fun closeResigns(gameId: String?): Boolean = gameId != "tft"
    fun disconnectExpiresToBot(gameId: String?): Boolean = gameId == "tft"
}
