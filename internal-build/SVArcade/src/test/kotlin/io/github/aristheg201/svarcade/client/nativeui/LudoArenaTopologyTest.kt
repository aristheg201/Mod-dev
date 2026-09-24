package io.github.aristheg201.svarcade.client.nativeui

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LudoArenaTopologyTest {
    @Test fun `authored ludo route is a closed cross topology rather than a rectangular debug grid`() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/assets/svarcade/arenas/ludo.json"))
        val arena = stream.reader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        assertEquals(15, arena.boardColumns)
        assertEquals(15, arena.boardRows)
        assertEquals(52, arena.boardAnchors.size)
        assertEquals(52, arena.boardAnchors.toSet().size)
        assertTrue(arena.boardAnchors.any { it.x == 0f && it.y == 7f })
        assertTrue(arena.boardAnchors.any { it.x == 14f && it.y == 7f })
        assertTrue(arena.boardAnchors.any { it.x == 7f && it.y == 0f })
        assertTrue(arena.boardAnchors.any { it.x == 7f && it.y == 14f })
        assertNotEquals(arena.boardAnchor(13), ArenaPoint(0f, 1f))
        assertEquals("move", arena.interactionAt(7f, 7f)?.action)
    }
}
