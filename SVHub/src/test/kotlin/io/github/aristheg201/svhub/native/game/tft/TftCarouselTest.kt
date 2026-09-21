package io.github.aristheg201.svhub.native.game.tft

import io.github.aristheg201.svhub.native.game.NativeGameRestorer
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.native.game.TftSession
import kotlin.math.sqrt
import kotlin.test.*

class TftCarouselTest {
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private fun carousel(): TftSession {
        val base = TftSetRegistry.bundled("kanto_rising")
        val pvpFirst = base.copy(roundSchedule = base.roundSchedule.mapIndexed { index, round ->
            if (index == 0) round.copy(type = "pvp", pve = null) else round
        })
        val session = TftSession(seats, seed = 4401L, definition = pvpFirst)
        val planningEnd = session.viewFor("a").fields.getValue("phaseEndsAt").toLong()
        session.tick(planningEnd + 1); session.tick(planningEnd + 51)
        val state = session.snapshotState()
        state.addProperty("phase", "POST_COMBAT"); state.addProperty("roundIndex", 5); state.addProperty("phaseRemainingMs", 0)
        val restored = NativeGameRestorer.restore("tft", seats, session.sessionId, state) as TftSession
        restored.tick(System.currentTimeMillis() + 1)
        assertEquals("draft", restored.viewFor("a").phase)
        return restored
    }

    @Test fun carouselRequiresReleaseRangeAndCurrentRevision() {
        val session = carousel(); val view = session.viewFor("a")
        val offer = view.fields.getValue("draft").substringBefore(';').split('~')
        val index = offer[0]; val ox = offer[8].toDouble(); val oy = offer[9].toDouble()
        assertFalse(session.act("a", "carousel_pick", mapOf("index" to index, "revision" to view.revision.toString())).accepted)
        var current = view.fields.getValue("carouselPosition").split(',').map(String::toDouble)
        repeat(12) {
            val dx=ox-current[0]; val dy=oy-current[1]; val distance=sqrt(dx*dx+dy*dy)
            if (distance <= .7) return@repeat
            val step=minOf(.8,distance); val x=current[0]+dx/distance*step;val y=current[1]+dy/distance*step
            session.act("a","carousel_move",mapOf("x" to x.toString(),"y" to y.toString()))
            current=listOf(x,y)
        }
        val fresh=session.viewFor("a")
        assertFalse(session.act("a","carousel_pick",mapOf("index" to index,"revision" to view.revision.toString())).accepted)
        assertTrue(session.act("a","carousel_pick",mapOf("index" to index,"revision" to fresh.revision.toString())).accepted)
        assertFalse(session.act("b","carousel_pick",mapOf("index" to index,"revision" to session.viewFor("b").revision.toString())).accepted)
    }

    @Test fun carouselStateAndPoolOwnershipSurviveRecovery() {
        val session=carousel(); val before=session.snapshotState(); val restored=NativeGameRestorer.restore("tft",seats,session.sessionId,before)
        assertEquals(session.viewFor("a").fields["draft"],restored.viewFor("a").fields["draft"])
        assertEquals(session.viewFor("a").fields["carouselPosition"],restored.viewFor("a").fields["carouselPosition"])
        assertEquals(before.get("poolCounts"),restored.snapshotState().get("poolCounts"))
    }
}
