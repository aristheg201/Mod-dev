package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.util.Compression
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded reassembler for chunked Hub payloads.
 *
 * A transfer is rejected before it can allocate unbounded memory. Duplicate
 * packets are idempotent only when their payload is byte-for-byte identical;
 * conflicting duplicates are treated as malformed input.
 */
class ChunkAssembler(
    private val timeoutMs: Long = 30_000L,
    private val maxActiveTransfers: Int = 4
) {
    private data class Transfer(
        val created: Long,
        val total: Int,
        val chunks: Array<String?>,
        var receivedChars: Int = 0,
        var receivedCount: Int = 0
    )

    private val transfers = ConcurrentHashMap<Long, Transfer>()

    @Synchronized
    fun accept(transferId: Long, index: Int, total: Int, chunk: String): String? {
        require(total in 1..MAX_CHUNKS) { "Invalid SVHub chunk count: $total" }
        require(index in 0 until total) { "Invalid SVHub chunk index: $index/$total" }
        require(chunk.length <= Compression.CHUNK_CHARS) { "SVHub chunk exceeds maximum size" }

        cleanupExpired()

        var transfer = transfers[transferId]
        if (transfer == null) {
            require(transfers.size < maxActiveTransfers) { "Too many concurrent SVHub transfers" }
            transfer = Transfer(System.currentTimeMillis(), total, arrayOfNulls(total))
            transfers[transferId] = transfer
        }
        require(transfer.total == total) { "SVHub transfer metadata changed mid-stream" }

        val existing = transfer.chunks[index]
        if (existing != null) {
            require(existing == chunk) { "Conflicting duplicate SVHub chunk" }
            return null
        }

        val nextChars = transfer.receivedChars + chunk.length
        require(nextChars <= Compression.MAX_TRANSFER_CHARS) { "SVHub transfer exceeds maximum compressed payload" }
        transfer.chunks[index] = chunk
        transfer.receivedChars = nextChars
        transfer.receivedCount++

        if (transfer.receivedCount != transfer.total) return null

        transfers.remove(transferId)
        return buildString(transfer.receivedChars) {
            transfer.chunks.forEach { append(requireNotNull(it)) }
        }
    }

    @Synchronized
    fun clear() = transfers.clear()

    @Synchronized
    fun activeTransferCount(): Int {
        cleanupExpired()
        return transfers.size
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        transfers.entries.removeIf { now - it.value.created > timeoutMs }
    }

    companion object {
        const val MAX_CHUNKS = (Compression.MAX_TRANSFER_CHARS + Compression.CHUNK_CHARS - 1) / Compression.CHUNK_CHARS
    }
}
