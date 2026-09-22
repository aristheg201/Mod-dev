package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.ui.SceneCameraPreset

internal interface NativeGameBoardSystem {
    val gameId: String
    fun arenaId(view: JsonObject): String = gameId
    fun camera(view: JsonObject, fallback: SceneCameraPreset): SceneCameraPreset =
        MinecraftArenaRegistry.definition(arenaId(view))?.camera(ArenaCameraRole.NORMAL, fallback) ?: fallback
    fun sourceCells(view: JsonObject): Set<Int> = emptySet()
    fun legalTargets(view: JsonObject, selectedCell: Int?, placementMode: Boolean): Set<Int> = emptySet()
    fun displayCell(view: JsonObject, logicalCell: Int): Int = logicalCell
    fun logicalCell(view: JsonObject, displayCell: Int): Int = displayCell
}

private abstract class MoveBoardSystem(
    override val gameId: String,
    private val parseSquare: (String) -> Int?
) : NativeGameBoardSystem {
    private fun legalMap(view: JsonObject): Map<Int, Set<Int>> {
        val raw = view.getAsJsonObject("fields")?.str("legalMoves").orEmpty()
        val out = linkedMapOf<Int, MutableSet<Int>>()
        raw.split(';').forEach { move ->
            val pair = move.split(':', limit = 2)
            val from = pair.getOrNull(0)?.let(parseSquare) ?: return@forEach
            val to = pair.getOrNull(1)?.let(parseSquare) ?: return@forEach
            out.getOrPut(from) { linkedSetOf() } += to
        }
        return out.mapValues { it.value.toSet() }
    }
    override fun sourceCells(view: JsonObject): Set<Int> = legalMap(view).keys
    override fun legalTargets(view: JsonObject, selectedCell: Int?, placementMode: Boolean): Set<Int> =
        selectedCell?.let { legalMap(view)[it] }.orEmpty()
}

private object ChessBoardSystem : MoveBoardSystem("chess", ::chessIndex) {
    private fun flipped(view: JsonObject) = view.getAsJsonObject("fields")?.str("you") == "black"
    override fun displayCell(view: JsonObject, logicalCell: Int): Int =
        if (flipped(view)) 63 - logicalCell else logicalCell
    override fun logicalCell(view: JsonObject, displayCell: Int): Int =
        if (flipped(view)) 63 - displayCell else displayCell
}
private object XiangqiBoardSystem : MoveBoardSystem("xiangqi", ::xiangqiIndex)

private object TowerDefenseBoardSystem : NativeGameBoardSystem {
    override val gameId = "tower_defense"
    override fun legalTargets(view: JsonObject, selectedCell: Int?, placementMode: Boolean): Set<Int> {
        if (!placementMode) return emptySet()
        val fields = view.getAsJsonObject("fields") ?: return emptySet()
        val buildZones = fields.str("buildZones").split(',').mapNotNull(String::toIntOrNull).toSet()
        val board = view.getAsJsonArray("board")
        val occupied = linkedSetOf<Int>()
        if (board != null) repeat(board.size()) { index ->
            val raw = runCatching { board[index].asString }.getOrDefault("")
            if (raw.split(',').any { it.startsWith("tower:") }) occupied += index
        }
        return buildZones - occupied
    }
}

private object LudoBoardSystem : NativeGameBoardSystem {
    override val gameId = "ludo"
    override fun arenaId(view: JsonObject): String =
        view.getAsJsonObject("fields")?.str("arenaId", gameId).orEmpty().ifBlank { gameId }
}

internal object NativeBoardSystems {
    private val systems = listOf(ChessBoardSystem, XiangqiBoardSystem, TowerDefenseBoardSystem, LudoBoardSystem)
        .associateBy(NativeGameBoardSystem::gameId)
    fun supports(gameId: String) = gameId in systems
    fun camera(gameId: String, view: JsonObject, fallback: SceneCameraPreset) =
        systems[gameId]?.camera(view, fallback) ?: fallback
    fun sourceCells(gameId: String, view: JsonObject) = systems[gameId]?.sourceCells(view).orEmpty()
    fun legalTargets(gameId: String, view: JsonObject, selectedCell: Int?, placementMode: Boolean = false) =
        systems[gameId]?.legalTargets(view, selectedCell, placementMode).orEmpty()
    fun displayCell(gameId: String, view: JsonObject, logicalCell: Int) =
        systems[gameId]?.displayCell(view, logicalCell) ?: logicalCell
    fun logicalCell(gameId: String, view: JsonObject, displayCell: Int) =
        systems[gameId]?.logicalCell(view, displayCell) ?: displayCell
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
