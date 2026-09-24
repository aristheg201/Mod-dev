package io.github.aristheg201.svarcade.native.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplayerResignRegressionTest {
    private fun seat(id: String, name: String) = NativeSeat(id, name)

    @Test
    fun `ludo resignation permanently removes a player and last active player wins`() {
        val game = LudoSession(
            listOf(seat("a", "A"), seat("b", "B"), seat("c", "C")),
            seed = 11L
        )

        val first = game.act("a", "resign", emptyMap())
        assertTrue(first.accepted)
        val afterA = game.viewFor("b")
        assertFalse(afterA.finished)
        assertEquals("B", afterA.turn)
        assertTrue(afterA.fields.getValue("eliminated").split(',').contains("a"))
        assertFalse(game.act("a", "roll", emptyMap()).accepted)

        val second = game.act("b", "resign", emptyMap())
        assertTrue(second.accepted)
        val final = game.viewFor("c")
        assertTrue(final.finished)
        assertEquals("C", final.winner)
    }

    @Test
    fun `uno resignation continues multiplayer and does not award an arbitrary opponent`() {
        val game = UnoSession(
            listOf(seat("a", "A"), seat("b", "B"), seat("c", "C")),
            seed = 21L
        )

        val first = game.act("a", "resign", emptyMap())
        assertTrue(first.accepted)
        val afterA = game.viewFor("b")
        assertFalse(afterA.finished)
        assertEquals("B", afterA.turn)
        assertTrue(afterA.fields.getValue("eliminated").split(',').contains("a"))
        assertFalse(game.act("a", "draw", emptyMap()).accepted)

        val second = game.act("b", "resign", emptyMap())
        assertTrue(second.accepted)
        val final = game.viewFor("c")
        assertTrue(final.finished)
        assertEquals("C", final.winner)
    }
}
