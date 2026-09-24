package io.github.aristheg201.svarcade.native.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeGameSnapshotRoundTripTest {
    @Test fun `stateful rng resumes exact sequence`() {
        val first=NativeStatefulRandom(1234L)
        repeat(11){first.nextInt()}
        val checkpoint=first.state
        val expected=List(16){first.nextInt()}
        val resumed=NativeStatefulRandom(0L)
        resumed.restore(checkpoint)
        assertEquals(expected,List(16){resumed.nextInt()})
    }

    private fun seat(id:String,name:String)=NativeSeat(id,name)

    private fun assertRoundTrip(game:NativeGameSession, viewers:List<String>) {
        val snapshot=game.snapshotState()
        assertTrue(snapshot.size()>0,"Expected non-empty snapshot for ${game.gameId}")
        val restored=NativeGameRestorer.restore(game.gameId,game.seats,game.sessionId,snapshot)
        assertEquals(snapshot,restored.snapshotState(),"Snapshot mismatch after restore for ${game.gameId}")
        viewers.forEach { viewer ->
            val before=game.viewFor(viewer)
            val after=restored.viewFor(viewer)
            assertEquals(before.board,after.board)
            assertEquals(before.cards,after.cards)
            assertEquals(before.fields,after.fields)
            assertEquals(before.finished,after.finished)
            assertEquals(before.winner,after.winner)
        }
    }

    @Test fun `chess checkpoint round trips exact rule state`() {
        val game=ChessSession(listOf(seat("w","White"),seat("b","Black")))
        assertTrue(game.act("w","move",mapOf("from" to "e2","to" to "e4")).accepted)
        assertRoundTrip(game,listOf("w","b"))
    }

    @Test fun `xiangqi checkpoint round trips board and clocks`() {
        val game=XiangqiSession(listOf(seat("r","Red"),seat("b","Black")))
        assertTrue(game.act("r","move",mapOf("from" to "a6","to" to "a5")).accepted)
        assertRoundTrip(game,listOf("r","b"))
    }

    @Test fun `ludo checkpoint preserves eliminated players`() {
        val game=LudoSession(listOf(seat("a","A"),seat("b","B"),seat("c","C")),seed=7L)
        assertTrue(game.act("b","resign",emptyMap()).accepted)
        assertRoundTrip(game,listOf("a","b","c"))
    }

    @Test fun `uno checkpoint preserves deck hand and elimination state`() {
        val game=UnoSession(listOf(seat("a","A"),seat("b","B"),seat("c","C")),seed=11L)
        assertTrue(game.act("b","resign",emptyMap()).accepted)
        assertRoundTrip(game,listOf("a","b","c"))
    }

    @Test fun `pokecards checkpoint preserves hidden decks and locked cards`() {
        val game=CardDuelSession(listOf(seat("a","A"),seat("b","B")),seed=13L)
        assertTrue(game.act("a","play",mapOf("index" to "0")).accepted)
        assertRoundTrip(game,listOf("a","b"))
    }

    @Test fun `tower defense checkpoint preserves live wave simulation`() {
        val game=TowerDefenseSession(listOf(seat("a","A")),seed=17L)
        assertTrue(game.act("a","deploy",mapOf("type" to "pikachu","slot" to "0")).accepted)
        assertTrue(game.act("a","start_wave",emptyMap()).accepted)
        game.tick(System.currentTimeMillis()+1_000L)
        assertRoundTrip(game,listOf("a"))
    }
}
