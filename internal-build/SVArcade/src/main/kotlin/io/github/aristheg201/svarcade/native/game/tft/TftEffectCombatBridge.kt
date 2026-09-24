package io.github.aristheg201.svarcade.native.game.tft

import io.github.aristheg201.svarcade.engine.*
import kotlin.math.roundToInt

/** Adapts TFT definitions to the shared runtime. It contains no roster identities. */
internal class TftEffectCombatBridge(
    private val set: TftSetDefinition,
    private val visibleUnits: MutableList<TftCombatUnit>,
    private val augments: Map<String, List<String>>,
    seed: Long,
    saved: BattleRuntime.Snapshot? = null,
    recovering: Boolean = false,
    savedConvergenceOriginals: Map<String, BattleUnit.Snapshot>? = null
) {
    private val convergenceOriginals = savedConvergenceOriginals.orEmpty().toMutableMap()
    fun convergenceSnapshot(): Map<String, BattleUnit.Snapshot> = convergenceOriginals.toMap()
    private val definitions = set.units.associateBy { it.id }
    private val board = BattleBoard(set.rules.boardColumns, set.rules.boardRows * 2, true, emptySet())
    private val limits = BattleRuntime.Limits(set.rules.formationCells * 2, 4096, 1024, 2048, 128)
    private val factory = BattleRuntime.UnitFactory { definition, id, owner, team, cell ->
        val def = definitions[definition] ?: error("Unknown summoned/transformed definition $definition")
        BattleUnit(id, owner, team, definition, cell, baseStats(def)).also { unit ->
            unit.traits += def.traits; unit.tags += def.tags; unit.triggers += def.triggers
        }
    }
    val runtime: BattleRuntime = if (saved != null) BattleRuntime.restore(board, limits, factory, set.effectGraphs, saved)
        else BattleRuntime(board, limits, seed, factory, set.effectGraphs).also { world ->
            visibleUnits.forEach { source ->
                val unit = factory.create(source.definition.id, source.instanceId, source.ownerId, source.team, source.cell)
                copyToRuntime(source, unit)
                val elite = visibleUnits.firstOrNull { candidate -> candidate.team == source.team &&
                    candidate.instanceId != source.instanceId && candidate.definition.elite != null &&
                    candidate.definition.team == source.definition.team && source.definition.team.isNotBlank() }
                    ?.definition?.elite
                if (elite != null) unit.stats[Stat.DAMAGE_AMPLIFICATION] = elite.damageMultiplier - 1.0
                unit.items += source.items
                source.items.forEach { id ->
                    unit.triggers += if (id.startsWith("full:")) set.fullItems.first { it.id == id.removePrefix("full:") }.triggers
                        else set.components.firstOrNull { it.id == id }?.triggers.orEmpty()
                }
                augments[source.ownerId].orEmpty().forEach { id -> unit.triggers += set.augments.first { it.id == id }.triggers }
                val counts = visibleUnits.filter { it.team == source.team }.distinctBy { it.definition.id }
                    .flatMap { it.definition.traits }.groupingBy { it }.eachCount()
                set.traits.forEach traitLoop@{ trait ->
                    val tier = trait.tiers.lastOrNull { it.threshold <= (counts[trait.id] ?: 0) } ?: return@traitLoop
                    unit.triggers += tier.teamTriggers
                    if (trait.id in source.definition.traits) unit.triggers += tier.triggers
                }
                unit.triggers.forEach { world.validate(it, "unit ${source.definition.id}.trigger ${it.id()}") }
                world.add(unit)
            }
        }

    init {
        if (saved == null && !recovering) {
            activateConvergences()
            runtime.units().filter { it.alive() }.forEach { runtime.emit(BattleEvent.ON_COMBAT_START, it, it, it, 0.0, 0) }
            runtime.drain()
        }
        restoreConvergencesIfReady()
        syncFromRuntime()
    }

    private fun activateConvergences() {
        val runtimeUnits = runtime.units().toList()
        runtimeUnits.groupBy { it.team }.forEach { (_, allies) ->
            set.convergences.forEach { convergence ->
                val selected = convergence.units.sorted().mapNotNull { definition ->
                    allies.filter { it.definitionId == definition && it.alive() }.minByOrNull { it.id }
                }
                if (selected.size != convergence.units.size) return@forEach
                val marker = "convergence:" + convergence.id
                selected.forEach { member ->
                    member.stacks[marker + ":member:" + member.definitionId] = 1
                }
                val leader = selected.minBy { it.id }
                leader.stacks[marker + ":leader"] = 1
                selected.filter { it !== leader }.forEach { member ->
                    member.hp = 0.0
                    member.deadAt = runtime.now()
                    runtime.cue("despawn", member, null, convergence.id, 0.0)
                }
                convergenceOriginals[leader.id] = leader.snapshot()
                runtime.transform(leader, convergence.summon, false, 0)
                runtime.cue("convergence", leader, null, convergence.id, 1.0)
            }
        }
    }

    private fun restoreConvergencesIfReady() {
        set.convergences.forEach { convergence ->
            val marker = "convergence:" + convergence.id
            val leaders = runtime.units().filter {
                it.stacks.getOrDefault(marker + ":leader", 0) > 0 && it.definitionId == convergence.summon && !it.alive()
            }
            leaders.forEach { leader ->
                val members = runtime.units().filter { unit ->
                    unit.team == leader.team && unit.owner == leader.owner &&
                        unit.stacks.keys.any { it.startsWith(marker + ":member:") }
                }
                members.forEach memberLoop@{ member ->
                    val original = member.stacks.keys.firstOrNull { it.startsWith(marker + ":member:") }
                        ?.substringAfter(marker + ":member:") ?: return@memberLoop
                    if (member === leader) {
                        runtime.transform(member, original, false, 0)
                        convergenceOriginals.remove(member.id)?.let { saved ->
                            member.stats.clear(); member.stats.putAll(saved.stats())
                            member.traits.clear(); member.traits.addAll(saved.traits())
                            member.tags.clear(); member.tags.addAll(saved.tags())
                            member.triggers.clear(); member.triggers.addAll(saved.triggers())
                            member.modifiers.clear()
                            member.modifiers.putAll(saved.modifiers().filterValues { it.expiresAt() <= 0 || it.expiresAt() > runtime.now() })
                        }
                    }
                    member.hp = member.stat(Stat.MAX_HP).coerceAtLeast(1.0)
                    member.mana = 0.0
                    member.deadAt = -1L
                    member.target = null
                    member.stacks.keys.removeIf { it.startsWith(marker + ":") }
                    runtime.emit(BattleEvent.ON_REVIVE, member, member, member, member.hp, 0)
                    runtime.cue("spawn", member, null, convergence.id, 1.0)
                }
            }
            if (leaders.isNotEmpty()) runtime.drain()
        }
    }

    fun advance(deltaMillis: Long) {
        runtime.advance(deltaMillis)
        restoreConvergencesIfReady()
        syncFromRuntime()
    }

    fun basic(source: TftCombatUnit, target: TftCombatUnit, multiplier: Double) {
        val actor = runtime.unit(source.instanceId) ?: return
        val victim = runtime.unit(target.instanceId) ?: return
        if (actor.hasStatus("disarm")) return
        runtime.emit(BattleEvent.ON_ATTACK_START, actor, actor, victim, 0.0, 0); runtime.drain()
        val crit = runtime.random().nextDouble() < actor.stat(Stat.CRIT_CHANCE)
        runtime.emit(BattleEvent.ON_ATTACK, actor, actor, victim, 0.0, 0)
        if (crit) runtime.emit(BattleEvent.ON_CRIT, actor, actor, victim, 0.0, 0)
        DamagePipeline.damage(runtime, actor, victim, actor.stat(Stat.ATTACK) * multiplier, DamagePipeline.Type.PHYSICAL, crit, 0.0, 0)
        runtime.emit(BattleEvent.ON_BASIC_HIT, actor, actor, victim, 0.0, 0)
        DamagePipeline.mana(runtime, actor, actor.stat(Stat.MANA_ON_ATTACK), 0)
        runtime.drain(); restoreConvergencesIfReady(); syncFromRuntime()
    }

    fun cast(source: TftCombatUnit, target: TftCombatUnit, multiplier: Double) {
        val actor = runtime.unit(source.instanceId) ?: return
        val victim = runtime.unit(target.instanceId)
        if (!canCast(source.instanceId)) return
        actor.mana = 0.0
        actor.casts++
        val ability = source.definition.ability
        val graph = ability.graph.ifEmpty { TftAbilityGraphCompiler.legacy(ability, source.star, source.abilityPower, multiplier) }
        actor.castReadyAt = runtime.now() + ability.castDelayMs
        runtime.emit(BattleEvent.ON_CAST_START, actor, actor, victim, 0.0, 0)
        runtime.drain()
        val castGraph = listOf(eventNode(BattleEvent.ON_CAST)) + graph + eventNode(BattleEvent.ON_CAST_END)
        if (ability.castDelayMs > 0) runtime.schedule(ability.castDelayMs, actor, victim, castGraph, 0)
        else runtime.execute(actor, victim, castGraph, 0)
        runtime.drain()
        restoreConvergencesIfReady()
        syncFromRuntime()
    }

    private fun eventNode(event: BattleEvent) = EffectDefinition("emit_event", "self", emptyMap(), mapOf("event" to event.name), emptyList(), emptyList())

    fun canCast(id: String): Boolean = runtime.unit(id)?.let { canAct(id) && !it.hasStatus("silence") && runtime.now() >= it.castReadyAt } ?: false
    fun targetChanged(source: TftCombatUnit, target: TftCombatUnit?) {
        val actor = runtime.unit(source.instanceId) ?: return
        val previous = actor.target
        actor.target = target?.instanceId
        source.targetId = actor.target
        if (previous != actor.target && actor.target != null) {
            runtime.emit(if (previous == null) BattleEvent.ON_TARGET_ACQUIRED else BattleEvent.ON_TARGET_CHANGED, actor, actor, runtime.unit(actor.target), 0.0, 0)
            runtime.drain()
            syncFromRuntime()
        }
    }

    fun canAct(id: String): Boolean = runtime.unit(id)?.let { it.alive() && !it.hasStatus("stun") && !it.hasStatus("fear") && runtime.now() >= it.castReadyAt } ?: false
    fun canMove(id: String): Boolean = runtime.unit(id)?.let { canAct(id) && !it.hasStatus("root") } ?: false
    fun moved(source: TftCombatUnit, from: Int, dash: Boolean = false) {
        val actor = runtime.unit(source.instanceId) ?: return
        actor.cell = source.cell
        if (from != source.cell) { runtime.emit(if (dash) BattleEvent.ON_DASH else BattleEvent.ON_MOVE, actor, actor, actor, 0.0, 0); runtime.drain(); syncFromRuntime() }
    }
    fun finish() {
        runtime.units().toList().forEach { runtime.emit(BattleEvent.ON_COMBAT_END, it, it, it, 0.0, 0) }
        runtime.drain(); syncFromRuntime()
    }
    fun snapshot(): BattleRuntime.Snapshot = runtime.snapshot()

    private fun syncFromRuntime() {
        val byId = visibleUnits.associateBy { it.instanceId }
        runtime.units().forEach { source ->
            val def = definitions.getValue(source.definitionId)
            val target = byId[source.id] ?: TftCombatUnit(source.id, source.owner, source.team, def,
                source.stat(Stat.STAR).toInt().coerceAtLeast(1), source.items.toList(), source.cell,
                source.stat(Stat.MAX_HP).roundToInt(), source.hp.roundToInt(), source.stat(Stat.MAX_MANA).roundToInt(), source.mana.roundToInt(),
                source.stat(Stat.ATTACK), source.stat(Stat.ARMOR), source.stat(Stat.SPDEF), source.stat(Stat.ATTACK_SPEED),
                source.stat(Stat.RANGE).roundToInt(), source.stat(Stat.CRIT_CHANCE), source.stat(Stat.CRIT_MULTIPLIER), source.stat(Stat.AP) / 100,
                source.stat(Stat.MANA_ON_ATTACK).roundToInt()).also(visibleUnits::add)
            target.definition = def; target.cell = source.cell; target.hp = if (source.alive()) source.hp.roundToInt().coerceAtLeast(1) else 0
            target.maxHp = source.stat(Stat.MAX_HP).roundToInt().coerceAtLeast(1)
            target.maxMana = source.stat(Stat.MAX_MANA).roundToInt()
            target.range = source.stat(Stat.RANGE).roundToInt()
            target.critChance = source.stat(Stat.CRIT_CHANCE)
            target.critMultiplier = source.stat(Stat.CRIT_MULTIPLIER)
            target.abilityPower = source.stat(Stat.AP) / 100.0
            target.items = source.items.toList()
            target.mana = source.mana.roundToInt(); target.shield = source.shield.roundToInt(); target.casts = source.casts
            target.attackDamage = source.stat(Stat.ATTACK); target.defense = source.stat(Stat.ARMOR); target.specialDefense = source.stat(Stat.SPDEF)
            target.attackSpeed = source.stat(Stat.ATTACK_SPEED).coerceIn(0.1, 5.0)
            target.damageDone = source.damageDone.toLong(); target.healingDone = source.healingDone.toLong()
            target.targetId = source.target
        }
        visibleUnits.removeIf { runtime.unit(it.instanceId) == null }
    }

    private fun copyToRuntime(s: TftCombatUnit, u: BattleUnit) {
        u.stats.putAll(mapOf(Stat.MAX_HP to s.maxHp.toDouble(), Stat.ATTACK to s.attackDamage, Stat.AP to s.abilityPower * 100,
            Stat.ARMOR to s.defense, Stat.SPDEF to s.specialDefense, Stat.ATTACK_SPEED to s.attackSpeed,
            Stat.RANGE to s.range.toDouble(), Stat.MAX_MANA to s.maxMana.toDouble(), Stat.MANA_ON_ATTACK to s.manaOnAttack.toDouble(),
            Stat.MANA_ON_HIT to 5.0, Stat.CRIT_CHANCE to s.critChance, Stat.CRIT_MULTIPLIER to s.critMultiplier, Stat.STAR to s.star.toDouble()))
        u.hp = s.hp.toDouble(); u.mana = s.mana.toDouble(); u.shield = s.shield.toDouble()
        u.damageDone = s.damageDone.toDouble(); u.healingDone = s.healingDone.toDouble(); u.casts = s.casts
    }

    private fun baseStats(def: TftUnitDefinition): Map<Stat, Double> = mapOf(
        Stat.MAX_HP to def.stats.hp.toDouble(), Stat.ATTACK to def.stats.attackDamage.toDouble(), Stat.AP to 100.0,
        Stat.ARMOR to def.stats.defense.toDouble(), Stat.SPDEF to def.stats.specialDefense.toDouble(), Stat.ATTACK_SPEED to def.stats.attackSpeed,
        Stat.RANGE to def.stats.range.toDouble(), Stat.MAX_MANA to def.stats.manaMax.toDouble(), Stat.MANA_ON_ATTACK to 10.0,
        Stat.MANA_ON_HIT to 5.0, Stat.CRIT_CHANCE to def.stats.critChance, Stat.CRIT_MULTIPLIER to def.stats.critMultiplier,
        Stat.STAR to 1.0, Stat.COST to def.cost.toDouble(), Stat.MOVE_SPEED to def.stats.moveSpeed)
}
