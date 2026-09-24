package io.github.aristheg201.svarcade.native.game.tft

import kotlin.test.*

class TftPersistentEvolutionTest {
    private val set=TftSetRegistry.bundled("kanto_rising")
    private val definitions=set.units.associateBy{it.id}

    @Test fun `hoopa permanently evolves exactly after three completed combats`() {
        var unit=TftOwnedUnit("u1","hoopa_sukuna",1, mutableListOf("sword"), poolCopies=1, poolUnitId="hoopa_sukuna")
        val one=TftPermanentEvolution.advance(unit,definitions)
        assertEquals("hoopa_sukuna",one.unit.unitId);assertEquals(1,one.unit.combatRounds);assertNull(one.evolvedFrom)
        val two=TftPermanentEvolution.advance(one.unit,definitions)
        assertEquals("hoopa_sukuna",two.unit.unitId);assertEquals(2,two.unit.combatRounds);assertNull(two.evolvedFrom)
        val three=TftPermanentEvolution.advance(two.unit,definitions)
        assertEquals("hoopa_unbound_sukuna",three.unit.unitId);assertEquals(0,three.unit.combatRounds)
        assertEquals("hoopa_sukuna",three.evolvedFrom)
        assertEquals("hoopa_sukuna",three.unit.poolSourceUnitId())
        assertEquals(listOf("sword"),three.unit.items)
        assertEquals("u1",three.unit.instanceId)
        assertTrue(definitions.getValue(three.unit.unitId).presentation.resolverAspects().containsAll(setOf("sukuna","unbound")))
    }

    @Test fun `non evolving units remain unchanged`() {
        val unit=TftOwnedUnit("u2","pikachu")
        assertEquals(TftPermanentEvolution.Result(unit),TftPermanentEvolution.advance(unit,definitions))
    }
}
