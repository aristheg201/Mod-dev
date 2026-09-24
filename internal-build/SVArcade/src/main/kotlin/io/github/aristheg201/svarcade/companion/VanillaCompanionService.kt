package io.github.aristheg201.svarcade.companion

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.aristheg201.svarcade.SVArcade
import io.github.aristheg201.svarcade.util.AtomicFiles
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object VanillaCompanionService {
    val allowed = CompanionArena.roster.mapValues { it.value.name }
    private val selected = ConcurrentHashMap<UUID, String>()
    private val online = ConcurrentHashMap.newKeySet<UUID>()
    private val active = ConcurrentHashMap<UUID, UUID>()
    private val tickCounter = AtomicInteger()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SVArcade-Companion-Persistence").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
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
            SVArcade.LOGGER.info("Loaded {} persisted vanilla companion selections", selected.size)
        }.onFailure { SVArcade.LOGGER.warn("Unable to read companion selections; starting empty", it) }
    }

    fun select(player: ServerPlayer, requested: String): Boolean {
        val entity = requested.trim().lowercase()
        if (entity != "none" && entity !in allowed) return false
        CompanionArena.clear(player.uuid)
        removeLoaded(player.server, player.uuid)
        if (entity == "none") {
            selected.remove(player.uuid)
            persistAsync()
            return true
        }
        selected[player.uuid] = entity
        online += player.uuid
        spawn(player.server, player, entity)
        persistAsync()
        return true
    }

    fun selectedFor(playerId: UUID): String? = selected[playerId]

    fun onJoin(player: ServerPlayer) {
        online += player.uuid
        selected[player.uuid]?.let { entity -> removeLoaded(player.server, player.uuid); spawn(player.server, player, entity) }
    }

    fun onDisconnect(player: ServerPlayer) {
        online -= player.uuid
        CompanionArena.clear(player.uuid)
        removeLoaded(player.server, player.uuid)
    }

    fun shutdown(server: MinecraftServer) {
        if (!closed.compareAndSet(false, true)) return
        active.keys.toList().forEach { removeLoaded(server, it) }
        saveNow()
        io.shutdown()
        runCatching { io.awaitTermination(2, TimeUnit.SECONDS) }
    }

    fun tick(server: MinecraftServer) {
        if (tickCounter.incrementAndGet() % 40 != 0) return
        server.playerList.players.forEach { player ->
            val entityId = active[player.uuid] ?: return@forEach
            val entity = findEntity(server, entityId) ?: run {
                selected[player.uuid]?.let { spawn(server, player, it) }
                return@forEach
            }
            if (entity.level() != player.level()) {
                removeLoaded(server, player.uuid)
                selected[player.uuid]?.let { spawn(server, player, it) }
                return@forEach
            }
            if (entity.distanceToSqr(player) > 64.0) entity.teleportTo(player.x + 1.1, player.y, player.z + 1.1)
        }
    }

    private fun spawn(server: MinecraftServer, player: ServerPlayer, type: String) {
        if (type !in allowed) return
        val entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(type))
        val entity = entityType.create(player.level()) ?: return
        entity.moveTo(player.x + 1.1, player.y, player.z + 1.1, player.yRot, player.xRot)
        entity.isInvulnerable = true
        entity.addTag("svarcade_companion")
        entity.addTag("svarcade_owner_${player.uuid}")
        player.level().addFreshEntity(entity)
        active[player.uuid] = entity.uuid
    }

    private fun removeLoaded(server: MinecraftServer, playerId: UUID) {
        val entityId = active.remove(playerId) ?: return
        findEntity(server, entityId)?.discard()
    }

    private fun findEntity(server: MinecraftServer, id: UUID): Entity? {
        server.allLevels.forEach { level -> level.getEntity(id)?.let { return it } }
        return null
    }

    private fun persistAsync() {
        if (closed.get()) return
        val path = file ?: return
        val snapshot = selected.entries.associate { it.key.toString() to it.value }
        io.execute { runCatching { AtomicFiles.writeUtf8(path, gson.toJson(snapshot)) }.onFailure { SVArcade.LOGGER.warn("Unable to persist companion selections", it) } }
    }

    private fun saveNow() {
        val path = file ?: return
        val snapshot = selected.entries.associate { it.key.toString() to it.value }
        runCatching { AtomicFiles.writeUtf8(path, gson.toJson(snapshot)) }.onFailure { SVArcade.LOGGER.warn("Unable to persist companion selections", it) }
    }
}
