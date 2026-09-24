package io.github.aristheg201.svarcade.client

import io.github.aristheg201.svarcade.content.HubContent
import io.github.aristheg201.svarcade.content.HubContentCodec
import io.github.aristheg201.svarcade.content.HubValidator
import io.github.aristheg201.svarcade.util.AtomicFiles
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors

object ClientCache {
    private val dir = FabricLoader.getInstance().configDir.resolve("svarcade/client-cache")
    private val ioExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SVArcade-Client-Cache-IO").apply { isDaemon = true }
    }

    /** Called during handshake. A corrupt/stale cache is ignored instead of failing login. */
    fun load(): HubContent? = runCatching {
        val path = pathForCurrentServer()
        if (!Files.exists(path)) return null
        HubContentCodec.decode(Files.readString(path)).also { HubValidator.validate(it).requireValid() }
    }.getOrNull()

    /**
     * Encoding happens before the async handoff so no Minecraft state is read from
     * a background thread. The actual filesystem write is intentionally off the
     * render thread.
     */
    fun save(content: HubContent) {
        val path = runCatching { pathForCurrentServer() }.getOrNull() ?: return
        val encoded = runCatching { HubContentCodec.encode(content) }.getOrNull() ?: return
        ioExecutor.execute {
            runCatching { AtomicFiles.writeUtf8(path, encoded) }
        }
    }

    /** Dynamic Placeholder API snapshots must never be reused by revision alone. */
    fun clear() {
        val path = runCatching { pathForCurrentServer() }.getOrNull() ?: return
        ioExecutor.execute { runCatching { Files.deleteIfExists(path) } }
    }

    private fun pathForCurrentServer(): java.nio.file.Path {
        Files.createDirectories(dir)
        val client = Minecraft.getInstance()
        val identity = when {
            client.currentServer != null -> "remote:${client.currentServer!!.ip.lowercase()}"
            client.hasSingleplayerServer() -> {
                val worldName = client.singleplayerServer?.worldData?.levelName?.takeIf { it.isNotBlank() }
                    ?: "unknown-world"
                "singleplayer:$worldName"
            }
            else -> "unknown-session"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(StandardCharsets.UTF_8))
        val key = digest.take(10).joinToString("") { "%02x".format(it) }
        return dir.resolve("$key.json")
    }
}
