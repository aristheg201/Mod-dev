package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.UiRect
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
    val gridColor: Int = 0xFF29403F.toInt()
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
                    scale = runCatching { obj.get("scale")?.asFloat ?: 0.7f }.getOrDefault(0.7f).coerceIn(0.3f, 1.4f)
                )
            }.orEmpty(),
            floorColor = color(root, "floorColor", 0xFF173530.toInt()),
            floorAltColor = color(root, "floorAltColor", 0xFF132C29.toInt()),
            allyColor = color(root, "allyColor", 0xFF173530.toInt()),
            enemyColor = color(root, "enemyColor", 0xFF302126.toInt()),
            pathColor = color(root, "pathColor", 0xFF4A463E.toInt()),
            gridColor = color(root, "gridColor", 0xFF29403F.toInt())
        )
    }

    private fun strings(root: JsonObject, key: String): List<String> =
        root.getAsJsonArray(key)?.mapNotNull { value ->
            runCatching { value.asString.trim() }.getOrNull()
                ?.takeIf { it.matches(Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) }
        }.orEmpty().take(16)

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
    fun renderTile(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        index: Int,
        role: ArenaTileRole,
        alternate: Boolean,
        seed: String
    ) {
        val palette = theme.palette(role, alternate)
        if (palette.isEmpty()) return
        val item = palette[Math.floorMod(seed.hashCode() * 31 + index * 131 + role.ordinal * 17, palette.size)]
        val point = layout.center(index)
        val size = min(16, max(7, min(layout.tileWidth, layout.tileHeight * 2) - 2))
        renderItem(gui, item, point.x.roundToInt(), point.y.roundToInt() - max(1, layout.tileHeight / 7), size, 10.0)
    }

    fun renderProps(
        gui: GuiGraphics,
        layout: PokemonSceneLayout,
        theme: MinecraftArenaDefinition,
        seed: String
    ) {
        theme.props.forEachIndexed { index, prop ->
            if (((seed.hashCode() xor (index * 0x45d9f3b)) and 3) == 0 && theme.props.size > 4) return@forEachIndexed
            val point = layout.project(prop.x, prop.y)
            val size = (min(24, max(10, layout.tileWidth)) * prop.scale).roundToInt().coerceIn(8, 28)
            renderItem(gui, prop.item, point.x.roundToInt(), point.y.roundToInt() - size / 3, size, 20.0 + index)
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
        val scale = (pixels / 16f).coerceIn(0.35f, 1.75f)
        val pose = gui.pose()
        pose.pushPose()
        pose.translate(centerX - 8.0 * scale, centerY - 8.0 * scale, depth)
        pose.scale(scale, scale, 1f)
        gui.renderItem(stack, 0, 0)
        pose.popPose()
    }
}
