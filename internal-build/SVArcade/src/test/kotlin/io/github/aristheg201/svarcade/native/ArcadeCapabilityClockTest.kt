package io.github.aristheg201.svarcade.native

import io.github.aristheg201.svarcade.native.game.*
import kotlin.test.*

class ArcadeCapabilityClockTest {
    private val seats = listOf(NativeSeat("a","A"),NativeSeat("b","B"))
    @Test fun `only board clock games advertise time controls`() {
        for (game in NativeArcadeService.games) {
            val c=ArcadeCapabilities.json(game.id,game.modes)
            assertEquals(game.id in setOf("chess","xiangqi"),c.get("supports_time_control").asBoolean)
            assertEquals("ranked" in game.modes,c.get("supports_ranked").asBoolean)
            if(game.id !in setOf("chess","xiangqi")) assertFalse(c.has("timeControls"))
        }
    }
    @Test fun `all advertised clock selections reach both engines and survive recovery`() {
        ArcadeCapabilities.clocks.forEach { control ->
            val (base,inc)=ArcadeCapabilities.time(control)
            listOf<NativeGameSession>(ChessSession(seats,base,inc),XiangqiSession(seats,base,inc)).forEach { session ->
                val fields=session.viewFor("a").fields
                assertEquals(base.toString(), fields[if(session.gameId=="chess") "whiteClockMs" else "redClockMs"])
                val restored=NativeGameRestorer.restore(session.gameId,seats,session.sessionId,session.snapshotState())
                assertEquals(inc.toString(),restored.viewFor("a").fields["incrementMs"])
                restored.tick(System.currentTimeMillis()+base+100)
                assertTrue(restored.finished)
                assertEquals("b",restored.winnerSeatId)
            }
        }
    }
    @Test fun `unknown clock is rejected instead of silently falling back`() { assertFailsWith<IllegalArgumentException> { ArcadeCapabilities.time("999+0") } }
}
