package io.github.aristheg201.svhub.native

import java.util.UUID
import kotlin.math.roundToLong

enum class NativeRewardOutcome { WIN, DRAW, LOSS, FORFEIT }

data class NativeRewardRules(
    val winTokens: Long = 50,
    val drawTokens: Long = 25,
    val lossTokens: Long = 15,
    val winTickets: Int = 0,
    val drawTickets: Int = 0,
    val lossTickets: Int = 0,
    val minimumHumanActions: Int = 2,
    val minimumDurationSeconds: Long = 10,
    val modeMultipliers: Map<String, Double> = linkedMapOf(
        "pvp" to 1.0,
        "solo" to 1.0,
        "bot_easy" to 0.50,
        "bot_normal" to 0.75,
        "bot_hard" to 1.0
    ),
    /** TFT placement multiplier. Top 4 use win reward base, bottom 4 use loss base. */
    val tftPlacementMultipliers: Map<Int, Double> = linkedMapOf(
        1 to 1.50, 2 to 1.25, 3 to 1.10, 4 to 1.00,
        5 to 0.80, 6 to 0.70, 7 to 0.60, 8 to 0.50
    )
)

data class NativeRewardParticipant(
    val playerId: UUID,
    val outcome: NativeRewardOutcome,
    val humanActions: Int,
    val forfeited: Boolean = false,
    val placement: Int? = null
)

data class NativeRewardCompletion(
    val sessionId: String,
    val gameId: String,
    val mode: String,
    val durationMillis: Long,
    val participants: List<NativeRewardParticipant>
)

data class NativeRewardAward(
    val playerId: UUID,
    val arcadeTokens: Long,
    val gachaTickets: Int,
    val eligible: Boolean,
    val reason: String
)

/** Pure reward computation. No game/profile/world access occurs here. */
object NativeRewardPolicy {
    fun calculate(completion: NativeRewardCompletion, rules: NativeRewardRules): List<NativeRewardAward> {
        val modeMultiplier = (rules.modeMultipliers[completion.mode] ?: 1.0).coerceIn(0.0, 10.0)
        val longEnough = completion.durationMillis >= rules.minimumDurationSeconds.coerceAtLeast(0L) * 1_000L
        return completion.participants.map { p ->
            val eligible = !p.forfeited && (p.humanActions >= rules.minimumHumanActions.coerceAtLeast(0) || longEnough)
            if (!eligible || p.outcome == NativeRewardOutcome.FORFEIT) {
                NativeRewardAward(p.playerId, 0, 0, false, if (p.forfeited) "forfeit" else "minimum participation not reached")
            } else {
                val placement = p.placement?.coerceIn(1, 8)
                val tftPlacement = completion.gameId == "tft" && placement != null
                val effectiveOutcome = if (tftPlacement) {
                    if (placement!! <= 4) NativeRewardOutcome.WIN else NativeRewardOutcome.LOSS
                } else p.outcome
                val placementMultiplier = if (tftPlacement) (rules.tftPlacementMultipliers[placement] ?: 1.0).coerceIn(0.0, 10.0) else 1.0
                val multiplier = modeMultiplier * placementMultiplier
                val baseTokens = when (effectiveOutcome) {
                    NativeRewardOutcome.WIN -> rules.winTokens
                    NativeRewardOutcome.DRAW -> rules.drawTokens
                    NativeRewardOutcome.LOSS -> rules.lossTokens
                    NativeRewardOutcome.FORFEIT -> 0L
                }.coerceAtLeast(0L)
                val tickets = when (effectiveOutcome) {
                    NativeRewardOutcome.WIN -> rules.winTickets
                    NativeRewardOutcome.DRAW -> rules.drawTickets
                    NativeRewardOutcome.LOSS -> rules.lossTickets
                    NativeRewardOutcome.FORFEIT -> 0
                }.coerceAtLeast(0)
                NativeRewardAward(
                    playerId = p.playerId,
                    arcadeTokens = (baseTokens * multiplier).roundToLong().coerceAtLeast(0L),
                    gachaTickets = (tickets * multiplier).roundToLong().toInt().coerceAtLeast(0),
                    eligible = true,
                    reason = if (tftPlacement) "placement #$placement x${"%.2f".format(multiplier)}" else "${effectiveOutcome.name.lowercase()} x${"%.2f".format(multiplier)}"
                )
            }
        }
    }
}
