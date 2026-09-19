package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.native.game.NativeStatefulRandom
import io.github.aristheg201.svhub.engine.BattleRuntime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class TftOwnedUnit(
    val instanceId: String,
    val unitId: String,
    var star: Int = 1,
    val items: MutableList<String> = mutableListOf()
)

data class TftCombatUnit(
    val instanceId: String,
    val ownerId: String,
    val team: Int,
    var definition: TftUnitDefinition,
    val star: Int,
    var items: List<String>,
    var cell: Int,
    var maxHp: Int,
    var hp: Int,
    var maxMana: Int,
    var mana: Int,
    var attackDamage: Double,
    var defense: Double,
    var specialDefense: Double,
    var attackSpeed: Double,
    var range: Int,
    var critChance: Double,
    var critMultiplier: Double,
    var abilityPower: Double,
    var manaOnAttack: Int,
    var shield: Int = 0,
    var stunMs: Long = 0L,
    var attackCooldownMs: Double = 0.0,
    var targetId: String? = null,
    var casts: Int = 0,
    var damageDone: Long = 0L,
    var healingDone: Long = 0L
) {
    val alive: Boolean get() = hp > 0
}

data class TftCombatResult(
    val winnerTeam: Int?,
    val survivingTeam0: List<TftCombatUnit>,
    val survivingTeam1: List<TftCombatUnit>,
    val timedOut: Boolean
)

data class TftCombatUnitSnapshot(
    val instanceId: String,
    val ownerId: String,
    val team: Int,
    val unitId: String,
    val star: Int,
    val items: List<String>,
    val cell: Int,
    val maxHp: Int,
    val hp: Int,
    val maxMana: Int,
    val mana: Int,
    val attackDamage: Double,
    val defense: Double,
    val specialDefense: Double,
    val attackSpeed: Double,
    val range: Int,
    val critChance: Double,
    val critMultiplier: Double,
    val abilityPower: Double,
    val manaOnAttack: Int,
    val shield: Int,
    val stunMs: Long,
    val attackCooldownMs: Double,
    val targetId: String?,
    val casts: Int,
    val damageDone: Long,
    val healingDone: Long
)

data class TftCombatSnapshot(
    val schema: Int = 2,
    val maxDurationMs: Long,
    val elapsedMs: Long,
    val finished: Boolean,
    val winnerTeam: Int?,
    val timedOut: Boolean,
    val rngState: Long,
    val units: List<TftCombatUnitSnapshot>,
    val effects: BattleRuntime.Snapshot? = null
)

/** Pure deterministic TFT-like combat. It never touches Minecraft state. */
class TftCombatEngine(
    private val set: TftSetDefinition,
    team0Owner: String,
    team0Board: Map<Int, TftOwnedUnit>,
    team0Augments: List<String>,
    team1Owner: String,
    team1Board: Map<Int, TftOwnedUnit>,
    team1Augments: List<String>,
    seed: Long,
    private val maxDurationMs: Long = set.combatSeconds.coerceIn(15, 90) * 1_000L
) {
    private val rng = NativeStatefulRandom(seed)
    private val unitDefs = set.units.associateBy { it.id }
    private val traitDefs = set.traits.associateBy { it.id }
    private val componentDefs = set.components.associateBy { it.id }
    private val augmentDefs = set.augments.associateBy { it.id }
    private val fullItemDefs = set.fullItems.associateBy { it.id }
    val units = mutableListOf<TftCombatUnit>()
    var elapsedMs: Long = 0L
        private set
    var finished: Boolean = false
        private set
    var result: TftCombatResult? = null
        private set

    private lateinit var effects: TftEffectCombatBridge

    init {
        units += createTeam(0, team0Owner, team0Board, team0Augments)
        units += createTeam(1, team1Owner, team1Board, team1Augments)
        effects = TftEffectCombatBridge(set, units, mapOf(team0Owner to team0Augments, team1Owner to team1Augments), seed)
        if (units.none { it.team == 0 } || units.none { it.team == 1 }) resolve(false)
    }

    constructor(set: TftSetDefinition, snapshot: TftCombatSnapshot) : this(
        set = set,
        team0Owner = "restore:0",
        team0Board = emptyMap(),
        team0Augments = emptyList(),
        team1Owner = "restore:1",
        team1Board = emptyMap(),
        team1Augments = emptyList(),
        seed = 0L,
        maxDurationMs = snapshot.maxDurationMs.coerceIn(1_000L, 600_000L)
    ) {
        require(snapshot.schema in 1..2) { "Unsupported TFT combat snapshot schema " + snapshot.schema }
        units.clear()
        snapshot.units.forEach { saved ->
            val def = unitDefs[saved.unitId]
                ?: error("TFT combat restore references unknown unit " + saved.unitId)
            val maxHp = saved.maxHp.coerceAtLeast(1)
            val maxMana = saved.maxMana.coerceAtLeast(0)
            units += TftCombatUnit(
                instanceId = saved.instanceId,
                ownerId = saved.ownerId,
                team = saved.team.coerceIn(0, 1),
                definition = def,
                star = saved.star.coerceIn(1, 3),
                items = saved.items.toList(),
                cell = saved.cell.coerceIn(0, BOARD_COLUMNS * BOARD_ROWS - 1),
                maxHp = maxHp,
                hp = saved.hp.coerceIn(0, maxHp),
                maxMana = maxMana,
                mana = saved.mana.coerceIn(0, maxMana),
                attackDamage = saved.attackDamage.coerceAtLeast(0.0),
                defense = saved.defense,
                specialDefense = saved.specialDefense,
                attackSpeed = saved.attackSpeed.coerceIn(0.1, 5.0),
                range = saved.range.coerceIn(1, 6),
                critChance = saved.critChance.coerceIn(0.0, 1.0),
                critMultiplier = saved.critMultiplier.coerceAtLeast(1.0),
                abilityPower = saved.abilityPower.coerceAtLeast(0.0),
                manaOnAttack = saved.manaOnAttack.coerceAtLeast(0),
                shield = saved.shield.coerceAtLeast(0),
                stunMs = saved.stunMs.coerceAtLeast(0L),
                attackCooldownMs = saved.attackCooldownMs.coerceAtLeast(0.0),
                targetId = saved.targetId,
                casts = saved.casts.coerceAtLeast(0),
                damageDone = saved.damageDone.coerceAtLeast(0L),
                healingDone = saved.healingDone.coerceAtLeast(0L)
            )
        }
        effects = TftEffectCombatBridge(set, units, emptyMap(), snapshot.rngState, snapshot.effects, recovering = true)
        elapsedMs = snapshot.elapsedMs.coerceIn(0L, maxDurationMs)
        rng.restore(snapshot.rngState)
        finished = snapshot.finished
        result = if (finished) {
            TftCombatResult(
                winnerTeam = snapshot.winnerTeam?.coerceIn(0, 1),
                survivingTeam0 = units.filter { it.alive && it.team == 0 },
                survivingTeam1 = units.filter { it.alive && it.team == 1 },
                timedOut = snapshot.timedOut
            )
        } else {
            null
        }
        cleanupTargets()
    }

    fun snapshotState(): TftCombatSnapshot = TftCombatSnapshot(
        maxDurationMs = maxDurationMs,
        elapsedMs = elapsedMs,
        finished = finished,
        winnerTeam = result?.winnerTeam,
        timedOut = result?.timedOut ?: false,
        rngState = rng.state,
        effects = effects.snapshot(),
        units = units.map { unit ->
            TftCombatUnitSnapshot(
                instanceId = unit.instanceId,
                ownerId = unit.ownerId,
                team = unit.team,
                unitId = unit.definition.id,
                star = unit.star,
                items = unit.items.toList(),
                cell = unit.cell,
                maxHp = unit.maxHp,
                hp = unit.hp,
                maxMana = unit.maxMana,
                mana = unit.mana,
                attackDamage = unit.attackDamage,
                defense = unit.defense,
                specialDefense = unit.specialDefense,
                attackSpeed = unit.attackSpeed,
                range = unit.range,
                critChance = unit.critChance,
                critMultiplier = unit.critMultiplier,
                abilityPower = unit.abilityPower,
                manaOnAttack = unit.manaOnAttack,
                shield = unit.shield,
                stunMs = unit.stunMs,
                attackCooldownMs = unit.attackCooldownMs,
                targetId = unit.targetId,
                casts = unit.casts,
                damageDone = unit.damageDone,
                healingDone = unit.healingDone
            )
        }
    )

    fun step(deltaMillis: Long): Boolean {
        if (finished) return false
        val dt = deltaMillis.coerceIn(20L, 250L)
        elapsedMs += dt
        effects.advance(dt)
        val aliveAtStart = units.count { it.alive }
        val occupancy = linkedMapOf<Int, TftCombatUnit>()
        units.filter { it.alive }.forEach { occupancy[it.cell] = it }

        val overtimeMultiplier = if (elapsedMs > OVERTIME_START_MS) 1.0 + ((elapsedMs - OVERTIME_START_MS) / 5_000.0).coerceAtMost(2.0) * 0.35 else 1.0
        val order = units.filter { it.alive }.sortedWith(compareBy<TftCombatUnit> { it.team }.thenBy { it.instanceId })
        for (unit in order) {
            if (!unit.alive || !effects.canAct(unit.instanceId)) continue
            if (unit.stunMs > 0) {
                unit.stunMs = (unit.stunMs - dt).coerceAtLeast(0L)
                continue
            }
            unit.attackCooldownMs = max(0.0, unit.attackCooldownMs - dt)
            val enemies = units.filter { it.alive && it.team != unit.team }
            if (enemies.isEmpty()) break
            var target = unit.targetId?.let { id -> enemies.firstOrNull { it.instanceId == id } }
            if (target == null) {
                target = enemies.minWithOrNull(compareBy<TftCombatUnit> { hexDistance(unit.cell, it.cell) }.thenBy { it.hp }.thenBy { it.instanceId })
                effects.targetChanged(unit, target)
            }
            target ?: continue
            val distance = hexDistance(unit.cell, target.cell)
            if (distance > unit.range) {
                val moved = effects.canMove(unit.instanceId) && moveToward(unit, target, occupancy)
                if (moved) continue
            }
            if (unit.mana >= unit.maxMana && unit.maxMana > 0 && effects.canCast(unit.instanceId)) {
                cast(unit, target, overtimeMultiplier)
                continue
            }
            if (unit.attackCooldownMs <= 0.0 && hexDistance(unit.cell, target.cell) <= unit.range) basicAttack(unit, target, overtimeMultiplier)
        }

        cleanupTargets()
        val team0Alive = units.any { it.alive && it.team == 0 }
        val team1Alive = units.any { it.alive && it.team == 1 }
        if (!team0Alive || !team1Alive) resolve(false)
        else if (elapsedMs >= maxDurationMs) resolve(true)
        return aliveAtStart != units.count { it.alive } || finished
    }

    private fun createTeam(team: Int, owner: String, board: Map<Int, TftOwnedUnit>, augments: List<String>): List<TftCombatUnit> {
        val traitCounts = board.values.mapNotNull { unitDefs[it.unitId] }.distinctBy { it.id }.flatMap { it.traits }.groupingBy { it }.eachCount()
        val activeTraitEffects = mutableMapOf<String, Double>()
        val teamEffects = mutableMapOf<String, Double>()
        for ((traitId, count) in traitCounts) {
            val tier = traitDefs[traitId]?.tiers?.filter { count >= it.threshold }?.maxByOrNull { it.threshold } ?: continue
            merge(activeTraitEffects, tier.effects, prefix = "$traitId|")
            merge(teamEffects, tier.teamEffects)
        }
        val augmentEffects = mutableMapOf<String, Double>()
        augments.mapNotNull(augmentDefs::get).forEach { merge(augmentEffects, it.effects) }

        return board.entries.sortedBy { it.key }.mapNotNull { (slot, owned) ->
            val def = unitDefs[owned.unitId] ?: return@mapNotNull null
            val effects = mutableMapOf<String, Double>()
            merge(effects, teamEffects); merge(effects, augmentEffects)
            def.traits.forEach { trait -> activeTraitEffects.filterKeys { it.startsWith("$trait|") }.forEach { (key, value) -> effects[key.substringAfter('|')] = (effects[key.substringAfter('|')] ?: 0.0) + value } }
            owned.items.forEach { item ->
                if (item.startsWith("full:")) fullItemDefs[item.removePrefix("full:")]?.let { merge(effects, it.effects) }
                else unpackRuntimeItem(item).forEach { component -> componentDefs[component]?.let { merge(effects, it.effects) } }
            }
            val starMult = when (owned.star) { 2 -> 1.80; 3 -> 3.24; else -> 1.0 }
            val hp = (def.stats.hp * starMult * (1.0 + effects.value("hp_pct"))).roundToInt().coerceAtLeast(1)
            val ad = def.stats.attackDamage * starMult * (1.0 + effects.value("attack_pct"))
            val globalCell = formationToCombatCell(slot, team)
            TftCombatUnit(
                instanceId = owned.instanceId, ownerId = owner, team = team, definition = def, star = owned.star,
                items = owned.items.toList(), cell = globalCell, maxHp = hp, hp = hp,
                maxMana = def.stats.manaMax.coerceAtLeast(0),
                mana = (def.stats.manaStart + effects.value("mana_start").roundToInt()).coerceIn(0, def.stats.manaMax.coerceAtLeast(0)),
                attackDamage = ad, defense = def.stats.defense + effects.value("defense"), specialDefense = def.stats.specialDefense + effects.value("spdef"),
                attackSpeed = (def.stats.attackSpeed * (1.0 + effects.value("attack_speed_pct"))).coerceIn(0.2, 5.0),
                range = (def.stats.range + effects.value("range").roundToInt()).coerceIn(1, 6),
                critChance = (def.stats.critChance + effects.value("crit_chance")).coerceIn(0.0, 1.0),
                critMultiplier = (def.stats.critMultiplier + effects.value("crit_multiplier")).coerceAtLeast(1.0),
                abilityPower = 1.0 + effects.value("ability_power_pct"), manaOnAttack = (10 + effects.value("mana_on_attack").roundToInt()).coerceAtLeast(1)
            )
        }
    }

    private fun basicAttack(attacker: TftCombatUnit, target: TftCombatUnit, overtimeMultiplier: Double) {
        effects.basic(attacker, target, overtimeMultiplier)
        attacker.attackCooldownMs = 1000.0 / attacker.attackSpeed
    }

    private fun cast(caster: TftCombatUnit, target: TftCombatUnit, overtimeMultiplier: Double) {
        effects.cast(caster, target, overtimeMultiplier)
    }

    private fun moveToward(unit: TftCombatUnit, target: TftCombatUnit, occupancy: MutableMap<Int, TftCombatUnit>): Boolean {
        val currentDistance = hexDistance(unit.cell, target.cell)
        val next = neighbors(unit.cell).filter { it !in occupancy }.minWithOrNull(compareBy<Int> { hexDistance(it, target.cell) }.thenBy { it })?.takeIf { hexDistance(it, target.cell) < currentDistance } ?: return false
        val previous = unit.cell
        occupancy.remove(previous); unit.cell = next; occupancy[next] = unit
        effects.moved(unit, previous)
        unit.attackCooldownMs = max(unit.attackCooldownMs, 240.0 / unit.definition.stats.moveSpeed.coerceAtLeast(0.2)); return true
    }

    private fun cleanupTargets() { val dead = units.filterNot { it.alive }.map { it.instanceId }.toSet(); if (dead.isEmpty()) return; units.filter { it.alive && it.targetId in dead }.forEach { it.targetId = null } }
    private fun resolve(timeout: Boolean) {
        if (finished) return
        effects.finish()
        finished = true
        val a = units.filter { it.alive && it.team == 0 }; val b = units.filter { it.alive && it.team == 1 }
        val winner = when { a.isNotEmpty() && b.isEmpty() -> 0; b.isNotEmpty() && a.isEmpty() -> 1; timeout -> { val ar = a.sumOf { it.hp.toDouble() / it.maxHp }; val br = b.sumOf { it.hp.toDouble() / it.maxHp }; when { ar > br + 0.01 -> 0; br > ar + 0.01 -> 1; else -> null } }; else -> null }
        result = TftCombatResult(winner, a, b, timeout)
    }
    private fun formationToCombatCell(slot: Int, team: Int): Int { val col = slot % BOARD_COLUMNS; val row = (slot / BOARD_COLUMNS).coerceIn(0, 3); return if (team == 0) (row + 4) * BOARD_COLUMNS + col else (3 - row) * BOARD_COLUMNS + (BOARD_COLUMNS - 1 - col) }
    private fun neighbors(cell: Int): List<Int> { val row = cell / BOARD_COLUMNS; val col = cell % BOARD_COLUMNS; val offsets = if (row and 1 == 0) EVEN_NEIGHBORS else ODD_NEIGHBORS; return offsets.mapNotNull { (dc, dr) -> val nc = col + dc; val nr = row + dr; if (nc in 0 until BOARD_COLUMNS && nr in 0 until BOARD_ROWS) nr * BOARD_COLUMNS + nc else null } }
    private fun hexDistance(a: Int, b: Int): Int { val ar = a / BOARD_COLUMNS; val ac = a % BOARD_COLUMNS; val br = b / BOARD_COLUMNS; val bc = b % BOARD_COLUMNS; val aq = ac - (ar - (ar and 1)) / 2; val bq = bc - (br - (br and 1)) / 2; val ax = aq; val az = ar; val ay = -ax - az; val bx = bq; val bz = br; val by = -bx - bz; return maxOf(abs(ax - bx), abs(ay - by), abs(az - bz)) }
    private fun unpackRuntimeItem(item: String): List<String> = if (item.startsWith("combo:")) item.removePrefix("combo:").split('+').filter(String::isNotBlank) else listOf(item)
    private fun merge(target: MutableMap<String, Double>, source: Map<String, Double>, prefix: String = "") { source.forEach { (key, value) -> target[prefix + key] = (target[prefix + key] ?: 0.0) + value } }
    private fun Map<String, Double>.value(key: String) = this[key] ?: 0.0

    companion object {
        const val BOARD_COLUMNS = 7
        const val BOARD_ROWS = 8
        private const val OVERTIME_START_MS = 30_000L
        private val EVEN_NEIGHBORS = arrayOf(-1 to 0, 1 to 0, -1 to -1, 0 to -1, -1 to 1, 0 to 1)
        private val ODD_NEIGHBORS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 1 to -1, 0 to 1, 1 to 1)
    }
}
