package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.util.Compression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompressionTest {
    @Test
    fun `round trips unicode content`() {
        val text = "SVArcade • Pokémon • Fakémon • Tiếng Việt\n".repeat(512)
        assertEquals(text, Compression.decodeUtf8(Compression.encodeUtf8(text)))
    }

    @Test
    fun `chunking respects wire chunk size`() {
        val encoded = "x".repeat(Compression.CHUNK_CHARS * 2 + 17)
        val chunks = Compression.chunks(encoded)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= Compression.CHUNK_CHARS })
        assertEquals(encoded, chunks.joinToString(""))
    }

    @Test
    fun `rejects compressed payload above transfer limit before decoding`() {
        val oversized = "A".repeat(Compression.MAX_TRANSFER_CHARS + 1)
        assertFailsWith<IllegalArgumentException> { Compression.decodeUtf8(oversized) }
    }

    @Test
    fun `rejects decompressed payload above hard limit`() {
        val bombText = "0".repeat(Compression.MAX_DECOMPRESSED_BYTES + 1)
        val encoded = Compression.encodeUtf8(bombText)
        assertTrue(encoded.length < Compression.MAX_TRANSFER_CHARS)
        assertFailsWith<IllegalArgumentException> { Compression.decodeUtf8(encoded) }
    }
}
