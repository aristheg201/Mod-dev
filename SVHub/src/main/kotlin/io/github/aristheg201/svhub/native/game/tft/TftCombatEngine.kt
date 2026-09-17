package io.github.aristheg201.svhub.native.game.tft

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
    val definition: TftUnitDefinition,
    val star: Int,
    val items: List<String>,
    var cell: Int,
    val maxHp: Int,
    var hp: Int,
    val maxMana: Int,
    var mana: Int,
    val attackDamage: Double,
    val defense: Double,
    val specialDefense: Double,
    var attackSpeed: Double,
    val range: Int,
    val critChance: Double,
    val critMultiplier: Double,
    val abilityPower: Double,
    val manaOnAttack: Int,
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
    private val rng = Random(seed)
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

    init {
        units += createTeam(0, team0Owner, team0Board, team0Augments)
        units += createTeam(1, team1Owner, team1Board, team1Augments)
        if (units.none { it.team == 0 } || units.none { it.team == 1 }) resolve(false)
    }

    fun step(deltaMillis: Long): Boolean {
        if (finished) return false
        val dt = deltaMillis.coerceIn(20L, 250L)
        elapsedMs += dt
        val aliveAtStart = units.count { it.alive }
        val occupancy = linkedMapOf<Int, TftCombatUnit>()
        units.filter { it.alive }.forEach { occupancy[it.cell] = it }

        val overtimeMultiplier = if (elapsedMs > OVERTIME_START_MS) 1.0 + ((elapsedMs - OVERTIME_START_MS) / 5_000.0).coerceAtMost(2.0) * 0.35 else 1.0
        val order = units.filter { it.alive }.sortedWith(compareBy<TftCombatUnit> { it.team }.thenBy { it.instanceId })
        for (unit in order) {
            if (!unit.alive) continue
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
                unit.targetId = target?.instanceId
            }
            target ?: continue
            val distance = hexDistance(unit.cell, target.cell)
            if (distance > unit.range) {
                val moved = moveToward(unit, target, occupancy)
                if (moved) continue
            }
            if (unit.mana >= unit.maxMana && unit.maxMana > 0) {
                cast(unit, target, overtimeMultiplier)
                continue
            }
            if (unit.attackCooldownMs <= 0.0 && hexDistance(unit.cell, target.cell) <= unit.range) {
                basicAttack(unit, target, overtimeMultiplier)
            }
        }

        cleanupTargets()
        val team0Alive = units.any { it.alive && it.team == 0 }
        val team1Alive = units.any { it.alive && it.team == 1 }
        if (!team0Alive || !team1Alive) resolve(false)
        else if (elapsedMs >= maxDurationMs) resolve(true)
        return aliveAtStart != units.count { it.alive } || finished
    }

    private fun createTeam(team: Int, owner: String, board: Map<Int, TftOwnedUnit>, augments: List<String>): List<TftCombatUnit> {
        val traitCounts = board.values.mapNotNull { unitDefs[it.unitId] }.flatMap { it.traits }.groupingBy { it }.eachCount()
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
            merge(effects, teamEffects)
            merge(effects, augmentEffects)
            def.traits.forEach { trait ->
                activeTraitEffects.filterKeys { it.startsWith("$trait|") }.forEach { (key, value) -> effects[key.substringAfter('|')] = (effects[key.substringAfter('|')] ?: 0.0) + value }
            }
            owned.items.forEach { item ->
                if (item.startsWith("full:")) {
                    fullItemDefs[item.removePrefix("full:")]?.let { merge(effects, it.effects) }
                } else {
                    unpackRuntimeItem(item).forEach { component -> componentDefs[component]?.let { merge(effects, it.effects) } }
                }
            }
            val starMult = when (owned.star) { 2 -> 1.80; 3 -> 3.24; else -> 1.0 }
            val hp = (def.stats.hp * starMult * (1.0 + effects.value("hp_pct"))).roundToInt().coerceAtLeast(1)
            val ad = def.stats.attackDamage * starMult * (1.0 + effects.value("attack_pct"))
            val globalCell = formationToCombatCell(slot, team)
            TftCombatUnit(
                instanceId = owned.instanceId,
                ownerId = owner,
                team = team,
                definition = def,
                star = owned.star,
                items = owned.items.toList(),
                cell = globalCell,
                maxHp = hp,
                hp = hp,
                maxMana = def.stats.manaMax.coerceAtLeast(0),
                mana = (def.stats.manaStart + effects.value("mana_start").roundToInt()).coerceIn(0, def.stats.manaMax.coerceAtLeast(0)),
                attackDamage = ad,
                defense = def.stats.defense + effects.value("defense"),
                specialDefense = def.stats.specialDefense + effects.value("spdef"),
                attackSpeed = (def.stats.attackSpeed * (1.0 + effects.value("attack_speed_pct"))).coerceIn(0.2, 5.0),
                range = (def.stats.range + effects.value("range").roundToInt()).coerceIn(1, 6),
                critChance = (def.stats.critChance + effects.value("crit_chance")).coerceIn(0.0, 1.0),
                critMultiplier = (def.stats.critMultiplier + effects.value("crit_multiplier")).coerceAtLeast(1.0),
                abilityPower = 1.0 + effects.value("ability_power_pct"),
                manaOnAttack = (10 + effects.value("mana_on_attack").roundToInt()).coerceAtLeast(1)
            )
        }
    }

    private fun basicAttack(attacker: TftCombatUnit, target: TftCombatUnit, overtimeMultiplier: Double) {
        val crit = rng.nextDouble() < attacker.critChance
        var raw = attacker.attackDamage * if (crit) attacker.critMultiplier else 1.0
        raw *= overtimeMultiplier
        val damage = mitigate(raw, target.defense)
        applyDamage(attacker, target, damage.roundToInt().coerceAtLeast(1))
        attacker.mana = (attacker.mana + attacker.manaOnAttack).coerceAtMost(attacker.maxMana)
        attacker.attackCooldownMs = 1000.0 / attacker.attackSpeed
    }

    private fun cast(caster: TftCombatUnit, currentTarget: TftCombatUnit, overtimeMultiplier: Double) {
        val ability = caster.definition.ability
        val enemies = units.filter { it.alive && it.team != caster.team }
        val allies = units.filter { it.alive && it.team == caster.team }
        val target = when (ability.target) {
            "lowest_hp_enemy" -> enemies.minByOrNull { it.hp.toDouble() / it.maxHp }
            "farthest_enemy" -> enemies.maxByOrNull { hexDistance(caster.cell, it.cell) }
            "lowest_hp_ally" -> allies.minByOrNull { it.hp.toDouble() / it.maxHp }
            "self" -> caster
            else -> currentTarget
        } ?: return
        caster.mana = 0
        caster.casts++
        if (ability.dash > 0 && target.team != caster.team) dashToward(caster, target, ability.dash)
        val targets = if (ability.radius > 0 && target.team != caster.team) enemies.filter { hexDistance(it.cell, target.cell) <= ability.radius } else listOf(target)
        if (ability.damage > 0 && target.team != caster.team) {
            for (victim in targets) {
                val raw = ability.damage * caster.abilityPower * starSpellMultiplier(caster.star) * overtimeMultiplier
                val resistance = if (ability.damageType == "physical") victim.defense else victim.specialDefense
                val amount = if (ability.damageType == "true") raw else mitigate(raw, resistance)
                applyDamage(caster, victim, amount.roundToInt().coerceAtLeast(1))
                if (ability.stunMs > 0) victim.stunMs = max(victim.stunMs, ability.stunMs.toLong())
            }
        }
        if (ability.heal > 0) {
            val amount = (ability.heal * caster.abilityPower * starSpellMultiplier(caster.star)).roundToInt()
            val healed = minOf(amount, target.maxHp - target.hp).coerceAtLeast(0)
            target.hp += healed; caster.healingDone += healed
        }
        if (ability.shield > 0) target.shield += (ability.shield * caster.abilityPower * starSpellMultiplier(caster.star)).roundToInt()
        val execute = ability.effects["execute_below_pct"]
        if (execute != null && target.team != caster.team && target.alive && target.hp.toDouble() / target.maxHp <= execute) { caster.damageDone += target.hp; target.hp = 0 }
    }

    private fun moveToward(unit: TftCombatUnit, target: TftCombatUnit, occupancy: MutableMap<Int, TftCombatUnit>): Boolean {
        val currentDistance = hexDistance(unit.cell, target.cell)
        val next = neighbors(unit.cell).filter { it !in occupancy }.minWithOrNull(compareBy<Int> { hexDistance(it, target.cell) }.thenBy { it })?.takeIf { hexDistance(it, target.cell) < currentDistance } ?: return false
        occupancy.remove(unit.cell); unit.cell = next; occupancy[next] = unit
        unit.attackCooldownMs = max(unit.attackCooldownMs, 240.0 / unit.definition.stats.moveSpeed.coerceAtLeast(0.2)); return true
    }

    private fun dashToward(unit: TftCombatUnit, target: TftCombatUnit, cells: Int) {
        val occupied = units.filter { it.alive && it !== unit }.map { it.cell }.toSet()
        repeat(cells.coerceIn(1, 4)) { val current=hexDistance(unit.cell,target.cell);val next=neighbors(unit.cell).filterNot(occupied::contains).minByOrNull{hexDistance(it,target.cell)}?:return;if(hexDistance(next,target.cell)>=current)return;unit.cell=next }
    }

    private fun applyDamage(source:TftCombatUnit,target:TftCombatUnit,amount:Int){if(!target.alive||amount<=0)return;var remaining=amount;if(target.shield>0){val absorbed=minOf(target.shield,remaining);target.shield-=absorbed;remaining-=absorbed};if(remaining>0){target.hp=(target.hp-remaining).coerceAtLeast(0);source.damageDone+=remaining;target.mana=(target.mana+5).coerceAtMost(target.maxMana)}}
    private fun cleanupTargets(){val dead=units.filterNot{it.alive}.map{it.instanceId}.toSet();if(dead.isEmpty())return;units.filter{it.alive&&it.targetId in dead}.forEach{it.targetId=null}}
    private fun resolve(timeout:Boolean){if(finished)return;finished=true;val a=units.filter{it.alive&&it.team==0};val b=units.filter{it.alive&&it.team==1};val winner=when{a.isNotEmpty()&&b.isEmpty()->0;b.isNotEmpty()&&a.isEmpty()->1;timeout->{val ar=a.sumOf{it.hp.toDouble()/it.maxHp};val br=b.sumOf{it.hp.toDouble()/it.maxHp};when{ar>br+0.01->0;br>ar+0.01->1;else->null}};else->null};result=TftCombatResult(winner,a,b,timeout)}
    private fun mitigate(raw:Double,resistance:Double):Double=if(resistance>=0)raw*100.0/(100.0+resistance) else raw*(2.0-100.0/(100.0-resistance))
    private fun starSpellMultiplier(star:Int)=when(star){2->1.45;3->2.20;else->1.0}
    private fun formationToCombatCell(slot:Int,team:Int):Int{val col=slot%BOARD_COLUMNS;val row=(slot/BOARD_COLUMNS).coerceIn(0,3);return if(team==0)(row+4)*BOARD_COLUMNS+col else(3-row)*BOARD_COLUMNS+(BOARD_COLUMNS-1-col)}
    private fun neighbors(cell:Int):List<Int>{val row=cell/BOARD_COLUMNS;val col=cell%BOARD_COLUMNS;val offsets=if(row and 1==0)EVEN_NEIGHBORS else ODD_NEIGHBORS;return offsets.mapNotNull{(dc,dr)->val nc=col+dc;val nr=row+dr;if(nc in 0 until BOARD_COLUMNS&&nr in 0 until BOARD_ROWS)nr*BOARD_COLUMNS+nc else null}}
    private fun hexDistance(a:Int,b:Int):Int{val ar=a/BOARD_COLUMNS;val ac=a%BOARD_COLUMNS;val br=b/BOARD_COLUMNS;val bc=b%BOARD_COLUMNS;val aq=ac-(ar-(ar and 1))/2;val bq=bc-(br-(br and 1))/2;val ax=aq;val az=ar;val ay=-ax-az;val bx=bq;val bz=br;val by=-bx-bz;return maxOf(abs(ax-bx),abs(ay-by),abs(az-bz))}
    private fun unpackRuntimeItem(item:String):List<String>=if(item.startsWith("combo:"))item.removePrefix("combo:").split('+').filter(String::isNotBlank) else listOf(item)
    private fun merge(target:MutableMap<String,Double>,source:Map<String,Double>,prefix:String=""){source.forEach{(key,value)->target[prefix+key]=(target[prefix+key]?:0.0)+value}}
    private fun Map<String,Double>.value(key:String)=this[key]?:0.0

    companion object { const val BOARD_COLUMNS=7;const val BOARD_ROWS=8;private const val OVERTIME_START_MS=30_000L;private val EVEN_NEIGHBORS=arrayOf(-1 to 0,1 to 0,-1 to -1,0 to -1,-1 to 1,0 to 1);private val ODD_NEIGHBORS=arrayOf(-1 to 0,1 to 0,0 to -1,1 to -1,0 to 1,1 to 1) }
}
