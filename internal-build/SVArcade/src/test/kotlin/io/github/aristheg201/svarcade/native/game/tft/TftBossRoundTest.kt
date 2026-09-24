package io.github.aristheg201.svarcade.native.game.tft

import java.net.JarURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TftBossRoundTest {
    @Test
    fun `bundled boss schedule resolves to real authored PvE encounters`() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val bosses = set.roundSchedule.filter { it.type == "boss" }
        assertEquals(setOf("6-7", "7-7"), bosses.map { it.label }.toSet())
        assertEquals(9, set.pveRounds.size)
        bosses.forEach { scheduled ->
            val encounter = assertNotNull(
                set.pveRounds.firstOrNull { it.round == (scheduled.pve ?: scheduled.label) },
                "Boss ${scheduled.label} must resolve to authored PvE data"
            )
            assertTrue(encounter.enemies.isNotEmpty())
            assertEquals("boss_cache", encounter.lootTable)
            assertTrue(encounter.lootRolls >= 5)
        }
        assertTrue(set.pveRounds.single { it.round == "6-7" }.enemies.any { it.unit == "mv_godzilla" && it.star == 3 })
        assertTrue(set.pveRounds.single { it.round == "7-7" }.enemies.any { it.unit == "mv_ghidorah" && it.star == 3 })
    }

    @Test
    fun `validator rejects boss schedule when its encounter is missing`() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(pveRounds = set.pveRounds.filterNot { it.round == "6-7" }))
        }
    }

    @Test
    fun `boss definitions are packaged with the runtime set`() {
        val url = assertNotNull(
            TftSetRegistry::class.java.getResource("/data/svarcade/tft/sets/kanto_rising/bosses.json")
        )
        if (System.getProperty("svarcade.test.packaged") == "true") {
            assertEquals("jar", url.protocol)
            val origin = TftSetRegistry::class.java.protectionDomain.codeSource.location
            assertEquals(origin, (url.openConnection() as JarURLConnection).jarFileURL)
        }
    }
}
