package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.network.ChunkAssembler
import io.github.aristheg201.svarcade.util.Compression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChunkAssemblerTest {
    @Test
    fun `assembles chunks out of order`() {
        val assembler = ChunkAssembler()
        assertNull(assembler.accept(7L, 1, 2, "world"))
        assertEquals("helloworld", assembler.accept(7L, 0, 2, "hello"))
        assertEquals(0, assembler.activeTransferCount())
    }

    @Test
    fun `identical duplicate is idempotent but conflicting duplicate is rejected`() {
        val assembler = ChunkAssembler()
        assertNull(assembler.accept(1L, 0, 2, "same"))
        assertNull(assembler.accept(1L, 0, 2, "same"))
        assertFailsWith<IllegalArgumentException> { assembler.accept(1L, 0, 2, "different") }
    }

    @Test
    fun `caps concurrent transfers`() {
        val assembler = ChunkAssembler(maxActiveTransfers = 2)
        assembler.accept(1L, 0, 2, "a")
        assembler.accept(2L, 0, 2, "b")
        assertFailsWith<IllegalArgumentException> { assembler.accept(3L, 0, 2, "c") }
    }

    @Test
    fun `rejects oversized chunks and invalid metadata`() {
        val assembler = ChunkAssembler()
        assertFailsWith<IllegalArgumentException> { assembler.accept(1L, 0, 1, "x".repeat(Compression.CHUNK_CHARS + 1)) }
        assertFailsWith<IllegalArgumentException> { assembler.accept(1L, 1, 1, "x") }
        assertFailsWith<IllegalArgumentException> { assembler.accept(1L, 0, ChunkAssembler.MAX_CHUNKS + 1, "x") }
    }
}
