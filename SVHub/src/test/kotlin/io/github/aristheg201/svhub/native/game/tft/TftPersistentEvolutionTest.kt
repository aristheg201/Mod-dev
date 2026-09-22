package io.github.aristheg201.svhub.native.game.tft

import kotlin.test.*

class TftPersistentEvolutionTest {
    @Test fun `owned unit keeps pool lineage across permanent evolution`() {
        val base = TftOwnedUnit("u1","hoopa_sukuna",1, mutableListOf("sword"), poolCopies=1, combatRounds=2, poolUnitId="hoopa_sukuna")
        val evolved = base.copy(unitId="hoopa_unbound_sukuna", combatRounds=0, poolUnitId=base.poolSourceUnitId())
        assertEquals("hoopa_sukuna", evolved.poolSourceUnitId())
        assertEquals(listOf("sword"), evolved.items)
        assertEquals("u1", evolved.instanceId)
    }
}
