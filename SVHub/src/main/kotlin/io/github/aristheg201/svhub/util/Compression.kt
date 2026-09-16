package io.github.aristheg201.svhub.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Compression {
    const val CHUNK_CHARS = 20_000
    const val MAX_TRANSFER_CHARS = 8_000_000
    const val MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024

    fun encodeUtf8(text: String): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun decodeUtf8(encoded: String): String {
        require(encoded.length <= MAX_TRANSFER_CHARS) { "SVHub transfer exceeds maximum compressed payload" }
        val bytes = Base64.getDecoder().decode(encoded)
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_DECOMPRESSED_BYTES) { "SVHub payload exceeds maximum decompressed size" }
                out.write(buffer, 0, read)
            }
            out.toString(StandardCharsets.UTF_8)
        }
    }

    fun chunks(encoded: String): List<String> = if (encoded.isEmpty()) listOf("") else encoded.chunked(CHUNK_CHARS)
}
