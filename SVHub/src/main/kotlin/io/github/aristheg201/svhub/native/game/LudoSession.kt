package io.github.aristheg201.svhub.native.game

import kotlin.random.Random

class LudoSession(
    override val seats: List<NativeSeat>,
    seed: Long = Random.nextLong(),
    override val sessionId: String = NativeIds.session("ludo")
) : NativeGameSession {
    override val gameId = "ludo"

    private val rng = Random(seed)
    private val pieces = seats.associate { it.id to IntArray(4) { HOME } }.toMutableMap()
    private val eliminated = linkedSetOf<String>()
    private var turn = 0
    private var rolled = 0
    private var sixChain = 0
    private var revision = 0L
    private var winner: String? = null
    private var result: String? = null
    private val log = ArrayDeque<String>()

    init {
        require(seats.size in 2..4)
    }

    override val finished get() = result != null
    override val winnerSeatId get() = winner

    override fun viewFor(viewerId: String): NativeGameView {
        val track = MutableList(52) { "" }
        seats.forEachIndexed { seatIndex, seat ->
            if (seat.id in eliminated) return@forEachIndexed
            pieces.getValue(seat.id).forEachIndexed { pieceIndex, progress ->
                if (progress in 0..51) {
                    val square = globalSquare(seatIndex, progress)
                    val token = "${seatIndex + 1}:${pieceIndex + 1}"
                    track[square] = if (track[square].isBlank()) token else track[square] + "," + token
                }
            }
        }

        val viewerSeat = seats.indexOfFirst { it.id == viewerId }
        val activeViewer = viewerSeat >= 0 && viewerId !in eliminated
        val mine = pieces[viewerId]
        val movable = if (activeViewer && viewerSeat == turn && rolled > 0 && mine != null) {
            movablePieces(viewerSeat, rolled)
        } else {
            emptyList()
        }

        val actions = buildList {
            add(
                NativeActionView(
                    "roll",
                    "Tung xúc xắc",
                    enabled = !finished && activeViewer && viewerSeat == turn && rolled == 0
                )
            )
            if (mine != null && activeViewer) {
                movable.forEach { piece ->
                    add(
                        NativeActionView(
                            "move",
                            "Đi quân ${piece + 1}",
                            "Dùng $rolled điểm",
                            true,
                            mapOf("piece" to piece.toString())
                        )
                    )
                }
            }
            add(
                NativeActionView(
                    "resign",
                    "Rời ván",
                    enabled = !finished && activeViewer
                )
            )
        }

        return NativeGameView(
            sessionId = sessionId,
            gameId = gameId,
            title = "Cờ Cá Ngựa",
            phase = if (finished) "finished" else if (rolled == 0) "roll" else "move",
            turn = seats[turn].name,
            status = result ?: if (rolled == 0) "Chờ tung xúc xắc" else "Đã tung: $rolled",
            boardWidth = 13,
            boardHeight = 4,
            board = track,
            actions = actions,
            fields = linkedMapOf(
                "rolled" to rolled.toString(),
                "positions" to seats.mapIndexed { index, seat ->
                    "${index + 1}:${seat.name}=[${pieces.getValue(seat.id).joinToString(",") { progress ->
                        when (progress) {
                            HOME -> "H"
                            FINISHED -> "F"
                            else -> progress.toString()
                        }
                    }}]"
                }.joinToString(" • "),
                "you" to viewerSeat.toString(),
                "safeSquares" to SAFE.joinToString(","),
                "eliminated" to eliminated.joinToString(",")
            ),
            log = log.toList().takeLast(12),
            revision = revision,
            finished = finished,
            winner = winner?.let { id -> seats.firstOrNull { it.id == id }?.name }
        )
    }

    override fun act(viewerId: String, action: String, args: Map<String, String>): NativeGameResult {
        if (finished) return NativeGameResult(false, message = "Game finished")
        val seat = seats.indexOfFirst { it.id == viewerId }
        if (seat < 0) return NativeGameResult(false, message = "Spectator")

        if (action == "resign") return resign(seat)
        if (viewerId in eliminated) return NativeGameResult(false, message = "Player eliminated")
        if (seat != turn) return NativeGameResult(false, message = "Chưa tới lượt")

        return when (action) {
            "roll" -> {
                if (rolled != 0) return NativeGameResult(false, message = "Đã tung xúc xắc")
                doRoll()
                runBots()
                NativeGameResult(true, true, "Đã tung $rolled")
            }
            "move" -> {
                if (rolled == 0) return NativeGameResult(false, message = "Hãy tung xúc xắc trước")
                val piece = args["piece"]?.toIntOrNull()
                    ?: return NativeGameResult(false, message = "Thiếu quân")
                if (piece !in movablePieces(seat, rolled)) {
                    return NativeGameResult(false, message = "Quân không đi được")
                }
                movePiece(seat, piece)
                runBots()
                NativeGameResult(true, true, result ?: "Đã đi")
            }
            else -> NativeGameResult(false, message = "Unknown Ludo action")
        }
    }

    override fun tick(nowMillis: Long): Boolean {
        if (finished) return false
        if (seats[turn].id in eliminated) {
            nextTurn(true)
            return true
        }
        if (seats[turn].bot) {
            runBots()
            return true
        }
        return false
    }

    private fun resign(seatIndex: Int): NativeGameResult {
        val seat = seats[seatIndex]
        if (!eliminated.add(seat.id)) return NativeGameResult(false, message = "Player eliminated")
        pieces.getValue(seat.id).fill(HOME)
        bump("${seat.name} rời ván")

        val active = activeSeatIndices()
        if (active.size == 1) {
            val last = seats[active.first()]
            finish("${last.name} thắng do còn lại cuối cùng", last.id)
        } else if (seatIndex == turn) {
            rolled = 0
            sixChain = 0
            nextTurn(true)
        }
        return NativeGameResult(true, true, result ?: "Đã rời ván")
    }

    private fun doRoll() {
        rolled = rng.nextInt(1, 7)
        if (rolled == 6) sixChain++ else sixChain = 0
        bump("${seats[turn].name} tung $rolled")
        if (sixChain >= 3) {
            bump("Ba lần 6 liên tiếp: mất lượt")
            rolled = 0
            sixChain = 0
            nextTurn(true)
            return
        }
        if (movablePieces(turn, rolled).isEmpty()) {
            val extra = rolled == 6
            rolled = 0
            if (!extra) nextTurn(true)
            else bump("Không có quân hợp lệ; được tung lại vì ra 6")
        }
    }

    private fun movePiece(seatIndex: Int, pieceIndex: Int) {
        val arr = pieces.getValue(seats[seatIndex].id)
        val before = arr[pieceIndex]
        val dice = rolled
        val target = if (before == HOME) 0 else before + dice
        arr[pieceIndex] = if (target == FINISH_PROGRESS) FINISHED else target
        val landed = arr[pieceIndex]
        if (landed in 0..51) captureAt(seatIndex, globalSquare(seatIndex, landed))
        bump("${seats[seatIndex].name} đi quân ${pieceIndex + 1}: ${display(before)} → ${display(arr[pieceIndex])}")

        if (arr.all { it == FINISHED }) {
            finish("${seats[seatIndex].name} về chuồng đủ 4 quân", seats[seatIndex].id)
            return
        }

        val extra = dice == 6
        rolled = 0
        if (!extra) {
            sixChain = 0
            nextTurn(true)
        } else {
            bump("Ra 6: được thêm lượt")
        }
    }

    private fun captureAt(mover: Int, square: Int) {
        if (square in SAFE) return
        seats.forEachIndexed { seatIndex, seat ->
            if (seatIndex == mover || seat.id in eliminated) return@forEachIndexed
            val arr = pieces.getValue(seat.id)
            arr.indices.forEach { index ->
                val progress = arr[index]
                if (progress in 0..51 && globalSquare(seatIndex, progress) == square) {
                    arr[index] = HOME
                    bump("${seats[mover].name} đá quân ${index + 1} của ${seat.name} về chuồng")
                }
            }
        }
    }

    private fun movablePieces(seatIndex: Int, dice: Int): List<Int> {
        if (seats[seatIndex].id in eliminated) return emptyList()
        val arr = pieces.getValue(seats[seatIndex].id)
        return arr.indices.filter { index ->
            val progress = arr[index]
            when {
                progress == FINISHED -> false
                progress == HOME -> dice == 6
                else -> progress + dice <= FINISH_PROGRESS
            }
        }
    }

    private fun runBots() {
        var guard = 0
        while (!finished && guard++ < 24) {
            if (seats[turn].id in eliminated) {
                nextTurn(true)
                continue
            }
            if (!seats[turn].bot) return
            if (rolled == 0) doRoll()
            if (finished || seats[turn].id in eliminated || !seats[turn].bot || rolled == 0) continue
            val movable = movablePieces(turn, rolled)
            if (movable.isEmpty()) {
                rolled = 0
                nextTurn(true)
                continue
            }
            movePiece(turn, movable.maxBy { moveScore(turn, it, rolled) })
        }
    }

    private fun moveScore(seatIndex: Int, pieceIndex: Int, dice: Int): Int {
        val progress = pieces.getValue(seats[seatIndex].id)[pieceIndex]
        if (progress == HOME) return 25
        val target = progress + dice
        if (target == FINISH_PROGRESS) return 100
        if (target in 0..51) {
            val square = globalSquare(seatIndex, target)
            var captures = 0
            if (square !in SAFE) {
                seats.forEachIndexed { other, seat ->
                    if (other != seatIndex && seat.id !in eliminated) {
                        captures += pieces.getValue(seat.id).count {
                            it in 0..51 && globalSquare(other, it) == square
                        }
                    }
                }
            }
            return target + captures * 50
        }
        return target
    }

    private fun activeSeatIndices(): List<Int> = seats.indices.filter { seats[it].id !in eliminated }

    private fun nextTurn(force: Boolean) {
        if (!force || finished) return
        var checked = 0
        do {
            turn = (turn + 1) % seats.size
            checked++
        } while (seats[turn].id in eliminated && checked < seats.size)
        rolled = 0
    }

    private fun finish(reason: String, winnerId: String?) {
        result = reason
        winner = winnerId
        bump(reason)
    }

    private fun bump(message: String) {
        revision++
        log.add(message)
        while (log.size > 32) log.removeFirst()
    }

    private fun globalSquare(seatIndex: Int, progress: Int) = (STARTS[seatIndex] + progress) % 52

    private fun display(progress: Int) = when (progress) {
        HOME -> "chuồng"
        FINISHED -> "đích"
        else -> progress.toString()
    }

    companion object {
        private const val HOME = -1
        private const val FINISHED = 58
        private const val FINISH_PROGRESS = 57
        private val STARTS = intArrayOf(0, 13, 26, 39)
        private val SAFE = setOf(0, 8, 13, 21, 26, 34, 39, 47)
    }
}
