package io.github.aristheg201.svhub.network

import io.github.aristheg201.svhub.util.Compression
import java.util.concurrent.ConcurrentHashMap

class ChunkAssembler(private val timeoutMs: Long = 30_000L) {
    private data class Transfer(val created: Long, val total: Int, val chunks: Array<String?>)
    private val transfers = ConcurrentHashMap<Long, Transfer>()
    @Synchronized fun accept(transferId: Long, index: Int, total: Int, chunk: String): String? {
        require(total in 1..410); require(index in 0 until total); require(chunk.length <= Compression.CHUNK_CHARS + 128)
        cleanup(); val transfer = transfers.computeIfAbsent(transferId) { Transfer(System.currentTimeMillis(), total, arrayOfNulls(total)) }
        require(transfer.total == total); transfer.chunks[index] = chunk
        if (transfer.chunks.all { it != null }) { transfers.remove(transferId); val joined = transfer.chunks.joinToString("") { it ?: "" }; require(joined.length <= Compression.MAX_TRANSFER_CHARS); return joined }
        return null
    }
    @Synchronized fun clear() = transfers.clear()
    private fun cleanup() { val now = System.currentTimeMillis(); transfers.entries.removeIf { now - it.value.created > timeoutMs } }
}
