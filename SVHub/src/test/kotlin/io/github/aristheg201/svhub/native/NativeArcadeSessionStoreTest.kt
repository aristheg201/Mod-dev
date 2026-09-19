package io.github.aristheg201.svhub.native

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.ChessSession
import io.github.aristheg201.svhub.native.game.NativeGameRestorer
import io.github.aristheg201.svhub.native.game.NativeSeat
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeArcadeSessionStoreTest {
    @Test fun diskEnvelopeRoundTripsIntoGameRestorer() {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        val seats = listOf(NativeSeat(first, "White"), NativeSeat(second, "Black"))
        val game = ChessSession(seats, seed = 99L)
        assertTrue(game.act(first, "move", mapOf("from" to "e2", "to" to "e4")).accepted)

        val record = NativeArcadeSessionStore.StoredSession(
            sessionId = game.sessionId,
            gameId = game.gameId,
            mode = "pvp",
            createdAtEpochMs = 1234L,
            seats = seats,
            humanActions = mapOf(first to 1, second to 0),
            controllers = mapOf(first to NativeBotRuntime.ControllerState(4L, io.github.aristheg201.svhub.native.game.NativeBotDifficulty.HARD)),
            reconnectRemainingMs = mapOf(first to 42_000L),
            state = game.snapshotState(),
            savedAtEpochMs = 5678L
        )
        val decoded = assertNotNull(NativeArcadeSessionStore.decode(NativeArcadeSessionStore.encode(record)))
        assertEquals(record.sessionId, decoded.sessionId)
        assertEquals(record.humanActions, decoded.humanActions)
        assertEquals(record.controllers, decoded.controllers)
        assertEquals(record.reconnectRemainingMs, decoded.reconnectRemainingMs)

        val restored = NativeGameRestorer.restore(
            decoded.gameId,
            decoded.seats,
            decoded.sessionId,
            decoded.state
        )
        assertEquals(game.viewFor(first).board, restored.viewFor(first).board)
        assertEquals(game.viewFor(first).fields, restored.viewFor(first).fields)
    }

    @Test fun rejectsEmptyOrInvalidSessionEnvelope() {
        val id = UUID.randomUUID().toString()
        val invalid = NativeArcadeSessionStore.StoredSession(
            sessionId = "chess-" + UUID.randomUUID(),
            gameId = "chess",
            mode = "pvp",
            createdAtEpochMs = 1L,
            seats = listOf(NativeSeat(id, "Player")),
            state = JsonObject()
        )
        assertNull(NativeArcadeSessionStore.decode(NativeArcadeSessionStore.encode(invalid)))
    }
}
