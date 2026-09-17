package io.github.aristheg201.svhub.companion

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.aristheg201.svhub.SVHub
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server-authoritative lightweight vanilla companion controller.
 *
 * It deliberately exposes a fixed entity whitelist instead of accepting arbitrary
 * entity ids or commands from the client. Only one tagged entity may exist per owner.
 * Persistence writes are serialized off-thread; the bounded follow pass runs once
 * every two seconds and never creates per-tick pathfinding work.
 */
object VanillaCompanionService {
    private val allowed = linkedMapOf(
        "allay" to "Allay",
        "axolotl" to "Axolotl",
        "bee" to "Bee",
        "cat" to "Cat",
        "fox" to "Fox",
        "frog" to "Frog",
        "parrot" to "Parrot",
        "rabbit" to "Rabbit",
        "wolf" to "Wolf",
        "armadillo" to "Armadillo",
        "sniffer" to "Sniffer"
    )
    private val selected = ConcurrentHashMap<UUID, String>()
    private val online = ConcurrentHashMap.newKeySet<UUID>()
    private val tickCounter = AtomicInteger()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SVHub-Companion-Persistence").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }
    @Volatile private var file: Path? = null

    fun start(path: Path) {
        if (!started.compareAndSet(false, true)) return
        file = path
        runCatching {
            Files.createDirectories(path.parent)
            if (!Files.exists(path)) return@runCatching
            val type = object : TypeToken<Map<String, String>>() {}.type
            val decoded: Map<String, String> = gson.fromJson(Files.readString(path), type) ?: emptyMap()
            decoded.forEach { (uuid, entity) ->
                val parsed = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return@forEach
                if (entity in allowed) selected[parsed] = entity
            }
            SVHub.LOGGER.info("Loaded {} persisted vanilla companion selections", selected.size)
        }.onFailure { SVHub.LOGGER.warn("Unable to read companion selections; starting empty", it) }
    }

    fun select(player: ServerPlayer, requested: String): Boolean {
        val entity = requested.trim().lowercase()
        if (entity != "none" && entity !in allowed) return false
        val server = player.server
        removeLoaded(server, player.uuid)
        if (entity == "none") {
            selected.remove(player.uuid)
            persistAsync()
            player.sendSystemMessage(Component.literal("Đã cất Linh Thú."))
            return true
        }
        selected[player.uuid] = entity
        online += player.uuid
        spawn(server, player, entity)
        persistAsync()
        player.sendSystemMessage(Component.literal("Linh Thú: ${allowed[entity] ?: entity}."))
        return true
    }

    fun onJoin(player: ServerPlayer) {
        online += player.uuid
        val entity = selected[player.uuid] ?: return
        removeLoaded(player.server, player.uuid)
        spawn(player.server, player, entity)
    }

    fun onDisconnect(player: ServerPlayer) {
        online -= player.uuid
        removeLoaded(player.server, player.uuid)
    }

    fun tick(server: MinecraftServer) {
        // Every 40 ticks (2 seconds): bounded, no custom AI/pathfinding and no disk I/O.
        if (tickCounter.incrementAndGet() % FOLLOW_INTERVAL_TICKS != 0) return
        online.toList().forEach { uuid ->
            val player = server.playerList.getPlayer(uuid) ?: return@forEach
            if (selected[uuid] == null) return@forEach
            val tag = ownerTag(uuid)
            // Distance is evaluated from the player's execution position. The selector
            // only touches the single owner-tagged companion.
            run(server, "execute at ${player.gameProfile.name} as @e[tag=$tag,limit=1,sort=nearest,distance=$FOLLOW_DISTANCE..] run tp @s ~1 ~ ~1")
        }
    }

    fun shutdown(server: MinecraftServer?) {
        if (closed.get()) return
        if (server != null) online.toList().forEach { uuid -> removeLoaded(server, uuid) }
        // Queue the final snapshot before closing the executor so shutdown cannot
        // discard the last selection change.
        persistAsync()
        if (!closed.compareAndSet(false, true)) return
        io.shutdown()
        try {
            if (!io.awaitTermination(3, TimeUnit.SECONDS)) io.shutdownNow()
        } catch (_: InterruptedException) {
            io.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun spawn(server: MinecraftServer, player: ServerPlayer, entity: String) {
        val tag = ownerTag(player.uuid)
        // Entity id is from the fixed whitelist above. Player names are restricted by
        // Minecraft account naming rules, so neither input can inject command syntax.
        val nbt = "{Invulnerable:1b,PersistenceRequired:1b,Tags:[\"svhub_companion\",\"$tag\"]}"
        run(server, "execute at ${player.gameProfile.name} run summon minecraft:$entity ~1 ~ ~1 $nbt")
    }

    private fun removeLoaded(server: MinecraftServer, uuid: UUID) {
        run(server, "kill @e[tag=${ownerTag(uuid)}]")
    }

    private fun ownerTag(uuid: UUID): String = "svhub_owner_${uuid.toString().replace("-", "")}"

    private fun run(server: MinecraftServer, command: String) {
        runCatching { server.commands.performPrefixedCommand(server.createCommandSourceStack(), command) }
            .onFailure { SVHub.LOGGER.warn("Companion command failed: {}", command.substringBefore('{'), it) }
    }

    private fun persistAsync() {
        if (closed.get() || io.isShutdown) return
        val path = file ?: return
        val snapshot = selected.entries.associate { it.key.toString() to it.value }
        io.execute {
            runCatching {
                Files.createDirectories(path.parent)
                val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
                Files.writeString(tmp, gson.toJson(snapshot))
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }.onFailure { SVHub.LOGGER.warn("Unable to persist companion selections", it) }
        }
    }

    private const val FOLLOW_INTERVAL_TICKS = 40
    private const val FOLLOW_DISTANCE = 8
}
