package io.github.aristheg201.svhub.native.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationContractTest {
    private fun seat(id: String, name: String) = NativeSeat(id, name)

    @Test
    fun `chess exposes server legal moves and stable last move serial`() {
        val game = ChessSession(listOf(seat("white", "White"), seat("black", "Black")))
        val initial = game.viewFor("white")
        assertTrue(initial.fields.getValue("legalMoves").split(';').contains("e2:e4"))
        assertEquals("", initial.fields.getValue("lastMoveFrom"))
        assertEquals("0", initial.fields.getValue("moveSerial"))

        val moved = game.act("white", "move", mapOf("from" to "e2", "to" to "e4"))
        assertTrue(moved.accepted)
        val after = game.viewFor("black")
        assertEquals("e2", after.fields.getValue("lastMoveFrom"))
        assertEquals("e4", after.fields.getValue("lastMoveTo"))
        assertEquals("1", after.fields.getValue("moveSerial"))
    }

    @Test
    fun `xiangqi exposes legal moves and move identity from server`() {
        val game = XiangqiSession(listOf(seat("red", "Red"), seat("black", "Black")))
        val initial = game.viewFor("red")
        assertTrue(initial.fields.getValue("legalMoves").split(';').contains("a6:a5"))

        val moved = game.act("red", "move", mapOf("from" to "a6", "to" to "a5"))
        assertTrue(moved.accepted)
        val after = game.viewFor("black")
        assertEquals("a6", after.fields.getValue("lastMoveFrom"))
        assertEquals("a5", after.fields.getValue("lastMoveTo"))
        assertEquals("1", after.fields.getValue("moveSerial"))
    }

    @Test
    fun `tower defense enemy payload carries stable id max hp and progress`() {
        val game = TowerDefenseSession(listOf(seat("player", "Player")), seed = 7L)
        assertTrue(game.act("player", "start_wave", emptyMap()).accepted)
        game.tick(System.currentTimeMillis() + 1_000L)
        val view = game.viewFor("player")
        assertEquals("v2", view.fields["enemyEncoding"])
        val token = view.board.firstOrNull { it.startsWith("enemy:") }
        assertTrue(token != null, "Expected at least one spawned enemy token")
        val parts = token!!.substringBefore(',').split(':')
        assertTrue(parts.size >= 6, "Expected enemy:<id>:<kind>:<hp>:<maxHp>:<progress>")
        assertTrue(parts[1].toIntOrNull() != null)
        val hp = parts[3].toInt()
        val maxHp = parts[4].toInt()
        val progress = parts[5].toDouble()
        assertTrue(maxHp > 0)
        assertTrue(hp in 0..maxHp)
        assertTrue(progress >= 0.0)
    }
}
