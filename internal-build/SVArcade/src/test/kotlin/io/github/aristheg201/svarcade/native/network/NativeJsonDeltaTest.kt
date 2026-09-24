package io.github.aristheg201.svarcade.native.network

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJsonDeltaTest {
    @Test fun recursivelyReplicatesOnlyChangedComponentsAndRemovals() {
        val previous = JsonParser.parseString("""{"view":{"phase":"planning","fields":{"gold":"5","unitCatalog":"stable"},"board":["a"]},"static":"keep"}""").asJsonObject
        val next = JsonParser.parseString("""{"view":{"phase":"combat","fields":{"gold":"7","unitCatalog":"stable"},"board":["a"]}}""").asJsonObject

        val patch = NativeJsonDelta.diff(previous, next)
        assertEquals("combat", patch.changed.getAsJsonObject("view").get("phase").asString)
        assertEquals("7", patch.changed.getAsJsonObject("view").getAsJsonObject("fields").get("gold").asString)
        assertFalse(patch.changed.getAsJsonObject("view").getAsJsonObject("fields").has("unitCatalog"))
        assertFalse(patch.changed.getAsJsonObject("view").has("board"))
        assertTrue("static" in patch.removed)

        val reconstructed = previous.deepCopy()
        NativeJsonDelta.apply(reconstructed, patch)
        assertEquals(next, reconstructed)
    }

    @Test fun identicalStateProducesNoOpWithoutSerializationPayload() {
        val state = JsonParser.parseString("""{"view":{"revision":4,"fields":{"gold":"10"}}}""").asJsonObject
        assertTrue(NativeJsonDelta.diff(state, state.deepCopy()).isEmpty)
    }
}
