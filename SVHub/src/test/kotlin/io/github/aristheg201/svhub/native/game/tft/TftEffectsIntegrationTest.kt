package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import io.github.aristheg201.svhub.engine.*
import kotlin.test.*

class TftEffectsIntegrationTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private fun graph(op: String, selector: String = "self", values: Map<String, Double> = emptyMap(),
                      strings: Map<String, String> = emptyMap(), children: List<EffectDefinition> = emptyList()) =
        EffectDefinition(op, selector, values, strings, children, emptyList())
    private fun trigger(event: BattleEvent, graph: List<EffectDefinition>) =
        TriggerDefinition("test:${event.name.lowercase()}", event, 0, 1.0, 0, 1, false, false, emptyList(), graph)
    private fun engine(set: TftSetDefinition) = TftCombatEngine(set, "a", mapOf(3 to TftOwnedUnit("a:1", set.units[0].id)),
        emptyList(), "b", mapOf(3 to TftOwnedUnit("b:1", set.units[1].id)), emptyList(), 90L)

    @Test fun delayedCastAndPendingEffectsSurviveJsonRecovery() {
        val actor = base.units[0].copy(stats = base.units[0].stats.copy(hp = 10_000, manaStart = 100, manaMax = 100, range = 6),
            ability = TftAbilityDefinition(castDelayMs = 300,
                graph = listOf(graph("shield", values = mapOf("amount" to 321.0)))),
            triggers = listOf(trigger(BattleEvent.ON_CAST_END, listOf(graph("gain_stack", strings = mapOf("id" to "completed"))))))
        val enemy = base.units[1].copy(stats = base.units[1].stats.copy(hp = 10_000, attackDamage = 1, attackSpeed = 0.1))
        val set = base.copy(units = listOf(actor, enemy) + base.units.drop(2))
        val running = engine(set)
        running.step(50)
        assertEquals(0, running.units.first { it.instanceId == "a:1" }.shield)
        val gson = Gson()
        val restored = TftCombatEngine(set, gson.fromJson(gson.toJson(running.snapshotState()), TftCombatSnapshot::class.java))
        repeat(6) { running.step(50); restored.step(50) }
        assertEquals(running.snapshotState(), restored.snapshotState())
        assertEquals(321, running.units.first { it.instanceId == "a:1" }.shield)
        assertEquals(1, running.snapshotState().effects!!.units().first { it.id() == "a:1" }.stacks()["completed"])
    }

    @Test fun transformationAndCloneUseDefinitionIdentityWithoutChangingOwnedUnit() {
        val target = base.units[2]
        val actor = base.units[0].copy(triggers = listOf(trigger(BattleEvent.ON_COMBAT_START, listOf(
            graph("transform", strings = mapOf("definition" to target.id)),
            graph("copy_unit", values = mapOf("count" to 2.0, "inheritance" to 0.6))
        ))))
        val set = base.copy(units = listOf(actor) + base.units.drop(1))
        val combat = engine(set)
        assertEquals(target.id, combat.units.first { it.instanceId == "a:1" }.definition.id)
        val clones = combat.snapshotState().effects!!.units().filter { it.summoner() == "a:1" }
        assertEquals(2, clones.size)
        assertTrue(clones.all { it.owner() == "a" && it.definitionId() == target.id })
        assertEquals(target.stats.hp * 0.6, clones[0].hp())
        assertEquals(base.units[0].id, actor.id)
    }

    @Test fun fullItemAndTraitTriggersExecuteThroughTheSameRuntime() {
        val shield = graph("shield", values = mapOf("amount" to 27.0))
        val trait = base.traits[0].copy(tiers = listOf(TftTraitTier(threshold = 1,
            triggers = listOf(trigger(BattleEvent.ON_COMBAT_START, listOf(shield))))))
        val item = base.fullItems[0].copy(triggers = listOf(trigger(BattleEvent.ON_ATTACK_START, listOf(shield))))
        val set = base.copy(traits = listOf(trait) + base.traits.drop(1), fullItems = listOf(item) + base.fullItems.drop(1),
            units = listOf(base.units[0].copy(traits = listOf(trait.id))) + base.units.drop(1))
        val combat = TftCombatEngine(set, "a", mapOf(3 to TftOwnedUnit("a:1", set.units[0].id, items = mutableListOf("full:${item.id}"))),
            emptyList(), "b", mapOf(3 to TftOwnedUnit("b:1", set.units[1].id)), emptyList(), 9L)
        assertEquals(27, combat.units.first { it.instanceId == "a:1" }.shield)
        combat.step(50)
        assertTrue(combat.snapshotState().effects!!.triggers().keys.any { "on_attack_start" in it })
    }

    @Test fun invalidSummonReferenceIsRejectedBeforeMatchCreation() {
        val unit = base.units[0].copy(ability = TftAbilityDefinition(graph = listOf(graph("summon", strings = mapOf("definition" to "test:missing")))))
        val error = assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(base.copy(units = listOf(unit) + base.units.drop(1))) }
        assertTrue(error.message.orEmpty().contains(".definition"))
        assertTrue(error.message.orEmpty().contains(unit.id))
    }
}
