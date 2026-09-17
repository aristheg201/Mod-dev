package io.github.aristheg201.svhub.native.game

import kotlin.math.abs
import kotlin.random.Random

class ChessSession(
    override val seats: List<NativeSeat>,
    private val initialClockMillis: Long = 10 * 60 * 1000L,
    private val incrementMillis: Long = 5_000L,
    override val sessionId: String = NativeIds.session("chess")
) : NativeGameSession {
    override val gameId: String = "chess"
    private var board = START.toCharArray()
    private var side = 'w'
    private var castling = "KQkq"
    private var epSquare = -1
    private var halfmove = 0
    private var fullmove = 1
    private var revision = 0L
    private val log = ArrayDeque<String>()
    private val repetitions = linkedMapOf<String, Int>()
    private var result: String? = null
    private var winner: String? = null
    private var drawOfferedBy: String? = null
    private var lastClockAt = System.currentTimeMillis()
    private var whiteClock = initialClockMillis
    private var blackClock = initialClockMillis

    init {
        require(seats.size == 2)
        repetitions[positionKey()] = 1
    }

    override val finished get() = result != null
    override val winnerSeatId get() = winner

    override fun viewFor(viewerId: String): NativeGameView {
        val i = seats.indexOfFirst { it.id == viewerId }
        val check = isKingInCheck(side)
        return NativeGameView(
            sessionId, gameId, "Pokémon Chess",
            if (finished) "finished" else "playing",
            if (side == 'w') seats[0].name else seats[1].name,
            result ?: if (check) "Check" else "${if (side == 'w') "White" else "Black"} to move",
            8, 8,
            board.map { if (it == '.') "" else it.toString() },
            actions = listOf(
                NativeActionView("resign", "Resign", "Concede", !finished && i in 0..1),
                NativeActionView("offer_draw", "Offer draw", "", !finished && i in 0..1),
                NativeActionView("accept_draw", "Accept draw", "", !finished && drawOfferedBy != null && drawOfferedBy != viewerId)
            ),
            fields = linkedMapOf(
                "you" to when (i) { 0 -> "white"; 1 -> "black"; else -> "spectator" },
                "white" to seats[0].name,
                "black" to seats[1].name,
                "whiteClockMs" to whiteClock.toString(),
                "blackClockMs" to blackClock.toString(),
                "halfmove" to halfmove.toString(),
                "fullmove" to fullmove.toString(),
                "castling" to castling,
                "enPassant" to if (epSquare >= 0) squareName(epSquare) else "-"
            ),
            log = log.toList().takeLast(12),
            revision = revision,
            finished = finished,
            winner = winner?.let { id -> seats.firstOrNull { it.id == id }?.name }
        )
    }

    override fun act(viewerId: String, action: String, args: Map<String, String>): NativeGameResult {
        if (finished) return NativeGameResult(false, message = "Game already finished")
        val si = seats.indexOfFirst { it.id == viewerId }
        if (si !in 0..1) return NativeGameResult(false, message = "Spectator")
        return when (action) {
            "move" -> {
                if (si != if (side == 'w') 0 else 1) return NativeGameResult(false, message = "Not your turn")
                val from = parseSquare(args["from"]) ?: return NativeGameResult(false, message = "Bad from")
                val to = parseSquare(args["to"]) ?: return NativeGameResult(false, message = "Bad to")
                val promo = args["promotion"]?.lowercase()?.firstOrNull()?.takeIf { it in "qrbn" } ?: 'q'
                val moves = legalMoves(side)
                val m = moves.firstOrNull { it.from == from && it.to == to && (!it.promotion || it.promoteTo == promo) }
                    ?: moves.firstOrNull { it.from == from && it.to == to }
                    ?: return NativeGameResult(false, message = "Illegal move")
                applyMove(m.copy(promoteTo = if (m.promotion) promo else m.promoteTo))
                maybeRunBot()
                NativeGameResult(true, true, result ?: "Move accepted")
            }
            "resign" -> {
                finish("${seats[si].name} resigned", seats[1 - si].id)
                NativeGameResult(true, true, result.orEmpty())
            }
            "offer_draw" -> {
                drawOfferedBy = viewerId
                bump("${seats[si].name} offered a draw")
                NativeGameResult(true, true, "Draw offered")
            }
            "accept_draw" -> {
                if (drawOfferedBy == null || drawOfferedBy == viewerId) return NativeGameResult(false, message = "No draw offer")
                finish("Draw by agreement", null)
                NativeGameResult(true, true, "Draw")
            }
            else -> NativeGameResult(false, message = "Unknown chess action")
        }
    }

    override fun tick(nowMillis: Long): Boolean {
        if (finished) return false
        val d = (nowMillis - lastClockAt).coerceIn(0L, 2000L)
        lastClockAt = nowMillis
        if (side == 'w') whiteClock -= d else blackClock -= d
        if (whiteClock <= 0) { whiteClock = 0; finish("White lost on time", seats[1].id); return true }
        if (blackClock <= 0) { blackClock = 0; finish("Black lost on time", seats[0].id); return true }
        if (seatForSide(side).bot) { maybeRunBot(); return true }
        return false
    }

    private fun maybeRunBot() {
        var g = 0
        while (!finished && seatForSide(side).bot && g++ < 2) {
            val ms = legalMoves(side)
            if (ms.isEmpty()) { resolveNoMoves(); return }
            applyMove(ms.maxBy { pieceValue(board[it.to]) * 20 + 7 - (abs(file(it.to) - 3) + abs(rank(it.to) - 3)) + Random.nextInt(0, 8) })
        }
    }

    private fun applyMove(m: Move) {
        val moving = side
        val piece = board[m.from]
        val captured = if (m.enPassant) board[m.to + if (moving == 'w') 8 else -8] else board[m.to]
        val pawn = piece.lowercaseChar() == 'p'
        val capture = captured != '.'
        board[m.from] = '.'
        if (m.enPassant) board[m.to + if (moving == 'w') 8 else -8] = '.'
        board[m.to] = if (m.promotion) if (moving == 'w') m.promoteTo.uppercaseChar() else m.promoteTo.lowercaseChar() else piece
        if (m.castle) when (m.to) {
            62 -> { board[63] = '.'; board[61] = 'R' }
            58 -> { board[56] = '.'; board[59] = 'R' }
            6 -> { board[7] = '.'; board[5] = 'r' }
            2 -> { board[0] = '.'; board[3] = 'r' }
        }
        if (piece == 'K') castling = castling.replace("K", "").replace("Q", "")
        if (piece == 'k') castling = castling.replace("k", "").replace("q", "")
        when (m.from) {
            63 -> castling = castling.replace("K", "")
            56 -> castling = castling.replace("Q", "")
            7 -> castling = castling.replace("k", "")
            0 -> castling = castling.replace("q", "")
        }
        when (m.to) {
            63 -> castling = castling.replace("K", "")
            56 -> castling = castling.replace("Q", "")
            7 -> castling = castling.replace("k", "")
            0 -> castling = castling.replace("q", "")
        }
        epSquare = if (pawn && abs(m.to - m.from) == 16) (m.to + m.from) / 2 else -1
        halfmove = if (pawn || capture) 0 else halfmove + 1
        if (moving == 'b') fullmove++
        if (moving == 'w') whiteClock += incrementMillis else blackClock += incrementMillis
        side = opposite(side)
        drawOfferedBy = null
        lastClockAt = System.currentTimeMillis()
        bump("${squareName(m.from)}-${squareName(m.to)}")
        val key = positionKey()
        repetitions[key] = (repetitions[key] ?: 0) + 1
        when {
            legalMoves(side).isEmpty() -> resolveNoMoves()
            halfmove >= 100 -> finish("Draw by fifty-move rule", null)
            (repetitions[key] ?: 0) >= 3 -> finish("Draw by threefold repetition", null)
            insufficientMaterial() -> finish("Draw by insufficient material", null)
        }
    }

    private fun resolveNoMoves() {
        if (isKingInCheck(side)) finish("Checkmate", seats[if (side == 'w') 1 else 0].id)
        else finish("Stalemate", null)
    }

    private fun legalMoves(c: Char) = pseudoMoves(c).filter { m ->
        val s = board.copyOf(); val e = epSquare
        applyBoardOnly(m, c)
        val ok = !isKingInCheck(c)
        board = s; epSquare = e
        ok
    }

    private fun pseudoMoves(c: Char): List<Move> {
        val out = ArrayList<Move>()
        for (from in board.indices) {
            val p = board[from]
            if (p == '.' || colorOf(p) != c) continue
            when (p.lowercaseChar()) {
                'p' -> pawnMoves(from, c, out)
                'n' -> jumpMoves(from, c, KNIGHT, out)
                'b' -> slideMoves(from, c, BISHOP, out)
                'r' -> slideMoves(from, c, ROOK, out)
                'q' -> slideMoves(from, c, QUEEN, out)
                'k' -> kingMoves(from, c, out)
            }
        }
        return out
    }

    private fun pawnMoves(from: Int, c: Char, out: MutableList<Move>) {
        val d = if (c == 'w') -1 else 1
        val sr = if (c == 'w') 6 else 1
        val pr = if (c == 'w') 0 else 7
        val r = rank(from); val f = file(from); val nr = r + d
        if (nr in 0..7) {
            val one = idx(f, nr)
            if (board[one] == '.') {
                addPawn(from, one, nr == pr, out)
                if (r == sr) { val two = idx(f, r + d * 2); if (board[two] == '.') out += Move(from, two) }
            }
            for (df in intArrayOf(-1, 1)) {
                val nf = f + df
                if (nf !in 0..7) continue
                val to = idx(nf, nr)
                if (board[to] != '.' && colorOf(board[to]) != c) addPawn(from, to, nr == pr, out)
                if (to == epSquare) out += Move(from, to, enPassant = true)
            }
        }
    }

    private fun addPawn(f: Int, t: Int, p: Boolean, o: MutableList<Move>) {
        if (!p) o += Move(f, t) else for (x in "qrbn") o += Move(f, t, true, x)
    }

    private fun jumpMoves(from: Int, c: Char, ds: Array<Pair<Int, Int>>, o: MutableList<Move>) {
        val r = rank(from); val f = file(from)
        for ((df, dr) in ds) {
            val nf = f + df; val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val t = idx(nf, nr)
            if (board[t] == '.' || colorOf(board[t]) != c) o += Move(from, t)
        }
    }

    private fun slideMoves(from: Int, c: Char, ds: Array<Pair<Int, Int>>, o: MutableList<Move>) {
        val r = rank(from); val f = file(from)
        for ((df, dr) in ds) {
            var nf = f + df; var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val t = idx(nf, nr)
                if (board[t] == '.') o += Move(from, t)
                else { if (colorOf(board[t]) != c) o += Move(from, t); break }
                nf += df; nr += dr
            }
        }
    }

    private fun kingMoves(from: Int, c: Char, o: MutableList<Move>) {
        jumpMoves(from, c, KING, o)
        if (isKingInCheck(c)) return
        if (c == 'w' && from == 60) {
            if ('K' in castling && board[61] == '.' && board[62] == '.' && board[63] == 'R' && !isSquareAttacked(61, 'b') && !isSquareAttacked(62, 'b')) o += Move(60, 62, castle = true)
            if ('Q' in castling && board[59] == '.' && board[58] == '.' && board[57] == '.' && board[56] == 'R' && !isSquareAttacked(59, 'b') && !isSquareAttacked(58, 'b')) o += Move(60, 58, castle = true)
        } else if (c == 'b' && from == 4) {
            if ('k' in castling && board[5] == '.' && board[6] == '.' && board[7] == 'r' && !isSquareAttacked(5, 'w') && !isSquareAttacked(6, 'w')) o += Move(4, 6, castle = true)
            if ('q' in castling && board[3] == '.' && board[2] == '.' && board[1] == '.' && board[0] == 'r' && !isSquareAttacked(3, 'w') && !isSquareAttacked(2, 'w')) o += Move(4, 2, castle = true)
        }
    }

    private fun applyBoardOnly(m: Move, c: Char) {
        val p = board[m.from]
        board[m.from] = '.'
        if (m.enPassant) board[m.to + if (c == 'w') 8 else -8] = '.'
        board[m.to] = if (m.promotion) if (c == 'w') m.promoteTo.uppercaseChar() else m.promoteTo.lowercaseChar() else p
        if (m.castle) when (m.to) {
            62 -> { board[63] = '.'; board[61] = 'R' }
            58 -> { board[56] = '.'; board[59] = 'R' }
            6 -> { board[7] = '.'; board[5] = 'r' }
            2 -> { board[0] = '.'; board[3] = 'r' }
        }
    }

    private fun isKingInCheck(c: Char) = board.indexOf(if (c == 'w') 'K' else 'k').let { it >= 0 && isSquareAttacked(it, opposite(c)) }

    private fun isSquareAttacked(s: Int, by: Char): Boolean {
        val sr = rank(s); val sf = file(s); val pd = if (by == 'w') 1 else -1
        for (df in intArrayOf(-1, 1)) { val f = sf + df; val r = sr + pd; if (f in 0..7 && r in 0..7 && board[idx(f, r)] == if (by == 'w') 'P' else 'p') return true }
        for ((df, dr) in KNIGHT) { val f = sf + df; val r = sr + dr; if (f in 0..7 && r in 0..7 && board[idx(f, r)] == if (by == 'w') 'N' else 'n') return true }
        for ((df, dr) in KING) { val f = sf + df; val r = sr + dr; if (f in 0..7 && r in 0..7 && board[idx(f, r)] == if (by == 'w') 'K' else 'k') return true }
        return ray(sf, sr, by, ROOK, setOf('r', 'q')) || ray(sf, sr, by, BISHOP, setOf('b', 'q'))
    }

    private fun ray(sf: Int, sr: Int, by: Char, ds: Array<Pair<Int, Int>>, types: Set<Char>): Boolean {
        for ((df, dr) in ds) {
            var f = sf + df; var r = sr + dr
            while (f in 0..7 && r in 0..7) {
                val p = board[idx(f, r)]
                if (p != '.') { if (colorOf(p) == by && p.lowercaseChar() in types) return true; break }
                f += df; r += dr
            }
        }
        return false
    }

    private fun insufficientMaterial(): Boolean {
        val p = board.filter { it != '.' && it.lowercaseChar() != 'k' }
        if (p.isEmpty()) return true
        if (p.size == 1 && p[0].lowercaseChar() in "bn") return true
        if (p.all { it.lowercaseChar() == 'b' }) return board.indices.filter { board[it].lowercaseChar() == 'b' }.map { (file(it) + rank(it)) and 1 }.distinct().size <= 1
        return false
    }

    private fun positionKey() = "${board.concatToString()}|$side|$castling|$epSquare"
    private fun finish(r: String, w: String?) { result = r; winner = w; revision++; log.add(r); while (log.size > 32) log.removeFirst() }
    private fun bump(m: String) { revision++; log.add(m); while (log.size > 32) log.removeFirst() }
    private fun seatForSide(c: Char) = seats[if (c == 'w') 0 else 1]
    private data class Move(val from: Int, val to: Int, val promotion: Boolean = false, val promoteTo: Char = 'q', val enPassant: Boolean = false, val castle: Boolean = false)

    companion object {
        private const val START = "rnbqkbnrpppppppp................................PPPPPPPPRNBQKBNR"
        private val KNIGHT = arrayOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        private val BISHOP = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val ROOK = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val QUEEN = BISHOP + ROOK
        private val KING = QUEEN
        private fun file(i: Int) = i and 7
        private fun rank(i: Int) = i ushr 3
        private fun idx(f: Int, r: Int) = r * 8 + f
        private fun colorOf(p: Char) = if (p.isUpperCase()) 'w' else 'b'
        private fun opposite(c: Char) = if (c == 'w') 'b' else 'w'
        private fun squareName(i: Int) = "${('a'.code + file(i)).toChar()}${8 - rank(i)}"
        private fun parseSquare(v: String?): Int? {
            if (v == null || v.length != 2) return null
            val f = v[0].lowercaseChar() - 'a'
            val rn = v[1].digitToIntOrNull() ?: return null
            val r = 8 - rn
            return if (f in 0..7 && r in 0..7) idx(f, r) else null
        }
        private fun pieceValue(p: Char) = when (p.lowercaseChar()) { 'p' -> 1; 'n', 'b' -> 3; 'r' -> 5; 'q' -> 9; 'k' -> 100; else -> 0 }
    }
}
