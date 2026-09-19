package io.github.aristheg201.svhub.native.game.tft

data class TftSetDefinition(
    val schema: Int = 1,
    val id: String = "kanto_rising",
    val name: String = "Kanto Rising",
    val planningSeconds: Int = 30,
    val combatSeconds: Int = 45,
    val postCombatSeconds: Int = 4,
    val maxLevel: Int = 10,
    val poolSizeByCost: Map<String, Int> = emptyMap(),
    val xpToNextByLevel: Map<String, Int> = emptyMap(),
    val progression: TftProgressionDefinition? = null,
    val shopOdds: List<TftShopOdds> = emptyList(),
    val units: List<TftUnitDefinition> = emptyList(),
    val traits: List<TftTraitDefinition> = emptyList(),
    val components: List<TftItemComponentDefinition> = emptyList(),
    val fullItems: List<TftFullItemDefinition> = emptyList(),
    val augments: List<TftAugmentDefinition> = emptyList(),
    val pveRounds: List<TftPveRoundDefinition> = emptyList()
)

data class TftShopOdds(val level: Int = 2, val odds: List<Int> = listOf(100, 0, 0, 0, 0))

data class TftUnitDefinition(
    val id: String = "",
    val species: String = "",
    val aspects: List<String> = emptyList(),
    val cost: Int = 1,
    val traits: List<String> = emptyList(),
    val role: String = "fighter",
    val stats: TftUnitStats = TftUnitStats(),
    val ability: TftAbilityDefinition = TftAbilityDefinition()
)

data class TftUnitStats(
    val hp: Int = 600,
    val attackDamage: Int = 50,
    val defense: Int = 30,
    val specialDefense: Int = 30,
    val attackSpeed: Double = 0.7,
    val range: Int = 1,
    val manaStart: Int = 0,
    val manaMax: Int = 80,
    val critChance: Double = 0.25,
    val critMultiplier: Double = 1.4,
    val moveSpeed: Double = 1.0
)

data class TftAbilityDefinition(
    val id: String = "basic_spell",
    val name: String = "Ability",
    val target: String = "current",
    val damageType: String = "magic",
    val damage: Int = 150,
    val heal: Int = 0,
    val shield: Int = 0,
    val radius: Int = 0,
    val stunMs: Int = 0,
    val dash: Int = 0,
    val effects: Map<String, Double> = emptyMap()
)

data class TftTraitDefinition(
    val id: String = "",
    val name: String = "",
    val tiers: List<TftTraitTier> = emptyList()
)

data class TftTraitTier(
    val threshold: Int = 2,
    val description: String = "",
    /** Applied to units carrying this trait. */
    val effects: Map<String, Double> = emptyMap(),
    /** Applied to every allied unit while this threshold is active. */
    val teamEffects: Map<String, Double> = emptyMap()
)

data class TftItemComponentDefinition(
    val id: String = "",
    val name: String = "",
    val effects: Map<String, Double> = emptyMap()
)

data class TftFullItemDefinition(
    val id: String = "",
    val name: String = "",
    val components: List<String> = emptyList(),
    val effects: Map<String, Double> = emptyMap()
)

data class TftAugmentDefinition(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val effects: Map<String, Double> = emptyMap(),
    /** Data-driven bot preference. Gameplay never branches on augment ids. */
    val aiWeight: Int = 50
)

data class TftPveRoundDefinition(
    val round: String = "1-1",
    val enemies: List<TftPveEnemyDefinition> = emptyList(),
    val componentDrops: Int = 1
)

data class TftPveEnemyDefinition(
    val unit: String = "",
    val star: Int = 1,
    /** Local 7x4 formation slot. */
    val slot: Int = 0
)

object TftDefinitionValidator {
    fun validate(set: TftSetDefinition): TftSetDefinition {
        require(set.schema == 1) { "Unsupported TFT schema ${set.schema}" }
        require(set.name.isNotBlank()) { "TFT set name is empty" }
        val progression = set.progression ?: TftProgressionDefinition(maxLevel = set.maxLevel, xpToNextByLevel = set.xpToNextByLevel)
        progression.validate("set ${set.id}.progression")
        require(set.planningSeconds in 1..600 && set.combatSeconds in 1..600 && set.postCombatSeconds in 1..60) { "Invalid TFT phase durations" }
        require(set.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid TFT set id ${set.id}" }
        require(set.units.size >= 20) { "TFT set ${set.id} requires at least 20 units" }
        require(set.units.map { it.id }.toSet().size == set.units.size) { "Duplicate TFT unit id" }
        require(set.traits.map { it.id }.toSet().size == set.traits.size) { "Duplicate TFT trait id" }
        val traitIds = set.traits.map { it.id }.toSet()
        set.units.forEach { unit ->
            require(unit.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid TFT unit id ${unit.id}" }
            require(unit.species.contains(':')) { "TFT unit ${unit.id} must use a namespaced species id" }
            require(unit.cost in 1..5) { "TFT unit ${unit.id} has invalid cost ${unit.cost}" }
            require(unit.stats.hp > 0 && unit.stats.attackDamage > 0) { "TFT unit ${unit.id} has invalid base stats" }
            require(unit.stats.attackSpeed in 0.1..5.0) { "TFT unit ${unit.id} attack speed out of range" }
            require(unit.stats.range in 1..6) { "TFT unit ${unit.id} attack range out of range" }
            val unknown = unit.traits.filterNot(traitIds::contains)
            require(unknown.isEmpty()) { "TFT unit ${unit.id} references unknown traits $unknown" }
        }
        require(set.shopOdds.map { it.level }.toSet().size == set.shopOdds.size) { "Duplicate TFT shop odds level" }
        for (level in 2..progression.maxLevel) {
            require(set.shopOdds.any { it.level == level }) { "Missing TFT shop odds for level $level" }
            if (level < progression.maxLevel) require((progression.xpToNextByLevel[level.toString()] ?: 0) > 0) { "Missing TFT XP requirement for level $level" }
        }
        require(set.shopOdds.isNotEmpty()) { "TFT set has no shop odds" }
        set.shopOdds.forEach { row ->
            require(row.odds.size == 5 && row.odds.all { it in 0..100 } && row.odds.sum() == 100) { "Shop odds for level ${row.level} must contain 5 entries summing to 100" }
        }
        for (cost in 1..5) require((set.poolSizeByCost[cost.toString()] ?: 0) > 0) { "Missing pool size for cost $cost" }
        set.traits.forEach { trait ->
            require(trait.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid trait id ${trait.id}" }
            require(trait.tiers.zipWithNext().all { it.first.threshold < it.second.threshold }) { "Trait ${trait.id} tiers must be ascending" }
        }
        val componentIds = set.components.map { it.id }.toSet()
        require(componentIds.size == set.components.size) { "Duplicate TFT component id" }
        require(set.fullItems.map { it.id }.toSet().size == set.fullItems.size) { "Duplicate TFT full item id" }
        require(set.fullItems.map { it.components.sorted().joinToString("+") }.toSet().size == set.fullItems.size) { "Duplicate TFT full item recipe" }
        set.augments.forEach { require(it.aiWeight in 0..1000) { "Augment ${it.id} aiWeight out of range" } }
        set.fullItems.forEach { item ->
            require(item.components.size == 2) { "Full item ${item.id} must have exactly two components" }
            require(item.components.all(componentIds::contains)) { "Full item ${item.id} references unknown components" }
        }
        require(set.components.isNotEmpty()) { "TFT set has no item components" }
        require(set.augments.map { it.id }.toSet().size == set.augments.size) { "Duplicate TFT augment id" }
        require(set.pveRounds.map { it.round }.toSet().size == set.pveRounds.size) { "Duplicate TFT PvE round" }
        val unitIds = set.units.map { it.id }.toSet()
        set.pveRounds.forEach { round ->
            require(round.enemies.isNotEmpty()) { "PvE round ${round.round} has no enemies" }
            require(round.componentDrops >= 0) { "PvE round ${round.round} has negative drops" }
            require(round.enemies.map { it.slot }.toSet().size == round.enemies.size) { "PvE round ${round.round} overlaps formation slots" }
            round.enemies.forEach { enemy ->
                require(enemy.unit in unitIds) { "PvE round ${round.round} references unknown unit ${enemy.unit}" }
                require(enemy.star in 1..3 && enemy.slot in 0..27) { "PvE round ${round.round} has invalid star/slot" }
            }
        }
        return set
    }
}
