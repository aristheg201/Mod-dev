package io.github.aristheg201.svarcade.client.nativeui

import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Visual contract: the local chess army must be rendered on the near edge and clicks must still address logical squares.
// Re-run after exposing the board transform facade to the test source set.
class NativeBoardSystemsPerspectiveTest {
    private fun chessView(side:String)=JsonObject().apply {
        add("fields", JsonObject().apply { addProperty("you", side) })
    }

    @Test
    fun `white viewer keeps logical chess orientation`() {
        val view=chessView("white")
        assertEquals(0,NativeBoardSystems.displayCell("chess",view,0))
        assertEquals(63,NativeBoardSystems.displayCell("chess",view,63))
        assertEquals(12,NativeBoardSystems.logicalCell("chess",view,12))
    }

    @Test
    fun `black viewer rotates chess board exactly 180 degrees and picking reverses it`() {
        val view=chessView("black")
        assertEquals(63,NativeBoardSystems.displayCell("chess",view,0))
        assertEquals(0,NativeBoardSystems.displayCell("chess",view,63))
        assertEquals(51,NativeBoardSystems.displayCell("chess",view,12))
        assertEquals(12,NativeBoardSystems.logicalCell("chess",view,51))
    }
}
