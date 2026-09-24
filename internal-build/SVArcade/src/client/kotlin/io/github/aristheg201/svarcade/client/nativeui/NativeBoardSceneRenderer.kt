package io.github.aristheg201.svarcade.client.nativeui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.client.cobblemon.PokemonView
import io.github.aristheg201.svarcade.ui.SceneCameras
import io.github.aristheg201.svarcade.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.floor

data class NativeBoardSceneResult(
    val frame: PokemonSceneFrame,
    val legalCells: Set<Int>
)

data class NativeSceneGhost(val entity: PokemonSceneEntity, val serial: Long)

class NativeBoardSceneUiState {
    private data class TimedGhost(val entity: PokemonSceneEntity, val serial: Long, val expiresAt: Long)
    private val scenes = linkedMapOf<String, PokemonSceneState>()
    private val captureGhosts = linkedMapOf<String, TimedGhost>()
    private val previousEntities = linkedMapOf<String, Map<String, PokemonSceneEntity>>()
    private val departureGhosts = linkedMapOf<String, MutableMap<String, TimedGhost>>()

    fun scene(gameId: String): PokemonSceneState = scenes.getOrPut(gameId) { PokemonSceneState() }

    fun captureGhost(gameId: String, serial: Long, entity: PokemonSceneEntity?, now: Long = System.currentTimeMillis()): NativeSceneGhost? {
        val current = captureGhosts[gameId]
        if (serial <= 0L || entity == null) {
            if (current != null && current.serial != serial) captureGhosts.remove(gameId)
            return current?.takeIf { it.serial == serial && now < it.expiresAt }?.let { NativeSceneGhost(it.entity, it.serial) }
        }
        if (current == null || current.serial != serial) {
            captureGhosts[gameId] = TimedGhost(entity, serial, now + GHOST_MS)
        }
        val ghost = captureGhosts[gameId] ?: return null
        if (now >= ghost.expiresAt) {
            captureGhosts.remove(gameId)
            return null
        }
        return NativeSceneGhost(ghost.entity, ghost.serial)
    }

    fun departedGhosts(gameId: String, current: List<PokemonSceneEntity>, now: Long = System.currentTimeMillis()): List<NativeSceneGhost> {
        val currentById = current.associateBy { it.id }
        val previous = previousEntities.put(gameId, currentById).orEmpty()
        val ghosts = departureGhosts.getOrPut(gameId) { linkedMapOf() }
        previous.forEach { (id, entity) ->
            if (id !in currentById && id !in ghosts) {
                ghosts[id] = TimedGhost(entity, now, now + GHOST_MS)
            }
        }
        currentById.keys.forEach(ghosts::remove)
        ghosts.entries.removeIf { now >= it.value.expiresAt }
        return ghosts.values.map { NativeSceneGhost(it.entity, it.serial) }
    }

    fun clear(gameId: String? = null) {
        if (gameId == null) {
            scenes.values.forEach(PokemonSceneState::clear)
            scenes.clear(); captureGhosts.clear(); previousEntities.clear(); departureGhosts.clear()
        } else {
            scenes.remove(gameId)?.clear()
            captureGhosts.remove(gameId); previousEntities.remove(gameId); departureGhosts.remove(gameId)
        }
    }

    companion object { private const val GHOST_MS = 900L }
}

object NativeBoardSceneRenderer {
    fun supports(gameId: String): Boolean = NativeBoardSystems.supports(gameId)

    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        ui: NativeBoardSceneUiState,
        selectedCell: Int?,
        placementMode: Boolean = false
    ): NativeBoardSceneResult? {
        val gameId = view.str("gameId")
        if (!NativeBoardSystems.supports(gameId)) return null
        return when (gameId) {
            "chess" -> renderChess(gui, font, area, view, ui, selectedCell)
            "xiangqi" -> renderXiangqi(gui, font, area, view, ui, selectedCell)
            "tower_defense" -> renderTowerDefense(gui, font, area, view, ui, selectedCell, placementMode)
            "ludo" -> renderLudo(gui, font, area, view, ui.scene(gameId), selectedCell)
            else -> null
        }
    }

    private fun renderChess(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        ui: NativeBoardSceneUiState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val scene = ui.scene("chess")
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val serial = fields.long("moveSerial")
        val lastFrom = chessIndex(fields.str("lastMoveFrom"))
        val lastTo = chessIndex(fields.str("lastMoveTo"))
        val auxFrom=chessIndex(fields.str("lastAuxMoveFrom"));val auxTo=chessIndex(fields.str("lastAuxMoveTo"))
        val legalCells = NativeBoardSystems.legalTargets("chess", view, selectedCell)
            .mapTo(linkedSetOf()) { NativeBoardSystems.displayCell("chess", view, it) }
        val selected = selectedCell?.let { setOf(NativeBoardSystems.displayCell("chess", view, it)) }.orEmpty()
        val entities = mutableListOf<PokemonSceneEntity>()
        val nativeAnimations = mutableListOf<SceneNativeAnimationSignal>()
        val viewerTeam = when (fields.str("you")) {
            "black" -> 1
            else -> 0
        }
        fun facingYaw(team:Int, base:Float) = base + if (team == viewerTeam) 0f else 180f

        repeat(minOf(64, board.size())) { index ->
            val token = runCatching { board[index].asString }.getOrDefault("")
            if (token.isBlank()) return@repeat
            val visual = NativeGameVisualRegistry.piece("chess", token.lowercase()) ?: return@repeat
            val team = if (token.firstOrNull()?.isUpperCase() == true) 0 else 1
            val displayIndex = NativeBoardSystems.displayCell("chess", view, index)
            var motionFrom: Int? = if (index == lastTo) lastFrom else null

            if (serial > 0 && index==auxTo) motionFrom=auxFrom
            val displayMotionFrom = motionFrom?.let { NativeBoardSystems.displayCell("chess", view, it) }

            entities += PokemonSceneEntity(
                id = "chess:$index:$token",
                view = visual.pokemon(token),
                label = token,
                boardX = (displayIndex % 8).toFloat(),
                boardY = (displayIndex / 8).toFloat(),
                team = team,
                yaw = facingYaw(team, visual.yaw),
                scale = visual.scale,
                motionSerial = if (displayMotionFrom != null) serial else 0L,
                motionFromX = displayMotionFrom?.let { (it % 8).toFloat() },
                motionFromY = displayMotionFrom?.let { (it / 8).toFloat() }
            )
        }

        val capturedToken = fields.str("lastCapturedPiece")
        val capturedIndex = chessIndex(fields.str("lastCapturedSquare"))
        val captureEntity = if (serial > 0L && capturedToken.isNotBlank() && capturedIndex != null) {
            val visual = NativeGameVisualRegistry.piece("chess", capturedToken.lowercase())
            val capturedTeam = if (capturedToken.firstOrNull()?.isUpperCase() == true) 0 else 1
            visual?.let {
                val displayCaptured = NativeBoardSystems.displayCell("chess", view, capturedIndex)
                PokemonSceneEntity(
                    id = "chess:captured:$serial",
                    view = it.pokemon(capturedToken),
                    label = capturedToken,
                    boardX = (displayCaptured % 8).toFloat(),
                    boardY = (displayCaptured / 8).toFloat(),
                    team = capturedTeam,
                    yaw = facingYaw(capturedTeam, it.yaw),
                    scale = it.scale
                )
            }
        } else null
        ui.captureGhost("chess", serial, captureEntity)?.let { ghost ->
            entities += ghost.entity
            nativeAnimations += SceneNativeAnimationSignal(
                id = "chess:faint",
                serial = ghost.serial,
                entityId = ghost.entity.id,
                kind = SceneNativeAnimationKind.FAINT
            )
        }
        if (capturedToken.isNotBlank() && lastTo != null) {
            val attacker = entities.firstOrNull { it.id.startsWith("chess:$lastTo:") }
            if (attacker != null) {
                nativeAnimations += SceneNativeAnimationSignal(
                    id = "chess:capture",
                    serial = serial,
                    entityId = attacker.id,
                    kind = SceneNativeAnimationKind.PHYSICAL,
                    targetEntityId = "chess:captured:$serial"
                )
            }
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
            camera = NativeBoardSystems.camera("chess", view, SceneCameras.BOARD),
            nativeAnimations = nativeAnimations,
            arenaId = "chess",
            arenaSeed = view.str("sessionId"),
            showUnitOverlays = view.get("finished")?.asBoolean != true
        )
        return NativeBoardSceneResult(frame, legalCells)
    }

    private fun renderXiangqi(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        ui: NativeBoardSceneUiState,
        selectedCell: Int?
    ): NativeBoardSceneResult {
        val scene = ui.scene("xiangqi")
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val serial = fields.long("moveSerial")
        val lastFrom = xiangqiIndex(fields.str("lastMoveFrom"))
        val lastTo = xiangqiIndex(fields.str("lastMoveTo"))
        val legalCells = NativeBoardSystems.legalTargets("xiangqi", view, selectedCell)
        val selected = selectedCell?.let(::setOf).orEmpty()
        val entities = mutableListOf<PokemonSceneEntity>()
        val nativeAnimations = mutableListOf<SceneNativeAnimationSignal>()

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

        val capturedToken = fields.str("lastCapturedPiece")
        val capturedIndex = xiangqiIndex(fields.str("lastCapturedSquare"))
        val captureEntity = if (serial > 0L && capturedToken.isNotBlank() && capturedIndex != null) {
            val visual = NativeGameVisualRegistry.piece("xiangqi", capturedToken.lowercase())
            val capturedTeam = if (capturedToken.firstOrNull()?.isUpperCase() == true) 0 else 1
            visual?.let {
                PokemonSceneEntity(
                    id = "xiangqi:captured:$serial",
                    view = it.pokemon(capturedToken),
                    label = capturedToken,
                    boardX = (capturedIndex % 9).toFloat(),
                    boardY = (capturedIndex / 9).toFloat(),
                    team = capturedTeam,
                    yaw = it.yaw + if (capturedTeam == 0) 180f else 0f,
                    scale = it.scale
                )
            }
        } else null
        ui.captureGhost("xiangqi", serial, captureEntity)?.let { ghost ->
            entities += ghost.entity
            nativeAnimations += SceneNativeAnimationSignal(
                id = "xiangqi:faint",
                serial = ghost.serial,
                entityId = ghost.entity.id,
                kind = SceneNativeAnimationKind.FAINT
            )
        }
        if (capturedToken.isNotBlank() && lastTo != null) {
            val attacker = entities.firstOrNull { it.id.startsWith("xiangqi:$lastTo:") }
            if (attacker != null) {
                nativeAnimations += SceneNativeAnimationSignal(
                    id = "xiangqi:capture",
                    serial = serial,
                    entityId = attacker.id,
                    kind = SceneNativeAnimationKind.PHYSICAL,
                    targetEntityId = "xiangqi:captured:$serial"
                )
            }
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
            camera = NativeBoardSystems.camera("xiangqi", view, SceneCameras.XIANGQI),
            nativeAnimations = nativeAnimations,
            arenaId = "xiangqi",
            arenaSeed = view.str("sessionId"),
            showUnitOverlays = view.get("finished")?.asBoolean != true
        )
        return NativeBoardSceneResult(frame, legalCells)
    }

    private fun renderTowerDefense(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        view: JsonObject,
        ui: NativeBoardSceneUiState,
        selectedCell: Int?,
        placementMode: Boolean
    ): NativeBoardSceneResult {
        val scene = ui.scene("tower_defense")
        val board = view.getAsJsonArray("board") ?: JsonArray()
        val fields = view.getAsJsonObject("fields") ?: JsonObject()
        val columns=view.int("boardWidth",1).coerceAtLeast(1);val rows=view.int("boardHeight",1).coerceAtLeast(1)
        val capacity=columns*rows
        val path = fields.str("path")
            .split(',')
            .mapNotNull(String::toIntOrNull)
            .filter { it in 0 until capacity }
        val entities = mutableListOf<PokemonSceneEntity>()
        val effects = mutableListOf<SceneEffectSignal>()
        val nativeAnimations = mutableListOf<SceneNativeAnimationSignal>()
        val legalCells = NativeBoardSystems.legalTargets("tower_defense", view, selectedCell, placementMode)

        repeat(minOf(capacity, board.size())) { index ->
            val raw = runCatching { board[index].asString }.getOrDefault("")
            if (raw.isBlank()) return@repeat
            raw.split(',').forEach { token ->
                when {
                    token.startsWith("tower:") -> parseTower(index, token,columns)?.let { visual ->
                        entities += visual.entity
                        visual.effect?.let(effects::add)
                        nativeAnimations += visual.animations
                    }
                    token.startsWith("enemy:") -> parseEnemy(index, token, path,columns)?.let(entities::add)
                }
            }
        }

        val liveEnemies = entities.filter { it.id.startsWith("td:enemy:") }
        ui.departedGhosts("tower_defense", liveEnemies).forEach { ghost ->
            entities += ghost.entity
            nativeAnimations += SceneNativeAnimationSignal(
                id = "td:faint:${ghost.entity.id}",
                serial = ghost.serial,
                entityId = ghost.entity.id,
                kind = SceneNativeAnimationKind.FAINT
            )
        }

        val frame = PokemonScene3D.render(
            gui = gui,
            font = font,
            area = area,
            columns = columns,
            rows = rows,
            entities = entities,
            state = scene,
            selectedCells = selectedCell?.let(::setOf).orEmpty(),
            legalCells = legalCells,
            camera = NativeBoardSystems.camera("tower_defense", view, SceneCameras.LANE),
            effects = effects,
            nativeAnimations = nativeAnimations,
            arenaId = "tower_defense",
            arenaSeed = view.str("sessionId"),
            pathCells = path.toSet(),
            pathRoute = path,
            showUnitOverlays = view.get("finished")?.asBoolean != true
        )
        return NativeBoardSceneResult(frame, legalCells)
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
        val arenaId=fields.str("arenaId",view.str("gameId"))
        val arena = MinecraftArenaRegistry.definition(arenaId) ?: MinecraftArenaDefinition(boardColumns = view.int("boardWidth",13), boardRows = view.int("boardHeight",4))
        val entities = mutableListOf<PokemonSceneEntity>()

        repeat(minOf(arena.boardAnchors.size.takeIf { it > 0 } ?: board.size(), board.size())) { index ->
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
                    boardX = arena.boardAnchor(index).x,
                    boardY = arena.boardAnchor(index).y,
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
            columns = arena.boardColumns,
            rows = arena.boardRows,
            entities = entities,
            state = scene,
            selectedCells = selectedCell?.let(::setOf).orEmpty(),
            camera = NativeBoardSystems.camera("ludo", view, SceneCameras.LUDO),
            arenaId = arenaId,
            arenaSeed = view.str("sessionId"),
            pathCells = arena.boardAnchors.indices.map { index ->
                val point = arena.boardAnchor(index)
                point.y.toInt() * arena.boardColumns + point.x.toInt()
            }.toSet(),
            pathRoute = arena.boardAnchors.map { point -> point.y.toInt() * arena.boardColumns + point.x.toInt() },
            showUnitOverlays = view.get("finished")?.asBoolean != true
        )
        return NativeBoardSceneResult(frame, emptySet())
    }

    private data class TowerScene(
        val entity: PokemonSceneEntity,
        val effect: SceneEffectSignal?,
        val animations: List<SceneNativeAnimationSignal>
    )

    private fun parseTower(index: Int, token: String,columns:Int): TowerScene? {
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
            boardX = (index % columns).toFloat(),
            boardY = (index / columns).toFloat(),
            team = 0,
            yaw = 165f,
            scale = visual.scale,
            star = level.coerceIn(1, 3)
        )
        val effect = targetId?.takeIf { fireSerial > 0L }?.let { enemyId ->
            SceneEffectSignal("td:shot:$index", fireSerial, SceneEffectKind.PROJECTILE, entity.id, "td:enemy:$enemyId")
        }
        val moveId = parts.getOrNull(5)?.takeIf(String::isNotBlank)
        val animations = targetId?.takeIf { fireSerial > 0L }?.let { enemyId ->
            listOf(
                SceneNativeAnimationSignal(
                    id = "td:attack:$index",
                    serial = fireSerial,
                    entityId = entity.id,
                    kind = if(parts.getOrNull(6)=="ATTACK_PHYSICAL")SceneNativeAnimationKind.PHYSICAL else SceneNativeAnimationKind.SPECIAL,
                    targetEntityId = "td:enemy:$enemyId",
                    moveId = moveId
                ),
                SceneNativeAnimationSignal(
                    id = "td:recoil:$enemyId:$index",
                    serial = fireSerial,
                    entityId = "td:enemy:$enemyId",
                    kind = SceneNativeAnimationKind.RECOIL
                )
            )
        }.orEmpty()
        return TowerScene(entity, effect, animations)
    }

    private fun parseEnemy(index: Int, token: String, path: List<Int>,columns:Int): PokemonSceneEntity? {
        val parts = token.split(':')
        if (parts.size < 6) return null
        val id = parts[1].toIntOrNull() ?: return null
        val kind = parts[2]
        val hp = parts[3].toIntOrNull() ?: return null
        val maxHp = parts[4].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val progress = parts[5].toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        val speciesName = kind.removeSuffix("_boss")
        val species = if (':' in speciesName) speciesName else "cobblemon:$speciesName"
        val pos = pathPosition(path, progress, index,columns)
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

    private fun pathPosition(path: List<Int>, progress: Float, fallbackIndex: Int,columns:Int): ScenePoint {
        if (path.isEmpty()) return ScenePoint((fallbackIndex % columns).toFloat(), (fallbackIndex / columns).toFloat())
        val base = floor(progress).toInt().coerceIn(0, path.lastIndex)
        val next = (base + 1).coerceAtMost(path.lastIndex)
        val fraction = (progress - floor(progress)).coerceIn(0f, 1f)
        val a = path[base]
        val b = path[next]
        val ax = (a % columns).toFloat()
        val ay = (a / columns).toFloat()
        val bx = (b % columns).toFloat()
        val by = (b / columns).toFloat()
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
