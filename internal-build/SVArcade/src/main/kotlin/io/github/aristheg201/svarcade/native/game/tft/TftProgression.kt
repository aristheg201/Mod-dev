package io.github.aristheg201.svarcade.native.game.tft

/** Content values belong to the set. Zero defaults disable grants in unconfigured sets. */
data class TftProgressionDefinition(
    val maxLevel: Int = 10,
    val passiveXpPerRound: Int = 0,
    val buyXp: TftXpPurchase = TftXpPurchase(),
    val xpToNextByLevel: Map<String, Int> = emptyMap(),
    val passiveRoundTypes: Set<String> = setOf("pvp", "pve")
) {
    fun validate(source: String) {
        require(maxLevel in 2..100) { "$source.maxLevel: expected 2..100" }
        require(passiveXpPerRound in 0..100000) { "$source.passiveXpPerRound: expected 0..100000" }
        require(buyXp.goldCost in 0..100000 && buyXp.xpGranted in 1..100000) { "$source.buyXp: invalid cost/grant" }
        require(passiveRoundTypes.all { it in setOf("pvp", "pve", "carousel") }) { "$source.passiveRoundTypes: unknown round type" }
        for (level in 2 until maxLevel) require((xpToNextByLevel[level.toString()] ?: 0) in 1..1000000) {
            "$source.xpToNextByLevel.$level: missing or invalid requirement"
        }
    }
}

data class TftXpPurchase(val goldCost: Int = 4, val xpGranted: Int = 4)
data class TftXpResult(val level: Int, val xp: Int, val levelsGained: Int, val granted: Int)

/** Pure arithmetic shared by purchased XP, round XP and reward XP. */
object TftProgression {
    fun grant(rules: TftProgressionDefinition, level: Int, xp: Int, amount: Int): TftXpResult {
        require(level in 2..rules.maxLevel && xp >= 0 && amount >= 0)
        if (level == rules.maxLevel) return TftXpResult(level, 0, 0, 0)
        var currentLevel = level
        var remaining = xp.toLong() + amount
        while (currentLevel < rules.maxLevel) {
            val required = rules.xpToNextByLevel[currentLevel.toString()]
                ?: error("progression.xpToNextByLevel.$currentLevel: missing requirement")
            require(required > 0)
            if (remaining < required) break
            remaining -= required
            currentLevel++
        }
        return TftXpResult(currentLevel, if (currentLevel == rules.maxLevel) 0 else remaining.toInt(), currentLevel - level, amount)
    }

    fun passiveAmount(rules: TftProgressionDefinition, roundType: String, flat: Double, multiplier: Double): Int {
        if (roundType !in rules.passiveRoundTypes) return 0
        require(flat.isFinite() && multiplier.isFinite())
        return ((rules.passiveXpPerRound + flat) * (1.0 + multiplier)).coerceIn(0.0, 100000.0).toInt()
    }
}
