package io.github.aristheg201.svarcade.native.game.tft

/** Generic player-level capabilities. Content selects capabilities; sessions never inspect content IDs. */
enum class TftPlayerModifier(
    val stacking: Stacking = Stacking.ADD,
    val minimum: Double = -100_000.0,
    val maximum: Double = 100_000.0
) {
    /** Reserved capabilities stay authorable only at zero until runtime consumers exist. */
    PLAYER_RESOURCE_FLAT(minimum = 0.0, maximum = 0.0),
    INCOME_FLAT,
    INCOME_MULTIPLIER(minimum = -1.0, maximum = 100.0),
    INTEREST_CAP(Stacking.MAX, minimum = 0.0),
    SHOP_REFRESH_COST,
    FREE_REFRESH_COUNT(minimum = 0.0),
    XP_GAIN_FLAT,
    XP_GAIN_MULTIPLIER(minimum = -1.0, maximum = 100.0),
    XP_PURCHASE_COST,
    XP_PURCHASE_AMOUNT,
    BOARD_CAPACITY,
    PVE_DROP_COUNT,
    POST_ROUND_HEAL,
    PLAYER_DAMAGE_FLAT(minimum = 0.0, maximum = 0.0),
    LOOT_MULTIPLIER(minimum = 0.0, maximum = 0.0),
    SHOP_ODDS_SHIFT(minimum = 0.0, maximum = 0.0);

    enum class Stacking { ADD, MAX }
}

/** Immutable compiled view assembled from augments/traits/teams/items. */
class TftPlayerModifierSet private constructor(private val values: Map<TftPlayerModifier, Double>) {
    fun value(capability: TftPlayerModifier): Double = values[capability] ?: 0.0

    fun apply(capability: TftPlayerModifier, base: Double): Double = when (capability.stacking) {
        TftPlayerModifier.Stacking.ADD -> base + value(capability)
        TftPlayerModifier.Stacking.MAX -> maxOf(base, value(capability))
    }.coerceIn(capability.minimum, capability.maximum)

    companion object {
        val EMPTY = TftPlayerModifierSet(emptyMap())

        fun compile(sources: Iterable<Map<TftPlayerModifier, Double>>): TftPlayerModifierSet {
            val compiled = mutableMapOf<TftPlayerModifier, Double>()
            sources.forEach { source ->
                source.forEach { (capability, amount) ->
                    require(amount.isFinite()) { "Player modifier $capability must be finite" }
                    require(amount in capability.minimum..capability.maximum) {
                        "Player modifier $capability is outside the implemented range"
                    }
                    compiled[capability] = when (capability.stacking) {
                        TftPlayerModifier.Stacking.ADD -> (compiled[capability] ?: 0.0) + amount
                        TftPlayerModifier.Stacking.MAX -> maxOf(compiled[capability] ?: capability.minimum, amount)
                    }
                }
            }
            return TftPlayerModifierSet(compiled.toMap())
        }
    }
}
