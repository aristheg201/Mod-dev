package io.github.aristheg201.svhub.client.nativeui

import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScenePoint(val x: Float, val y: Float)

data class PokemonSceneEntity(
    val id: String,
    val view: PokemonView?,
    val label: String,
    val boardX: Float,
    val boardY: Float,
    val team: Int = 0,
    val yaw: Float = 0f,
    val scale: Float = 1f,
    val hp: Int = -1,
    val maxHp: Int = -1,
    val mana: Int = -1,
    val maxMana: Int = -1,
    val star: Int = 1,
    val motionSerial: Long = 0L,
    val motionFromX: Float? = null,
    val motionFromY: Float? = null
)

class PokemonSceneState {
    private data class Motion(
        var fromX: Float,
        var fromY: Float,
        var toX: Float,
        var toY: Float,
        var startedAt: Long,
        var serial: Long
    )

    private val motions = linkedMapOf<String, Motion>()

    fun position(entity: PokemonSceneEntity, now: Long = System.currentTimeMillis()): ScenePoint {
        val existing = motions[entity.id]
        if (existing == null) {
            val fromX = entity.motionFromX?.takeIf { entity.motionSerial > 0 } ?: entity.boardX
            val fromY = entity.motionFromY?.takeIf { entity.motionSerial > 0 } ?: entity.boardY
            val created = Motion(fromX, fromY, entity.boardX, entity.boardY, now, entity.motionSerial)
            motions[entity.id] = created
            return sample(created, now)
        }

        val targetChanged = existing.toX != entity.boardX || existing.toY != entity.boardY
        val serialChanged = entity.motionSerial > 0 && existing.serial != entity.motionSerial
        if (targetChanged || serialChanged) {
            val current = sample(existing, now)
            existing.fromX = if (serialChanged && entity.motionFromX != null) entity.motionFromX else current.x
            existing.fromY = if (serialChanged && entity.motionFromY != null) entity.motionFromY else current.y
            existing.toX = entity.boardX
            existing.toY = entity.boardY
            existing.startedAt = now
            existing.serial = entity.motionSerial
        }
        return sample(existing, now)
    }

    fun prune(activeIds: Set<String>) {
        motions.keys.removeIf { it !in activeIds }
    }

    fun clear() = motions.clear()

    private fun sample(motion: Motion, now: Long): ScenePoint {
        val t = ((now - motion.startedAt).toFloat() / MOTION_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return ScenePoint(
            motion.fromX + (motion.toX - motion.fromX) * eased,
            motion.fromY + (motion.toY - motion.fromY) * eased
        )
    }

    companion object {
        private const val MOTION_MS = 320f
    }
}

data class PokemonSceneLayout(
    val area: UiRect,
    val columns: Int,
    val rows: Int,
    val originX: Float,
    val originY: Float,
    val tileWidth: Int,
    val tileHeight: Int
) {
    fun project(x: Float, y: Float): ScenePoint = ScenePoint(
        originX + (x - y) * tileWidth * 0.5f,
        originY + (x + y) * tileHeight * 0.5f
    )

    fun center(index: Int): ScenePoint = project((index % columns).toFloat(), (index / columns).toFloat())

    fun hitBox(index: Int): UiRect {
        val point = center(index)
        return UiRect(
            (point.x - tileWidth * 0.34f).roundToInt(),
            (point.y - tileHeight * 0.38f).roundToInt(),
            max(8, (tileWidth * 0.68f).roundToInt()),
            max(7, (tileHeight * 0.76f).roundToInt())
        )
    }

    fun pick(mouseX: Double, mouseY: Double): Int? {
        var best: Int? = null
        var bestDistance = Double.MAX_VALUE
        repeat(columns * rows) { index ->
            val p = center(index)
            val nx = abs(mouseX - p.x) / max(1.0, tileWidth * 0.5)
            val ny = abs(mouseY - p.y) / max(1.0, tileHeight * 0.5)
            val distance = nx + ny
            if (distance <= 1.0 && distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }
}

data class PokemonSceneFrame(
    val layout: PokemonSceneLayout,
    val entityCenters: Map<String, ScenePoint>
)

object PokemonScene3D {
    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        columns: Int,
        rows: Int,
        entities: List<PokemonSceneEntity>,
        state: PokemonSceneState,
        selectedCells: Set<Int> = emptySet(),
        legalCells: Set<Int> = emptySet(),
        teamSplitRow: Int? = null
    ): PokemonSceneFrame {
        require(columns > 0 && rows > 0)
        val span = (columns + rows).coerceAtLeast(2)
        val widthBound = ((area.width - 12).coerceAtLeast(24) * 2 / span).coerceAtLeast(10)
        val heightBound = ((area.height - 28).coerceAtLeast(20) * 4 / span).coerceAtLeast(10)
        val tileW = min(72, min(widthBound, heightBound)).coerceAtLeast(10)
        val tileH = max(7, tileW / 2)
        val boardHeight = span * tileH / 2
        val layout = PokemonSceneLayout(
            area = area,
            columns = columns,
            rows = rows,
            originX = area.x + area.width / 2f,
            originY = area.y + max(tileH / 2f + 5f, (area.height - boardHeight) * 0.34f),
            tileWidth = tileW,
            tileHeight = tileH
        )

        val activeIds = entities.mapTo(linkedSetOf()) { it.id }
        state.prune(activeIds)
        PokemonModelRenderer.pruneScene(activeIds)

        gui.enableScissor(area.x, area.y, area.right, area.bottom)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                val point = layout.project(col.toFloat(), row.toFloat())
                val fill = when {
                    index in selectedCells -> SELECTED
                    index in legalCells -> LEGAL
                    teamSplitRow != null && row < teamSplitRow -> if ((row + col) and 1 == 0) ENEMY_A else ENEMY_B
                    else -> if ((row + col) and 1 == 0) ALLY_A else ALLY_B
                }
                drawDiamond(gui, point.x.roundToInt(), point.y.roundToInt(), tileW, tileH, fill, GRID_LINE)
            }
        }

        val now = System.currentTimeMillis()
        val positioned = entities.map { entity -> entity to state.position(entity, now) }
            .sortedWith(compareBy<Pair<PokemonSceneEntity, ScenePoint>> { it.second.x + it.second.y * 2f }.thenBy { it.first.id })
        val centers = linkedMapOf<String, ScenePoint>()

        positioned.forEachIndexed { order, (entity, logical) ->
            val point = layout.project(logical.x, logical.y)
            centers[entity.id] = point
            val ring = if (entity.team == 0) ALLY_RING else ENEMY_RING
            drawDiamond(
                gui,
                point.x.roundToInt(),
                (point.y + tileH * 0.20f).roundToInt(),
                max(10, (tileW * 0.52f).roundToInt()),
                max(5, (tileH * 0.30f).roundToInt()),
                ring,
                ring
            )

            val modelSize = max(30, (tileW * 1.18f * entity.scale).roundToInt()).coerceAtMost(104)
            val rendered = entity.view?.let { view ->
                PokemonModelRenderer.renderScene(
                    gui = gui,
                    view = view,
                    instanceId = entity.id,
                    centerX = point.x.roundToInt(),
                    centerY = (point.y + tileH * 0.22f).roundToInt(),
                    size = modelSize,
                    yaw = entity.yaw,
                    zoom = entity.scale.coerceIn(0.55f, 1.35f),
                    pitch = 34f,
                    depth = 1000.0 + order * 3.0
                )
            } ?: false

            if (!rendered) {
                val label = font.plainSubstrByWidth(entity.label, max(16, tileW - 8))
                gui.drawCenteredString(font, label, point.x.roundToInt(), point.y.roundToInt() - 4, TEXT)
            }

            if (entity.star > 1) {
                gui.drawCenteredString(
                    font,
                    "★".repeat(entity.star.coerceIn(2, 3)),
                    point.x.roundToInt(),
                    (point.y - tileH * 0.75f).roundToInt(),
                    GOLD
                )
            }
            if (entity.maxHp > 0) {
                val barW = max(12, (tileW * 0.62f).roundToInt())
                val x = point.x.roundToInt() - barW / 2
                val y = (point.y + tileH * 0.55f).roundToInt()
                gui.fill(x, y, x + barW, y + 3, BAR_BG)
                val hpW = (barW * entity.hp.coerceIn(0, entity.maxHp) / entity.maxHp)
                    .coerceAtLeast(if (entity.hp > 0) 1 else 0)
                gui.fill(x, y, x + hpW, y + 2, if (entity.team == 0) HP_ALLY else HP_ENEMY)
                if (entity.maxMana > 0) {
                    val manaW = barW * entity.mana.coerceIn(0, entity.maxMana) / entity.maxMana
                    gui.fill(x, y + 3, x + manaW, y + 4, MANA)
                }
            }
        }
        gui.disableScissor()
        return PokemonSceneFrame(layout, centers)
    }

    private fun drawDiamond(
        gui: GuiGraphics,
        cx: Int,
        cy: Int,
        width: Int,
        height: Int,
        fill: Int,
        border: Int
    ) {
        val halfW = max(2, width / 2)
        val halfH = max(2, height / 2)
        val bands = min(10, max(4, halfH * 2))
        repeat(bands) { band ->
            val y0 = cy - halfH + (band * halfH * 2 / bands)
            val y1 = cy - halfH + ((band + 1) * halfH * 2 / bands)
            val midY = (y0 + y1) * 0.5
            val ratio = 1.0 - abs(midY - cy) / halfH.toDouble()
            val half = max(1, (halfW * ratio).roundToInt())
            gui.fill(cx - half - 1, y0, cx + half + 1, max(y0 + 1, y1), border)
            if (half > 1) gui.fill(cx - half, y0, cx + half, max(y0 + 1, y1), fill)
        }
    }

    private const val GRID_LINE = 0xFF29403F.toInt()
    private const val ALLY_A = 0xFF173530.toInt()
    private const val ALLY_B = 0xFF132C29.toInt()
    private const val ENEMY_A = 0xFF302126.toInt()
    private const val ENEMY_B = 0xFF291B20.toInt()
    private const val SELECTED = 0xFF2F786E.toInt()
    private const val LEGAL = 0xFF365D45.toInt()
    private const val ALLY_RING = 0xFF4CC7B2.toInt()
    private const val ENEMY_RING = 0xFFB95E67.toInt()
    private const val TEXT = 0xFFF2F6F4.toInt()
    private const val GOLD = 0xFFE2BE62.toInt()
    private const val BAR_BG = 0xFF10191C.toInt()
    private const val HP_ALLY = 0xFF54C97A.toInt()
    private const val HP_ENEMY = 0xFFD86668.toInt()
    private const val MANA = 0xFF55A9E8.toInt()
}
