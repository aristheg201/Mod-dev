package io.github.aristheg201.svarcade.native.game.tft

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TftPlayerModifierTest {
    @Test fun additiveCapabilitiesStackInDefinitionOrder() {
        val modifiers = TftPlayerModifierSet.compile(listOf(
            mapOf(TftPlayerModifier.XP_GAIN_FLAT to 2.0, TftPlayerModifier.BOARD_CAPACITY to 1.0),
            mapOf(TftPlayerModifier.XP_GAIN_FLAT to 3.0, TftPlayerModifier.BOARD_CAPACITY to 2.0)
        ))
        assertEquals(5.0, modifiers.value(TftPlayerModifier.XP_GAIN_FLAT))
        assertEquals(8.0, modifiers.apply(TftPlayerModifier.BOARD_CAPACITY, 5.0))
    }

    @Test fun maximumCapabilitiesSelectStrongestValueWithoutLoweringRulesetBase() {
        val modifiers = TftPlayerModifierSet.compile(listOf(
            mapOf(TftPlayerModifier.INTEREST_CAP to 7.0),
            mapOf(TftPlayerModifier.INTEREST_CAP to 6.0)
        ))
        assertEquals(7.0, modifiers.apply(TftPlayerModifier.INTEREST_CAP, 5.0))
        assertEquals(9.0, modifiers.apply(TftPlayerModifier.INTEREST_CAP, 9.0))
    }

    @Test fun rejectsNonFiniteModifierValues() {
        assertFailsWith<IllegalArgumentException> {
            TftPlayerModifierSet.compile(listOf(mapOf(TftPlayerModifier.INCOME_FLAT to Double.NaN)))
        }
    }
}
