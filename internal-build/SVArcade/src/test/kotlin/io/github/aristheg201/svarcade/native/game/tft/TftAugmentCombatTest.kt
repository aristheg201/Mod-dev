package io.github.aristheg201.svarcade.native.game.tft

import kotlin.test.Test
import kotlin.test.assertEquals

class TftAugmentCombatTest {
    private val base = TftSetRegistry.bundled("kanto_rising")

    @Test
    fun `trait augment affects only units carrying its authored trait`() {
        val bug = base.units.first().copy(
            id = "test_bug",
            traits = listOf("bug"),
            stats = base.units.first().stats.copy(hp = 1000)
        )
        val plain = base.units[1].copy(
            id = "test_plain",
            traits = emptyList(),
            stats = base.units[1].stats.copy(hp = 1000)
        )
        val enemy = base.units[2].copy(
            id = "test_enemy",
            traits = emptyList(),
            stats = base.units[2].stats.copy(hp = 1000)
        )
        val augment = TftAugmentDefinition(
            id = "bug_probe",
            name = "Bug Probe",
            description = "Test",
            effects = mapOf("defense" to 7.0),
            traitEffects = mapOf("bug" to mapOf("hp_pct" to 0.5)),
            tags = setOf("bug", "test"),
            tier = "Gold"
        )
        val set = base.copy(
            units = listOf(bug, plain, enemy) + base.units.drop(3),
            augments = base.augments + augment
        )
        val combat = TftCombatEngine(
            set,
            "a",
            mapOf(
                0 to TftOwnedUnit("bug", bug.id),
                1 to TftOwnedUnit("plain", plain.id)
            ),
            listOf(augment.id),
            "b",
            mapOf(0 to TftOwnedUnit("enemy", enemy.id)),
            emptyList(),
            17L
        )

        val bugRuntime = combat.units.single { it.instanceId == "bug" }
        val plainRuntime = combat.units.single { it.instanceId == "plain" }
        assertEquals(1500, bugRuntime.maxHp)
        assertEquals(1000, plainRuntime.maxHp)
        assertEquals(bug.stats.defense + 7.0, bugRuntime.defense)
        assertEquals(plain.stats.defense + 7.0, plainRuntime.defense)
    }
}
