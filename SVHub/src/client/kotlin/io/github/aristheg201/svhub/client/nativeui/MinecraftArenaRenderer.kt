package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ArenaTileRole { FLOOR, ALLY, ENEMY, PATH }

data class MinecraftArenaProp(
    val item: String,
    val x: Float,
    val y: Float,
    val scale: Float = 0.7f
)

data class MinecraftArenaDefinition(
    val style: String = "terrain",
    val floor: List<String> = emptyList(),
    val floorAlt: List<String> = emptyList(),
    val ally: List<String> = emptyList(),
    val enemy: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val props: List<MinecraftArenaProp> = emptyList(),
    val floorColor: Int = 0xFF173530.toInt(),
    val floorAltColor: Int = 0xFF132C29.toInt(),
    val allyColor: Int = 0xFF173530.toInt(),
    val enemyColor: Int = 0xFF302126.toInt(),
    val pathColor: Int = 0xFF4A463E.toInt(),
    val pathAccentColor: Int = 0xFF756F62.toInt(),
    val gridColor: Int = 0xFF29403F.toInt(),
    val depthColor: Int = 0xFF101719.toInt(),
    val depth: Int = 2,
    val detailEvery: Int = 0,
    val pathDetailEvery: Int = 0
) {
    fun color(role: ArenaTileRole, alternate: Boolean): Int = when (role) {
        ArenaTileRole.PATH -> pathColor
        ArenaTileRole.ALLY -> allyColor
        ArenaTileRole.ENEMY -> enemyColor
        ArenaTileRole.FLOOR -> if (alternate) floorAltColor else floorColor
    }

    fun palette(role: ArenaTileRole, alternate: Boolean): List<String> = when (role) {
        ArenaTileRole.PATH -> path.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.ALLY -> ally.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.ENEMY -> enemy.ifEmpty { if (alternate) floorAlt else floor }
        ArenaTileRole.FLOOR -> if (alternate) floorAlt.ifEmpty { floor } else floor
    }

    fun detailCadence(role: ArenaTileRole): Int =
        if (role == ArenaTileRole.PATH) pathDetailEvery else detailEvery
}

object MinecraftArenaRegistry {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, MinecraftArenaDefinition?>()

    fun definition(arenaId: String): MinecraftArenaDefinition? =
        cache.computeIfAbsent(arenaId, ::load)

    fun clear() = cache.clear()

    private fun load(arenaId: String): MinecraftArenaDefinition? {
        if (!arenaId.matches(Regex("^[a-z0-9_.-]{1,64}$"))) return null
        val id = ResourceLocation.fromNamespaceAndPath("svhub", "arenas/$arenaId.json")
        val resource = Minecraft.getInstance().resourceManager.getResource(id).orElse(null) ?: return null
        return runCatching {
            resource.open().bufferedReader().use { reader ->
                parse(gson.fromJson(reader, JsonObject::class.java) ?: JsonObject())
            }
        }.getOrNull()
    }

    private fun parse(root: JsonObject): MinecraftArenaDefinition {
        return MinecraftArenaDefinition(
            style = string(root, "style", "terrain").take(32),
            floor = strings(root, "floor"),
            floorAlt = strings(root, "floorAlt"),
            ally = strings(root, "ally"),
            enemy = strings(root, "enemy"),
            path = strings(root, "path"),
            props = root.getAsJsonArray("props")?.mapNotNull { raw ->
                val obj = runCatching { raw.asJsonObject }.getOrNull() ?: return@mapNotNull null
                val item = runCatching { obj.get("item")?.asString.orEmpty() }.getOrDefault("")
                if (item.isBlank()) return@mapNotNull null
                MinecraftArenaProp(
                    item = item,
                    x = runCatching { obj.get("x")?.asFloat ?: 0f }.getOrDefault(0f),
                    y = runCatching { obj.get("y")?.asFloat ?: 0f }.getOrDefault(0f),
                    scale = runCatching { obj.get("scale")?.asFloat ?: 0.7f }.getOrDefault(0.7f).coerceIn(0.25f, 1.8f)
                )
            }.orEmpty().take(32),
            floorColor = color(root, "floorColor", 0xFF173530.toInt()),
            floorAltColor = color(root, "floorAltColor", 0xFF132C29.toInt()),
            allyColor = color(root, "allyColor", 0xFF173530.toInt()),
            enemyColor = color(root, "enemyColor", 0xFF302126.toInt()),
            pathColor = color(root, "pathColor", 0xFF4A463E.toInt()),
            pathAccentColor = color(root, "pathAccentColor", 0xFF756F62.toInt()),
            gridColor = color(root, "gridColor", 0xFF29403F.toInt()),
            depthColor = color(root, "depthColor", 0xFF101719.toInt()),
            depth = int(root, "depth", 2).coerceIn(0, 6),
            detailEvery = int(root, "detailEvery", 0).coerceIn(0, 32),
            pathDetailEvery = int(root, "pathDetailEvery", 0).coerceIn(0, 32)
        )
    }

    private fun strings(root: JsonObject, key: String): List<String> =
        root.getAsJsonArray(key)?.mapNotNull { value ->
            runCatching { value.asString.trim() }.getOrNull()
                ?.takeIf { it.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) }
        }.orEmpty().take(16)

    private fun string(root: JsonObject, key: String, fallback: String): String =
        runCatching { root.get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private fun int(root: JsonObject, key: String, fallback: Int): Int =
        runCatching { root.get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private fun color(root: JsonObject, key: String, fallback: Int): Int {
        val raw = runCatching { root.get(key)?.asString.orEmpty() }.getOrDefault("").removePrefix("#")
        val rgb = raw.toLongOrNull(16) ?: return fallback
        return when (raw.length) {
            6 -> (0xFF000000L or rgb).toInt()
            8 -> rgb.toInt()
            else -> fallback
        }
    }
}

object MinecraftArenaRenderer {
    /**
     * Sparse surface detail only. The old implementation rendered one block item on
     * every cell, which made arenas look like a debug checkerboard of floating cubes.
     */
    fun renderTile(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        index: Int,
        role: ArenaTileRole,
        alternate: Boolean,
        seed: String
    ) {
        val cadence = theme.detailCadence(role)
        if (cadence <= 0) return
        val hash = seed.hashCode() * 31 + index * 131 + role.ordinal * 17
        if (Math.floorMod(hash, cadence) != 0) return

        val palette = theme.palette(role, alternate)
        if (palette.isEmpty()) return
        val item = palette[Math.floorMod(hash ushr 3, palette.size)]
        val point = layout.center(index)
        val size = min(12, max(6, min(layout.tileWidth, layout.tileHeight * 2) / 2))
        renderItem(gui, item, point.x.roundToInt(), point.y.roundToInt() - max(1, layout.tileHeight / 8), size, 10.0)
    }

    fun renderProps(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        seed: String
    ) {
        theme.props
            .mapIndexed { index, prop -> Triple(index, prop, layout.project(prop.x, prop.y)) }
            .sortedBy { it.third.y }
            .forEach { (index, prop, point) ->
                val size = (min(26, max(11, layout.tileWidth)) * prop.scale).roundToInt().coerceIn(8, 34)
                renderItem(
                    gui,
                    prop.item,
                    point.x.roundToInt(),
                    point.y.roundToInt() - size / 3,
                    size,
                    30.0 + point.y / 8.0 + index * 0.01
                )
            }
    }

    private fun renderItem(gui: GuiGraphics, itemId: String, centerX: Int, centerY: Int, pixels: Int, depth: Double) {
        val id = ResourceLocation.tryParse(itemId) ?: return
        val optional = BuiltInRegistries.ITEM.getOptional(id)
        if (optional.isEmpty) return
        val item = optional.get()
        if (item === Items.AIR) return
        val stack = ItemStack(item)
        if (stack.isEmpty) return
        val scale = (pixels / 16f).coerceIn(0.35f, 2.1f)
        val pose = gui.pose()
        pose.pushPose()
        pose.translate(centerX - 8.0 * scale, centerY - 8.0 * scale, depth)
        pose.scale(scale, scale, 1f)
        gui.renderItem(stack, 0, 0)
        pose.popPose()
    }
}
