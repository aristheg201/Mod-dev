package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TftOverrideCompatibilityTest {
    @Test
    fun `stale override using bundled id cannot mask a newer production roster`() = inTempDirectory { root ->
        val bundled = TftSetRegistry.bundled("kanto_rising")
        val stale = bundled.copy(
            augments = bundled.augments + bundled.augments.first().copy(
                id = "stale_extra_augment",
                name = "Stale Extra Augment"
            )
        )
        val path = root.resolve("active-set.json")
        Files.writeString(path, Gson().toJson(stale))

        TftSetRegistry.start(root)

        assertEquals(bundled.augments.size, TftSetRegistry.active().augments.size)
        assertEquals(bundled.units.size, TftSetRegistry.active().units.size)
        assertTrue(Files.readString(path).contains("stale_extra_augment"), "The user's stale file must remain untouched")
        val publishedBeforeReload = TftSetRegistry.active()
        assertTrue(TftSetRegistry.reload().isFailure, "Live reload must reject a stale built-in override")
        assertTrue(publishedBeforeReload === TftSetRegistry.active(), "Rejected live reload must retain the published snapshot")
    }

    @Test
    fun `intentional custom set id may still provide a different content shape`() = inTempDirectory { root ->
        val bundled = TftSetRegistry.bundled("kanto_rising")
        val custom = bundled.copy(
            id = "custom_roster",
            augments = bundled.augments + bundled.augments.first().copy(
                id = "custom_extra_augment",
                name = "Custom Extra Augment"
            )
        )
        Files.writeString(root.resolve("active-set.json"), Gson().toJson(custom))

        TftSetRegistry.start(root)

        assertEquals("custom_roster", TftSetRegistry.active().id)
        assertEquals(bundled.augments.size + 1, TftSetRegistry.active().augments.size)
        assertEquals("custom_roster", TftSetRegistry.reload().getOrThrow().id)
    }

    private fun inTempDirectory(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("svhub-tft-override-")
        try {
            block(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
