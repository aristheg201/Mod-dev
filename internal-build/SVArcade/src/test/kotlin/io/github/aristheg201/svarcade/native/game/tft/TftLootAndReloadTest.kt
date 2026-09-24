package io.github.aristheg201.svarcade.native.game.tft

import java.nio.file.Files
import kotlin.test.*

class TftLootAndReloadTest {
    @Test fun bundledLootTablesCoverDeterministicRewardKinds() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertEquals(setOf("standard_pve", "boss_cache"), set.lootTables.map { it.id }.toSet())
        val types = set.lootTables.flatMap { table -> table.entries.map { it.type } }.toSet()
        assertTrue(setOf("gold", "component", "full_item", "unit", "xp", "free_reroll", "special", "choice").all(types::contains))
        assertTrue(set.pveRounds.all { it.lootTable != null && it.lootRolls > 0 })
    }

    @Test fun failedTransactionalReloadRetainsPublishedSnapshot() {
        val root = Files.createTempDirectory("svarcade-tft-reload")
        try {
            TftSetRegistry.start(root)
            val before = TftSetRegistry.active()
            Files.writeString(root.resolve("active-set.json"), "{ broken")
            assertTrue(TftSetRegistry.reload().isFailure)
            assertSame(before, TftSetRegistry.active())
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
