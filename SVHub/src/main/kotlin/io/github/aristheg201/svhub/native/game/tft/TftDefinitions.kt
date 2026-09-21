package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.engine.EffectDefinition
import io.github.aristheg201.svhub.engine.TriggerDefinition

data class TftSetDefinition(
    val schema: Int = 1,
    val id: String = "kanto_rising",
    val name: String = "Kanto Rising",
    val planningSeconds: Int = 30,
    val combatSeconds: Int = 45,
    val postCombatSeconds: Int = 4,
    val rules: TftRulesDefinition = TftRulesDefinition(),
    val roundSchedule: List<TftRoundDefinition> = emptyList(),
    val carousel: TftCarouselDefinition = TftCarouselDefinition(),
    val maxLevel: Int = 10,
    val poolSizeByCost: Map<String, Int> = emptyMap(),
    val xpToNextByLevel: Map<String, Int> = emptyMap(),
    val progression: TftProgressionDefinition? = null,
    val tacticians: List<TftTacticianDefinition> = emptyList(),
    val defaultTactician: String = "",
    val effectGraphs: Map<String, List<EffectDefinition>> = emptyMap(),
    val shopOdds: List<TftShopOdds> = emptyList(),
    val units: List<TftUnitDefinition> = emptyList(),
    val teams: List<TftTeamDefinition> = emptyList(),
    val traits: List<TftTraitDefinition> = emptyList(),
    val components: List<TftItemComponentDefinition> = emptyList(),
    val fullItems: List<TftFullItemDefinition> = emptyList(),
    val augments: List<TftAugmentDefinition> = emptyList(),
    val pveRounds: List<TftPveRoundDefinition> = emptyList(),
    val lootTables: List<TftLootTableDefinition> = emptyList()
    ,val botStrategies: List<TftBotStrategyDefinition> = emptyList()
)

data class TftBotStrategyDefinition(
    val id:String="", val preferredTeams:List<String> = emptyList(), val fallbackTeams:List<String> = emptyList(),
    val preferredTraits:List<String> = emptyList(), val preferredCarryRoles:List<String> = emptyList(),
    val preferredItemTags:List<String> = emptyList(), val preferredAugmentTags:List<String> = emptyList(),
    val economyProfile:Map<String,Double> = emptyMap(), val rollProfile:Map<String,Double> = emptyMap(),
    val levelProfile:Map<String,Double> = emptyMap(), val positioningProfile:Map<String,Double> = emptyMap(),
    val transitionRules:List<TftBotTransitionRule> = emptyList()
)
data class TftBotTransitionRule(val phase:String="early",val minimumLevel:Int=1,val maximumLevel:Int=10,val team:String="",val minimumCopies:Int=0,val maximumContested:Int=99)

enum class TftCapability {
    CAN_BUY_UNIT, CAN_REROLL, CAN_BUY_XP, CAN_SELL, CAN_MOVE_BOARD_UNIT,
    CAN_MOVE_BENCH_UNIT, CAN_EQUIP_ITEM, CAN_COMBINE_ITEM, CAN_SCOUT,
    CAN_CAROUSEL_PICK, CAN_EMOTE, CAN_OPEN_SHOP, CAN_INTERACT_BENCH
}

data class TftRulesDefinition(val shopSlots: Int = 5, val benchSlots: Int = 9, val boardColumns: Int = 7, val boardRows: Int = 4, val maxBoardCapacity: Int = 12, val defaultArena: String = "kanto_stadium", val arenas: Set<String> = setOf("kanto_stadium"), val phaseCapabilities: Map<String, Set<TftCapability>> = defaultTftCapabilities()) {
    val formationCells: Int get() = boardColumns * boardRows
}

private fun defaultTftCapabilities(): Map<String, Set<TftCapability>> {
    val economy = setOf(TftCapability.CAN_BUY_UNIT, TftCapability.CAN_REROLL, TftCapability.CAN_BUY_XP,
        TftCapability.CAN_OPEN_SHOP, TftCapability.CAN_SCOUT, TftCapability.CAN_EMOTE)
    return mapOf(
        "planning" to economy + setOf(TftCapability.CAN_SELL, TftCapability.CAN_MOVE_BOARD_UNIT,
            TftCapability.CAN_MOVE_BENCH_UNIT, TftCapability.CAN_EQUIP_ITEM, TftCapability.CAN_COMBINE_ITEM,
            TftCapability.CAN_INTERACT_BENCH),
        "combat" to economy + TftCapability.CAN_SELL,
        "post" to economy,
        "draft" to setOf(TftCapability.CAN_CAROUSEL_PICK, TftCapability.CAN_EMOTE),
        "finished" to emptySet()
    )
}

data class TftRoundDefinition(val label: String = "", val type: String = "pvp", val planningSeconds: Int? = null, val combatSeconds: Int? = null, val income: Boolean = true, val passiveXp: Boolean = true, val pve: String? = null)

data class TftCarouselDefinition(
    val offerCount: Int = 9,
    val ringRadius: Double = 3.0,
    val spawnRadius: Double = 5.0,
    val pickupRadius: Double = 0.72,
    val movementRadius: Double = 5.6,
    val maxMovePerIntent: Double = 0.8,
    val durationMs: Long = 18_000,
    val releaseWaveSize: Int = 2,
    val releaseDelayMs: Long = 1_500,
    val releaseOrder: String = "lowest_health_first",
    val centerDecoration: String = "minecraft:beacon",
    val arenaId: String = "carousel_convergence"
)

data class TftShopOdds(val level: Int = 2, val odds: List<Int> = listOf(100, 0, 0, 0, 0))

/** Tacticians are presentation-only cosmetics, never combat units. */
data class TftTacticianDefinition(
    val id: String = "",
    val entity: String = "",
    val pokemon: PokemonPresentationIdentity? = null,
    val name: String = "",
    val scale: Double = 1.0,
    val cosmeticVfx: String = ""
) {
    val presentation: PokemonPresentationIdentity? get() = pokemon
}

data class TftUnitDefinition(
    val id: String = "",
    /** Complete immutable presentation identity. New content must use this field. */
    val pokemon: PokemonPresentationIdentity? = null,
    /** Schema-one compatibility. Migrated to [pokemon] when definitions are compiled. */
    val species: String = "",
    val aspects: List<String> = emptyList(),
    val cost: Int = 1,
    val traits: List<String> = emptyList(),
    val role: String = "fighter",
    val stats: TftUnitStats = TftUnitStats(),
    val ability: TftAbilityDefinition = TftAbilityDefinition(),
    val triggers: List<TriggerDefinition> = emptyList(),
    val tags: Set<String> = emptySet(),
    val team: String = ""
) {
    val presentation: PokemonPresentationIdentity
        get() = pokemon ?: PokemonPresentationIdentity(species = species, aspects = aspects.toSet())
}

data class PokemonPresentationIdentity(
    val species: String = "",
    val form: String? = null,
    val aspects: Set<String> = emptySet(),
    val shiny: Boolean = false,
    val gender: String? = null,
    val features: Map<String, String> = emptyMap(),
    val cosmeticAspects: Set<String> = emptySet(),
    val scale: Double = 1.0
) {
    fun resolverAspects(): Set<String> = buildSet {
        addAll(aspects)
        addAll(cosmeticAspects)
        form?.takeIf(String::isNotBlank)?.let(::add)
        if (shiny) add("shiny")
        gender?.takeIf(String::isNotBlank)?.let(::add)
        features.toSortedMap().forEach { (key, value) ->
            add(if (value.isBlank()) key else "$key=$value")
        }
    }
}

data class TftTeamDefinition(
    val id: String = "",
    val name: String = "",
    val synergyTrait: String? = null,
    val members: List<TftTeamMemberDefinition> = emptyList(),
    val bench: List<TftTeamMemberDefinition> = emptyList(),
    val augments: List<String> = emptyList(),
    val tactician: String? = null,
    val arena: String? = null,
    val startingLevel: Int = 2,
    val startingGold: Int = 0,
    val startingHealth: Int = 100,
    val aiProfile: String = "normal"
)

data class TftTeamMemberDefinition(
    val unit: String = "",
    val slot: Int? = null,
    val star: Int = 1,
    val items: List<String> = emptyList()
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
    val effects: Map<String, Double> = emptyMap(),
    val graph: List<EffectDefinition> = emptyList(),
    val castDelayMs: Long = 0
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
    val teamEffects: Map<String, Double> = emptyMap(),
    val triggers: List<TriggerDefinition> = emptyList(),
    val teamTriggers: List<TriggerDefinition> = emptyList()
)

data class TftItemComponentDefinition(
    val id: String = "",
    val name: String = "",
    val stack: String = "",
    val effects: Map<String, Double> = emptyMap(),
    val triggers: List<TriggerDefinition> = emptyList()
)

data class TftFullItemDefinition(
    val id: String = "",
    val name: String = "",
    val stack: String = "",
    val components: List<String> = emptyList(),
    val effects: Map<String, Double> = emptyMap(),
    val triggers: List<TriggerDefinition> = emptyList()
)

data class TftAugmentDefinition(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val effects: Map<String, Double> = emptyMap(),
    val playerModifiers: Map<TftPlayerModifier, Double> = emptyMap(),
    /** Data-driven bot preference. Gameplay never branches on augment ids. */
    val aiWeight: Int = 50,
    val tier: String = "Gold",
    val triggers: List<TriggerDefinition> = emptyList()
)

data class TftPveRoundDefinition(
    val round: String = "1-1",
    val enemies: List<TftPveEnemyDefinition> = emptyList(),
    val componentDrops: Int = 1,
    val lootTable: String? = null,
    val lootRolls: Int = 0
)

data class TftLootTableDefinition(val id: String = "", val entries: List<TftLootEntryDefinition> = emptyList())
data class TftLootEntryDefinition(val type: String = "gold", val weight: Int = 1, val amount: Int = 1, val value: String? = null, val choices: List<String> = emptyList())

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
        require(set.tacticians.map { it.id }.distinct().size == set.tacticians.size) { "set ${set.id}.tacticians: duplicate id" }
        require(set.botStrategies.map { it.id }.distinct().size == set.botStrategies.size) { "set ${set.id}.botStrategies: duplicate id" }
        val teamDefinitionIds=set.teams.map{it.id}.toSet()
        set.botStrategies.forEach { strategy ->
            require(strategy.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "set ${set.id}.botStrategies.${strategy.id}.id: invalid" }
            require((strategy.preferredTeams+strategy.fallbackTeams).all { it in teamDefinitionIds }) { "set ${set.id}.botStrategies.${strategy.id}.teams: unknown team" }
            require(strategy.transitionRules.all { it.team in teamDefinitionIds && it.minimumLevel in 1..set.maxLevel && it.maximumLevel in it.minimumLevel..set.maxLevel }) { "set ${set.id}.botStrategies.${strategy.id}.transitionRules: invalid" }
        }
        set.tacticians.forEach { tactician ->
            val pokemon=tactician.presentation
            val vanilla=tactician.entity.matches(Regex("minecraft:[a-z0-9_]+"))
            val pokemonValid=pokemon!=null &&
                pokemon.species.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) &&
                pokemon.scale in 0.1..8.0 &&
                pokemon.aspects.none(String::isBlank) &&
                pokemon.cosmeticAspects.none(String::isBlank) &&
                (pokemon.gender==null || pokemon.gender in setOf("male","female","genderless"))
            require(tactician.id.isNotBlank() && tactician.scale in 0.2..3.0 && (vanilla xor pokemonValid)) {
                "set ${set.id}.tacticians.${tactician.id}: define exactly one valid vanilla entity or Pokemon identity"
            }
        }
        require(set.tacticians.isEmpty() || set.tacticians.any { it.id == set.defaultTactician }) { "set ${set.id}.defaultTactician: unknown id" }
        require(set.planningSeconds in 1..600 && set.combatSeconds in 1..600 && set.postCombatSeconds in 1..60) { "Invalid TFT phase durations" }
        require(set.rules.shopSlots in 1..12 && set.rules.benchSlots in 1..24) { "set ${set.id}.rules inventory geometry is invalid" }
        require(set.rules.boardColumns in 2..12 && set.rules.boardRows in 2..8 && set.rules.maxBoardCapacity in 1..set.rules.formationCells) { "set ${set.id}.rules board geometry is invalid" }
        require(set.rules.defaultArena in set.rules.arenas && set.rules.arenas.all { it.matches(Regex("^[a-z0-9_.-]{1,64}$")) }) { "set ${set.id}.rules arena registry is invalid" }
        require(setOf("planning","combat","post","draft","finished").all(set.rules.phaseCapabilities::containsKey)) { "set ${set.id}.rules.phaseCapabilities must define every phase" }
        require(set.roundSchedule.isNotEmpty()) { "set ${set.id}.roundSchedule is empty" }
        require(set.roundSchedule.map { it.label }.distinct().size == set.roundSchedule.size) { "set ${set.id}.roundSchedule has duplicate labels" }
        set.roundSchedule.forEachIndexed { index, round ->
            require(round.label.isNotBlank()) { "set ${set.id}.roundSchedule[$index].label is empty" }
            require(round.type in setOf("planning", "pvp", "pve", "augment", "carousel", "boss", "special")) { "set ${set.id}.roundSchedule[$index].type is invalid: ${round.type}" }
        }
        with(set.carousel) {
            require(arenaId.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "set ${set.id}.carousel.arenaId is invalid" }
            require(offerCount in 2..24 && ringRadius in 1.5..8.0 && spawnRadius > ringRadius) { "set ${set.id}.carousel ring geometry is invalid" }
            require(pickupRadius in 0.25..2.0 && movementRadius >= spawnRadius && maxMovePerIntent in 0.1..2.0) { "set ${set.id}.carousel movement bounds are invalid" }
            require(durationMs in 5_000..120_000 && releaseWaveSize in 1..8 && releaseDelayMs in 0..20_000) { "set ${set.id}.carousel release timing is invalid" }
            require(releaseOrder in setOf("lowest_health_first", "highest_health_first", "seat_order", "random_seeded")) { "set ${set.id}.carousel.releaseOrder is invalid" }
        }
        require(set.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid TFT set id ${set.id}" }
        require(set.units.size >= 20) { "TFT set ${set.id} requires at least 20 units" }
        require(set.units.map { it.id }.toSet().size == set.units.size) { "Duplicate TFT unit id" }
        require(set.traits.map { it.id }.toSet().size == set.traits.size) { "Duplicate TFT trait id" }
        val traitIds = set.traits.map { it.id }.toSet()
        set.units.forEach { unit ->
            require(unit.id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid TFT unit id ${unit.id}" }
            val identity = unit.presentation
            require(identity.species.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$"))) { "TFT unit ${unit.id}.pokemon.species must be namespaced" }
            require(identity.scale in 0.1..8.0) { "TFT unit ${unit.id}.pokemon.scale is out of range" }
            require(identity.aspects.none(String::isBlank) && identity.cosmeticAspects.none(String::isBlank)) { "TFT unit ${unit.id}.pokemon contains a blank aspect" }
            require(identity.gender == null || identity.gender in setOf("male", "female", "genderless")) { "TFT unit ${unit.id}.pokemon.gender is invalid" }
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
        set.components.forEach { require(it.stack.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$"))) { "Component ${it.id}.stack must reference a registry item" } }
        require(set.fullItems.map { it.id }.toSet().size == set.fullItems.size) { "Duplicate TFT full item id" }
        require(set.fullItems.map { it.components.sorted().joinToString("+") }.toSet().size == set.fullItems.size) { "Duplicate TFT full item recipe" }
        set.augments.forEach { augment ->
            require(augment.aiWeight in 0..1000) { "Augment ${augment.id} aiWeight out of range" }
            augment.playerModifiers.forEach { (capability, value) ->
                require(value.isFinite() && value in capability.minimum..capability.maximum) {
                    "Augment ${augment.id}.playerModifiers.$capability is out of range"
                }
            }
        }
        set.fullItems.forEach { item ->
            require(item.stack.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$"))) { "Full item ${item.id}.stack must reference a registry item" }
            require(item.components.size == 2) { "Full item ${item.id} must have exactly two components" }
            require(item.components.all(componentIds::contains)) { "Full item ${item.id} references unknown components" }
        }
        require(set.components.isNotEmpty()) { "TFT set has no item components" }
        require(set.augments.map { it.id }.toSet().size == set.augments.size) { "Duplicate TFT augment id" }
        require(set.pveRounds.map { it.round }.toSet().size == set.pveRounds.size) { "Duplicate TFT PvE round" }
        val unitIds = set.units.map { it.id }.toSet()
        require(set.teams.map { it.id }.toSet().size == set.teams.size) { "Duplicate TFT team id" }
        val augmentIds = set.augments.map { it.id }.toSet()
        val itemIds = componentIds + set.fullItems.map { it.id }
        val tacticianIds = set.tacticians.map { it.id }.toSet()
        set.teams.forEach { team ->
            require(team.id.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_.-]+$"))) { "TFT team ${team.id}: id must be namespaced" }
            require(team.name.isNotBlank()) { "TFT team ${team.id}.name is empty" }
            require(team.members.isNotEmpty()) { "TFT team ${team.id}.members is empty" }
            require(team.startingLevel in 1..progression.maxLevel && team.startingGold >= 0 && team.startingHealth > 0) { "TFT team ${team.id}: invalid starting state" }
            require(team.synergyTrait == null || team.synergyTrait in traitIds) { "TFT team ${team.id}.synergyTrait is unknown" }
            require(team.augments.all(augmentIds::contains)) { "TFT team ${team.id}.augments contains an unknown augment" }
            require(team.tactician == null || team.tactician in tacticianIds) { "TFT team ${team.id}.tactician is unknown" }
            require(team.arena == null || team.arena.removePrefix("svhub:") in set.rules.arenas) { "TFT team ${team.id}.arena is unknown" }
            val positioned = team.members + team.bench
            require(team.members.mapNotNull { it.slot }.distinct().size == team.members.mapNotNull { it.slot }.size) { "TFT team ${team.id}.members has duplicate board slots" }
            positioned.forEachIndexed { index, member ->
                require(member.unit in unitIds) { "TFT team ${team.id}.member[$index].unit is unknown: ${member.unit}" }
                require(member.star in 1..3) { "TFT team ${team.id}.member[$index].star is invalid" }
                require(member.slot == null || member.slot in 0 until set.rules.formationCells) { "TFT team ${team.id}.member[$index].slot is invalid for configured board" }
                require(member.items.all(itemIds::contains)) { "TFT team ${team.id}.member[$index].items contains an unknown item" }
            }
        }
        set.pveRounds.forEach { round ->
            require(round.enemies.isNotEmpty()) { "PvE round ${round.round} has no enemies" }
            require(round.componentDrops >= 0) { "PvE round ${round.round} has negative drops" }
            require(round.lootRolls in 0..20) { "PvE round ${round.round}.lootRolls is invalid" }
            require(round.enemies.map { it.slot }.toSet().size == round.enemies.size) { "PvE round ${round.round} overlaps formation slots" }
            round.enemies.forEach { enemy ->
                require(enemy.unit in unitIds) { "PvE round ${round.round} references unknown unit ${enemy.unit}" }
                require(enemy.star in 1..3 && enemy.slot in 0 until set.rules.formationCells) { "PvE round ${round.round} has invalid star/slot" }
            }
        }
        val lootIds = set.lootTables.map { it.id }.toSet()
        require(lootIds.size == set.lootTables.size) { "Duplicate TFT loot table id" }
        set.lootTables.forEach { table ->
            require(table.id.matches(Regex("^[a-z0-9_.-]{1,64}$")) && table.entries.isNotEmpty()) { "Invalid TFT loot table ${table.id}" }
            table.entries.forEachIndexed { index, entry ->
                require(entry.type in setOf("gold", "component", "full_item", "unit", "xp", "free_reroll", "special", "choice") && entry.weight > 0 && entry.amount > 0) { "Loot ${table.id}.entries[$index] is invalid" }
                require(entry.type !in setOf("component", "full_item", "unit") || !entry.value.isNullOrBlank()) { "Loot ${table.id}.entries[$index].value is required" }
            }
        }
        set.pveRounds.forEach { require(it.lootTable == null || it.lootTable in lootIds) { "PvE round ${it.round}.lootTable is unknown" } }
        TftEffectValidator.validate(set)
        return set
    }
}
