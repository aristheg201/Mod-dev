package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.floor

data class NativeBoardSceneResult(
    val frame: PokemonSceneFrame,
    val legalCells: Set<Int>
)

class NativeBoardSceneUiState {
    private val scenes = linkedMapOf<String, PokemonSceneState>()
    fun scene(gameId: String): PokemonSceneState = scenes.getOrPut(gameId) { PokemonSceneState() }
    fun clear(gameId: String? = null) {
        if (gameId == null) scenes.values.forEach(PokemonSceneState::clear)
        else scenes.remove(gameId)?.clear()
    }
}

object NativeBoardSceneRenderer {
    private val supported = setOf("chess", "xiangqi", "tower_defense", "ludo")

    fun supports(gameId: String): Boolean = gameId in supported

    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        ui: NativeBoardSceneUiState,
        selectedCell: Int?
    ): NativeBoardSceneResult? {
        val gameId = view.str("gameId")
        if (gameId !in supported) return null
        return when (gameId) {
            "chess" -> renderChess(gui, font, area, view, ui.scene(gameId), selectedCell)
            "xiangqi" -> renderXiangqi(gui, font, area, view, ui.scene(gameId), selectedCell)
            "tower_defense" -> renderTowerDefense(gui, font, area, view, ui.scene(gameId), selectedCell)
            "ludo" -> renderLudo(gui, font, area, view, ui.scene(gameId), selectedCell)
            else -> null
        }
    }

    private fun renderChess(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        scene: PokemonSceneState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val serial = fields.long("moveSerial")
        val lastFrom = chessIndex(fields.str("lastMoveFrom"))
        val lastTo = chessIndex(fields.str("lastMoveTo"))
        val legalMoves = parseLegalMoves(fields.str("legalMoves"), ::chessIndex)
        val legalCells = if (selectedCell == null) emptySet() else legalMoves[selectedCell].orEmpty()
        val selected = selectedCell?.let(::setOf).orEmpty()
        val entities = mutableListOf<PokemonSceneEntity>()

        repeat(minOf(64, board.size())) { index ->
            val token = runCatching { board[index].asString }.getOrDefault("")
            if (token.isBlank()) return@repeat
            val visual = NativeGameVisualRegistry.piece("chess", token.lowercase()) ?: return@repeat
            val team = if (token.firstOrNull()?.isUpperCase() == true) 0 else 1
            var motionFrom: Int? = if (index == lastTo) lastFrom else null

            if (serial > 0 && token.lowercase() == "r") {
                val rookMotion = castleRookMotion(lastFrom, lastTo, index)
                if (rookMotion != null) motionFrom = rookMotion
            }

            entities += PokemonSceneEntity(
                id = "chess:$index:$token",
                view = visual.pokemon(token),
                label = token,
                boardX = (index % 8).toFloat(),
                boardY = (index / 8).toFloat(),
                team = team,
                yaw = visual.yaw + if (team == 0) 180f else 0f,
                scale = visual.scale,
                motionSerial = if (motionFrom != null) serial else 0L,
                motionFromX = motionFrom?.let { (it % 8).toFloat() },
                motionFromY = motionFrom?.let { (it / 8).toFloat() }
            )
        }

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = area,
            columns = 8,
            rows = 8,
            entities = entities,
            state = scene,
            selectedCells = selected,
            legalCells = legalCells,
            camera = SceneCameras.BOARD
        )
        return NativeBoardSceneResult(frame, legalCells)
    }

    private fun renderXiangqi(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        scene: PokemonSceneState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val serial = fields.long("moveSerial")
        val lastFrom = xiangqiIndex(fields.str("lastMoveFrom"))
        val lastTo = xiangqiIndex(fields.str("lastMoveTo"))
        val legalMoves = parseLegalMoves(fields.str("legalMoves"), ::xiangqiIndex)
        val legalCells = if (selectedCell == null) emptySet() else legalMoves[selectedCell].orEmpty()
        val selected = selectedCell?.let(::setOf).orEmpty()
        val entities = mutableListOf<PokemonSceneEntity>()

        repeat(minOf(90, board.size())) { index ->
            val token = runCatching { board[index].asString }.getOrDefault("")
            if (token.isBlank()) return@repeat
            val visual = NativeGameVisualRegistry.piece("xiangqi", token.lowercase()) ?: return@repeat
            val team = if (token.firstOrNull()?.isUpperCase() == true) 0 else 1
            val moving = index == lastTo && lastFrom != null
            entities += PokemonSceneEntity(
                id = "xiangqi:$index:$token",
                view = visual.pokemon(token),
                label = token,
                boardX = (index % 9).toFloat(),
                boardY = (index / 9).toFloat(),
                team = team,
                yaw = visual.yaw + if (team == 0) 180f else 0f,
                scale = visual.scale,
                motionSerial = if (moving) serial else 0L,
                motionFromX = if (moving) (lastFrom!! % 9).toFloat() else null,
                motionFromY = if (moving) (lastFrom!! / 9).toFloat() else null
            )
        }

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = area,
            columns = 9,
            rows = 10,
            entities = entities,
            state = scene,
            selectedCells = selected,
            legalCells = legalCells,
            camera = SceneCameras.XIANGQI
        )
        return NativeBoardSceneResult(frame, legalCells)
    }

    private fun renderTowerDefense(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        scene: PokemonSceneState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val path = fields.str("path")
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .filter { it in 0 until 96 }
        val entities = mutableListOf<PokemonSceneEntity>()
        val effects = mutableListOf<SceneEffectSignal>()

        repeat(minOf(96, board.size())) { index ->
            val raw = runCatching { board[index].asString }.getOrDefault("")
            if (raw.isBlank()) return@repeat
            raw.split(',').forEach { token ->
                when {
                    token.startsWith("tower:") -> parseTower(index, token)?.let { visual -> entities += visual.entity; visual.effect?.let(effects::add) }
                    token.startsWith("enemy:") -> parseEnemy(index, token, path)?.let(entities::add)
                }
            }
        }

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = area,
            columns = 12,
            rows = 8,
            entities = entities,
            state = scene,
            selectedCells = selectedCell?.let(::setOf).orEmpty(),
            camera = SceneCameras.LANE,
            effects = effects
        )
        return NativeBoardSceneResult(frame, emptySet())
    }

    private fun renderLudo(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        scene: PokemonSceneState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val viewerTeam = fields.int("you", -1)
        val entities = mutableListOf<PokemonSceneEntity>()

        repeat(minOf(52, board.size())) { index ->
            val raw = runCatching { board[index].asString }.getOrDefault("")
            if (raw.isBlank()) return@repeat
            raw.split(',').forEach { token ->
                val parts = token.split(':')
                val teamIndex = parts.getOrNull(0)?.toIntOrNull()?.minus(1) ?: return@forEach
                val pieceIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: return@forEach
                val visual = NativeGameVisualRegistry.team("ludo", teamIndex) ?: return@forEach
                entities += PokemonSceneEntity(
                    id = "ludo:$teamIndex:$pieceIndex",
                    view = visual.pokemon("P${pieceIndex + 1}"),
                    label = "${teamIndex + 1}:${pieceIndex + 1}",
                    boardX = (index % 13).toFloat(),
                    boardY = (index / 13).toFloat(),
                    team = if (teamIndex == viewerTeam) 0 else 1,
                    yaw = visual.yaw + if (teamIndex == viewerTeam) 180f else 0f,
                    scale = visual.scale
                )
            }
        }

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = area,
            columns = 13,
            rows = 4,
            entities = entities,
            state = scene,
            selectedCells = selectedCell?.let(::setOf).orEmpty(),
            camera = SceneCameras.LUDO
        )
        return NativeBoardSceneResult(frame, emptySet())
    }

    private data class TowerScene(val entity: PokemonSceneEntity, val effect: SceneEffectSignal?)

    private fun parseTower(index: Int, token: String): TowerScene? {
        val parts = token.split(':')
        val type = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val level = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val fireSerial = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val targetId = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it >= 0 }
        val species = if (':' in type) type else "cobblemon:$type"
        val visual = NativePieceVisual(species = species, scale = (0.78f + level * 0.05f).coerceAtMost(1.15f))
        val entity = PokemonSceneEntity(
            id = "td:tower:$index",
            view = visual.pokemon(type),
            label = type,
            boardX = (index % 12).toFloat(),
            boardY = (index / 12).toFloat(),
            team = 0,
            yaw = 165f,
            scale = visual.scale,
            star = level.coerceIn(1, 3)
        )
        val effect = targetId?.takeIf { fireSerial > 0L }?.let { enemyId ->
            SceneEffectSignal("td:shot:$index", fireSerial, SceneEffectKind.PROJECTILE, entity.id, "td:enemy:$enemyId")
        }
        return TowerScene(entity, effect)
    }

    private fun parseEnemy(index: Int, token: String, path: List<Int>): PokemonSceneEntity? {
        val parts = token.split(':')
        if (parts.size < 6) return null
        val id = parts[1].toIntOrNull() ?: return null
        val kind = parts[2]
        val hp = parts[3].toIntOrNull() ?: return null
        val maxHp = parts[4].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val progress = parts[5].toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        val speciesName = kind.removeSuffix("_boss")
        val species = if (':' in speciesName) speciesName else "cobblemon:$speciesName"
        val pos = pathPosition(path, progress, index)
        val boss = kind.endsWith("_boss")
        return PokemonSceneEntity(
            id = "td:enemy:$id",
            view = NativePieceVisual(species, scale = if (boss) 1.25f else 0.82f).pokemon(kind),
            label = kind,
            boardX = pos.x,
            boardY = pos.y,
            team = 1,
            yaw = 0f,
            scale = if (boss) 1.25f else 0.82f,
            hp = hp,
            maxHp = maxHp
        )
    }

    private fun pathPosition(path: List<Int>, progress: Float, fallbackIndex: Int): ScenePoint {
        if (path.isEmpty()) return ScenePoint((fallbackIndex % 12).toFloat(), (fallbackIndex / 12).toFloat())
        val base = floor(progress).toInt().coerceIn(0, path.lastIndex)
        val next = (base + 1).coerceAtMost(path.lastIndex)
        val fraction = (progress - floor(progress)).coerceIn(0f, 1f)
        val a = path[base]
        val b = path[next]
        val ax = (a % 12).toFloat()
        val ay = (a / 12).toFloat()
        val bx = (b % 12).toFloat()
        val by = (b / 12).toFloat()
        return ScenePoint(ax + (bx - ax) * fraction, ay + (by - ay) * fraction)
    }

    private fun parseLegalMoves(raw: String, parser: (String) -> Int?): Map<Int, Set<Int>> {
        val out = linkedMapOf<Int, MutableSet<Int>>()
        raw.split(';').forEach { move ->
            val pair = move.split(':', limit = 2)
            val from = pair.getOrNull(0)?.let(parser) ?: return@forEach
            val to = pair.getOrNull(1)?.let(parser) ?: return@forEach
            out.getOrPut(from) { linkedSetOf() } += to
        }
        return out.mapValues { it.value.toSet() }
    }

    private fun castleRookMotion(lastFrom: Int?, lastTo: Int?, rookIndex: Int): Int? = when {
        lastFrom == 60 && lastTo == 62 && rookIndex == 61 -> 63
        lastFrom == 60 && lastTo == 58 && rookIndex == 59 -> 56
        lastFrom == 4 && lastTo == 6 && rookIndex == 5 -> 7
        lastFrom == 4 && lastTo == 2 && rookIndex == 3 -> 0
        else -> null
    }

    private fun chessIndex(square: String): Int? {
        if (square.length != 2) return null
        val file = square[0].lowercaseChar() - 'a'
        val rank = square[1].digitToIntOrNull() ?: return null
        val row = 8 - rank
        return if (file in 0..7 && row in 0..7) row * 8 + file else null
    }

    private fun xiangqiIndex(square: String): Int? {
        if (square.length !in 2..3) return null
        val file = square[0].lowercaseChar() - 'a'
        val row = square.substring(1).toIntOrNull() ?: return null
        return if (file in 0..8 && row in 0..9) row * 9 + file else null
    }

    private fun JsonObject.str(key: String, fallback: String = ""): String =
        runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private fun JsonObject.int(key: String, fallback: Int = 0): Int =
        runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private fun JsonObject.long(key: String, fallback: Long = 0L): Long =
        runCatching { get(key)?.asLong ?: fallback }.getOrDefault(fallback)
}
