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
    )
)

data class NativeRewardParticipant(
    val playerId: UUID,
    val outcome: NativeRewardOutcome,
    val humanActions: Int,
    val forfeited: Boolean = false
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
        val multiplier = (rules.modeMultipliers[completion.mode] ?: 1.0).coerceIn(0.0, 10.0)
        val longEnough = completion.durationMillis >= rules.minimumDurationSeconds.coerceAtLeast(0L) * 1_000L
        return completion.participants.map { p ->
            val eligible = !p.forfeited && (p.humanActions >= rules.minimumHumanActions.coerceAtLeast(0) || longEnough)
            if (!eligible || p.outcome == NativeRewardOutcome.FORFEIT) {
                NativeRewardAward(p.playerId, 0, 0, false, if (p.forfeited) "forfeit" else "minimum participation not reached")
            } else {
                val baseTokens = when (p.outcome) {
                    NativeRewardOutcome.WIN -> rules.winTokens
                    NativeRewardOutcome.DRAW -> rules.drawTokens
                    NativeRewardOutcome.LOSS -> rules.lossTokens
                    NativeRewardOutcome.FORFEIT -> 0L
                }.coerceAtLeast(0L)
                val tickets = when (p.outcome) {
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
                    reason = "${p.outcome.name.lowercase()} x${"%.2f".format(multiplier)}"
                )
            }
        }
    }
}
