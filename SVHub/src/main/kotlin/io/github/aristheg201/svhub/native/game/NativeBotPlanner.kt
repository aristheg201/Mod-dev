package io.github.aristheg201.svhub.native.game

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

data class NativeBotAction(val action: String, val args: Map<String, String> = emptyMap())
data class NativeBotPlan(val candidates: List<NativeBotAction>)

/**
 * Pure, immutable-view bot planner. This class never touches Minecraft, Cobblemon,
 * networking, persistence, or live session state, so it is safe to run on a worker thread.
 * The server thread still validates every returned action through the real game session.
 */
object NativeBotPlanner {
    fun plan(view: NativeGameView, difficulty: NativeBotDifficulty): NativeBotPlan {
        if (view.finished) return NativeBotPlan(emptyList())
        val actions = when (view.gameId) {
            "chess" -> chess(view, difficulty)
            "xiangqi" -> xiangqi(view, difficulty)
            "ludo" -> ludo(view, difficulty)
            "uno" -> uno(view, difficulty)
            "pokecards" -> cards(view, difficulty)
            "tft" -> tft(view, difficulty)
            "tower_defense" -> towerDefense(view, difficulty)
            else -> emptyList()
        }
        return NativeBotPlan(actions.take(MAX_CANDIDATES))
    }

    fun shouldThink(view: NativeGameView, seat: NativeSeat): Boolean {
        if (!seat.bot || view.finished) return false
        return when (view.gameId) {
            "chess", "xiangqi", "ludo", "uno" -> view.turn == seat.name
            "pokecards" -> view.fields["locked"] != "true"
            "tft" -> view.phase == "shop" && view.actions.any { it.id == "ready" && it.enabled }
            "tower_defense" -> true
            else -> false
        }
    }

    private fun ludo(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        val enabled = view.actions.filter { it.enabled }
        val moves = enabled.filter { it.id == "move" }
        if (moves.isNotEmpty()) {
            val ordered = when (difficulty) {
                NativeBotDifficulty.EASY -> moves.shuffledLocal()
                NativeBotDifficulty.NORMAL -> moves.sortedByDescending { it.payload["piece"]?.toIntOrNull() ?: 0 }
                NativeBotDifficulty.HARD -> moves.sortedWith(compareByDescending<NativeActionView> { it.hint.filter(Char::isDigit).toIntOrNull() ?: 0 }.thenByDescending { it.payload["piece"]?.toIntOrNull() ?: 0 })
            }
            return ordered.map { NativeBotAction("move", it.payload) }
        }
        return enabled.firstOrNull { it.id == "roll" }?.let { listOf(NativeBotAction("roll")) } ?: emptyList()
    }

    private fun uno(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        val active = view.fields["activeColor"].orEmpty()
        val hasActiveColor = view.cards.any { it.meta["color"] == active && it.meta["kind"] != "wild4" }
        val playable = view.cards.filter { card ->
            card.subtitle.isNotBlank() && !(card.meta["kind"] == "wild4" && hasActiveColor)
        }
        val color = bestUnoColor(view.cards)
        val ordered = when (difficulty) {
            NativeBotDifficulty.EASY -> playable.shuffledLocal()
            NativeBotDifficulty.NORMAL -> playable.sortedByDescending { it.value }
            NativeBotDifficulty.HARD -> playable.sortedByDescending { unoScore(it, active) }
        }
        val plays = ordered.map { card ->
            val args = linkedMapOf("index" to card.id)
            if (card.meta["color"] == "wild") args["color"] = color
            NativeBotAction("play", args)
        }
        return plays + NativeBotAction("draw")
    }

    private fun unoScore(card: NativeCardView, active: String): Int {
        var score = card.value
        score += when (card.meta["kind"]) {
            "wild4" -> 45
            "draw2" -> 30
            "skip", "reverse" -> 18
            "wild" -> 15
            else -> 0
        }
        if (card.meta["color"] == active) score += 4
        return score
    }

    private fun bestUnoColor(cards: List<NativeCardView>): String {
        val counts = linkedMapOf("red" to 0, "yellow" to 0, "green" to 0, "blue" to 0)
        cards.forEach { c -> c.meta["color"]?.let { if (it in counts) counts[it] = counts.getValue(it) + 1 } }
        return counts.maxByOrNull { it.value }?.key ?: "red"
    }

    private fun cards(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        val cards = when (difficulty) {
            NativeBotDifficulty.EASY -> view.cards.shuffledLocal()
            NativeBotDifficulty.NORMAL -> view.cards.sortedByDescending { it.value + random(0, 5) }
            NativeBotDifficulty.HARD -> view.cards.sortedByDescending { it.value * 10 + cardTraitBonus(it.subtitle) }
        }
        return cards.map { NativeBotAction("play", mapOf("index" to it.id)) }
    }

    private fun cardTraitBonus(text: String): Int = when {
        "legend" in text.lowercase() -> 25
        "guardian" in text.lowercase() -> 12
        "swift" in text.lowercase() -> 8
        else -> 0
    }

    private fun tft(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        val out = mutableListOf<NativeBotAction>()
        val board = view.board
        val bench = view.fields["bench"].orEmpty()
        val gold = view.fields["gold"]?.toIntOrNull() ?: 0
        if (bench.isNotBlank()) {
            val empties = board.indices.filter { board[it].isBlank() }
            if (empties.isNotEmpty()) {
                val slot = when (difficulty) {
                    NativeBotDifficulty.EASY -> empties.randomLocal()
                    NativeBotDifficulty.NORMAL -> empties.minByOrNull { abs((it % 7) - 3) + abs((it / 7) - 2) } ?: empties.first()
                    NativeBotDifficulty.HARD -> empties.minByOrNull { abs((it % 7) - 3) } ?: empties.first()
                }
                out += NativeBotAction("deploy", mapOf("bench" to "0", "slot" to slot.toString()))
            }
        }
        val affordable = view.cards.filter { it.value <= gold }
        val orderedShop = when (difficulty) {
            NativeBotDifficulty.EASY -> affordable.sortedBy { it.value }
            NativeBotDifficulty.NORMAL -> affordable.sortedByDescending { it.value + random(0, 2) }
            NativeBotDifficulty.HARD -> affordable.sortedByDescending { it.value * 10 + tftSpeciesBonus(it.label) }
        }
        orderedShop.forEach { card -> out += NativeBotAction("buy", mapOf("index" to card.id.substringAfter(':'))) }
        if (difficulty == NativeBotDifficulty.HARD && gold >= 8 && affordable.isEmpty()) out += NativeBotAction("refresh")
        out += NativeBotAction("ready")
        return out
    }

    private fun tftSpeciesBonus(species: String): Int = when (species.lowercase()) {
        "rayquaza", "mewtwo" -> 30
        "dragonite", "tyranitar", "metagross", "garchomp" -> 18
        else -> 0
    }

    private fun towerDefense(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        val out = mutableListOf<NativeBotAction>()
        val gold = view.fields["gold"]?.toIntOrNull() ?: 0
        val running = view.fields["running"] == "true"
        val path = view.board.indices.filter { view.board[it].startsWith("path") || view.board[it].startsWith("enemy") }.toSet()
        val empties = view.board.indices.filter { view.board[it].isBlank() }
        val deploySlots = empties.sortedBy { slot -> path.minOfOrNull { p -> manhattan(slot, p, 12) } ?: 99 }
        val affordable = view.cards.filter { it.value <= gold }
        val towerOrder = when (difficulty) {
            NativeBotDifficulty.EASY -> affordable.sortedBy { it.value }
            NativeBotDifficulty.NORMAL -> affordable.sortedByDescending { it.value }
            NativeBotDifficulty.HARD -> affordable.sortedByDescending { it.value * 10 + tdTowerBonus(it.id) }
        }
        if (deploySlots.isNotEmpty()) {
            towerOrder.forEach { tower -> out += NativeBotAction("deploy", mapOf("type" to tower.id, "slot" to deploySlots.first().toString())) }
        }
        val towers = view.board.withIndex().filter { it.value.startsWith("tower:") }
        if (difficulty != NativeBotDifficulty.EASY) {
            towers.sortedByDescending { towerLevel(it.value) }.take(4).forEach { out += NativeBotAction("upgrade", mapOf("slot" to it.index.toString())) }
        }
        if (!running) out += NativeBotAction("start_wave")
        return out
    }

    private fun tdTowerBonus(id: String): Int = when (id) {
        "gardevoir" -> 18
        "lucario" -> 16
        "pikachu" -> 10
        else -> 0
    }

    private fun towerLevel(text: String): Int = text.substringAfterLast(':').toIntOrNull() ?: 1

    private fun chess(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        if (view.board.size != 64) return emptyList()
        val b = CharArray(64) { i -> view.board[i].firstOrNull() ?: '.' }
        val white = view.fields["you"] == "white"
        val moves = chessPseudoMoves(b, white)
        val ordered = rankBoardMoves(moves, b, difficulty, chess = true)
        return ordered.flatMap { m ->
            val base = mutableMapOf("from" to chessSquare(m.from), "to" to chessSquare(m.to))
            if (m.promotion) {
                listOf('q', 'r', 'b', 'n').map { p -> NativeBotAction("move", base + ("promotion" to p.toString())) }
            } else listOf(NativeBotAction("move", base))
        }
    }

    private data class BoardMove(val from: Int, val to: Int, val promotion: Boolean = false, val bonus: Int = 0)

    private fun chessPseudoMoves(b: CharArray, white: Boolean): List<BoardMove> {
        val out = ArrayList<BoardMove>(48)
        fun mine(p: Char) = p != '.' && p.isUpperCase() == white
        fun enemy(p: Char) = p != '.' && p.isUpperCase() != white
        for (from in b.indices) {
            val p = b[from]
            if (!mine(p)) continue
            val f = from % 8; val r = from / 8
            when (p.lowercaseChar()) {
                'p' -> {
                    val dr = if (white) -1 else 1
                    val start = if (white) 6 else 1
                    val promo = if (white) 0 else 7
                    val nr = r + dr
                    if (nr in 0..7) {
                        val one = nr * 8 + f
                        if (b[one] == '.') {
                            out += BoardMove(from, one, nr == promo, 1)
                            if (r == start) {
                                val two = (r + dr * 2) * 8 + f
                                if (b[two] == '.') out += BoardMove(from, two, false, 0)
                            }
                        }
                        for (df in intArrayOf(-1, 1)) {
                            val nf = f + df
                            if (nf in 0..7) {
                                val to = nr * 8 + nf
                                if (enemy(b[to])) out += BoardMove(from, to, nr == promo, 25 + pieceValue(b[to]))
                            }
                        }
                    }
                }
                'n' -> addJumps(b, from, white, KNIGHT, out)
                'b' -> addSlides(b, from, white, BISHOP, out)
                'r' -> addSlides(b, from, white, ROOK, out)
                'q' -> addSlides(b, from, white, QUEEN, out)
                'k' -> addJumps(b, from, white, KING, out)
            }
        }
        return out
    }

    private fun addJumps(b: CharArray, from: Int, white: Boolean, ds: Array<Pair<Int, Int>>, out: MutableList<BoardMove>) {
        val f = from % 8; val r = from / 8
        for ((df, dr) in ds) {
            val nf = f + df; val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = nr * 8 + nf; val p = b[to]
            if (p == '.' || p.isUpperCase() != white) out += BoardMove(from, to, bonus = if (p == '.') 0 else 20 + pieceValue(p))
        }
    }

    private fun addSlides(b: CharArray, from: Int, white: Boolean, ds: Array<Pair<Int, Int>>, out: MutableList<BoardMove>) {
        val f = from % 8; val r = from / 8
        for ((df, dr) in ds) {
            var nf = f + df; var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val to = nr * 8 + nf; val p = b[to]
                if (p == '.') out += BoardMove(from, to)
                else {
                    if (p.isUpperCase() != white) out += BoardMove(from, to, bonus = 20 + pieceValue(p))
                    break
                }
                nf += df; nr += dr
            }
        }
    }

    private fun xiangqi(view: NativeGameView, difficulty: NativeBotDifficulty): List<NativeBotAction> {
        if (view.board.size != 90) return emptyList()
        val b = CharArray(90) { i -> view.board[i].firstOrNull() ?: '.' }
        val red = view.fields["you"] == "red"
        val moves = xiangqiPseudoMoves(b, red)
        val ordered = rankBoardMoves(moves, b, difficulty, chess = false)
        return ordered.map { NativeBotAction("move", mapOf("from" to xiangqiSquare(it.from), "to" to xiangqiSquare(it.to))) }
    }

    private fun xiangqiPseudoMoves(b: CharArray, red: Boolean): List<BoardMove> {
        val out = ArrayList<BoardMove>(72)
        fun mine(p: Char) = p != '.' && p.isUpperCase() == red
        fun add(from: Int, f: Int, r: Int, bonus: Int = 0) {
            if (f !in 0..8 || r !in 0..9) return
            val to = r * 9 + f; val p = b[to]
            if (p == '.' || !mine(p)) out += BoardMove(from, to, bonus = bonus + if (p == '.') 0 else 20 + xiangqiValue(p))
        }
        for (from in b.indices) {
            val p = b[from]
            if (!mine(p)) continue
            val f = from % 9; val r = from / 9
            when (p.lowercaseChar()) {
                'k' -> {
                    for ((df, dr) in ORTHO) {
                        val nf = f + df; val nr = r + dr
                        if (nf in 3..5 && nr in (if (red) 7..9 else 0..2)) add(from, nf, nr)
                    }
                    val step = if (red) -1 else 1
                    var rr = r + step
                    while (rr in 0..9) {
                        val t = b[rr * 9 + f]
                        if (t != '.') { if (t.lowercaseChar() == 'k' && !mine(t)) add(from, f, rr, 100); break }
                        rr += step
                    }
                }
                'a' -> for ((df, dr) in DIAG) {
                    val nf = f + df; val nr = r + dr
                    if (nf in 3..5 && nr in (if (red) 7..9 else 0..2)) add(from, nf, nr)
                }
                'e' -> for ((df, dr) in ELEPHANT) {
                    val nf = f + df; val nr = r + dr
                    if (nf !in 0..8 || nr !in 0..9) continue
                    if (red && nr < 5 || !red && nr > 4) continue
                    if (b[(r + dr / 2) * 9 + (f + df / 2)] == '.') add(from, nf, nr)
                }
                'h' -> {
                    val specs = HORSE
                    for ((leg, dest) in specs) {
                        val lf = f + leg.first; val lr = r + leg.second
                        if (lf !in 0..8 || lr !in 0..9 || b[lr * 9 + lf] != '.') continue
                        add(from, f + dest.first, r + dest.second)
                    }
                }
                'r', 'c' -> {
                    val cannon = p.lowercaseChar() == 'c'
                    for ((df, dr) in ORTHO) {
                        var nf = f + df; var nr = r + dr; var screen = false
                        while (nf in 0..8 && nr in 0..9) {
                            val to = nr * 9 + nf; val t = b[to]
                            if (!cannon) {
                                if (t == '.') out += BoardMove(from, to)
                                else { if (!mine(t)) out += BoardMove(from, to, bonus = 20 + xiangqiValue(t)); break }
                            } else if (!screen) {
                                if (t == '.') out += BoardMove(from, to) else screen = true
                            } else if (t != '.') {
                                if (!mine(t)) out += BoardMove(from, to, bonus = 20 + xiangqiValue(t))
                                break
                            }
                            nf += df; nr += dr
                        }
                    }
                }
                'p' -> {
                    add(from, f, r + if (red) -1 else 1, 2)
                    val crossed = if (red) r <= 4 else r >= 5
                    if (crossed) { add(from, f - 1, r, 1); add(from, f + 1, r, 1) }
                }
            }
        }
        return out
    }

    private fun rankBoardMoves(moves: List<BoardMove>, board: CharArray, difficulty: NativeBotDifficulty, chess: Boolean): List<BoardMove> {
        if (moves.isEmpty()) return emptyList()
        return when (difficulty) {
            NativeBotDifficulty.EASY -> moves.shuffledLocal()
            NativeBotDifficulty.NORMAL -> moves.sortedByDescending { it.bonus + random(0, 8) }
            NativeBotDifficulty.HARD -> moves.sortedByDescending { m ->
                val target = board[m.to]
                val capture = if (target == '.') 0 else if (chess) pieceValue(target) * 8 else xiangqiValue(target) * 8
                val center = if (chess) 7 - (abs((m.to % 8) - 3) + abs((m.to / 8) - 3)) else 7 - abs((m.to % 9) - 4)
                m.bonus * 3 + capture + center
            }
        }
    }

    private fun pieceValue(p: Char) = when (p.lowercaseChar()) { 'p' -> 1; 'n', 'b' -> 3; 'r' -> 5; 'q' -> 9; 'k' -> 100; else -> 0 }
    private fun xiangqiValue(p: Char) = when (p.lowercaseChar()) { 'p' -> 1; 'a', 'e' -> 2; 'h' -> 4; 'c' -> 5; 'r' -> 9; 'k' -> 100; else -> 0 }
    private fun chessSquare(i: Int) = "${('a'.code + i % 8).toChar()}${8 - i / 8}"
    private fun xiangqiSquare(i: Int) = "${('a'.code + i % 9).toChar()}${i / 9}"
    private fun manhattan(a: Int, b: Int, width: Int) = abs(a % width - b % width) + abs(a / width - b / width)
    private fun random(min: Int, maxExclusive: Int): Int = if (maxExclusive <= min) min else ThreadLocalRandom.current().nextInt(min, maxExclusive)
    private fun <T> List<T>.shuffledLocal(): List<T> = toMutableList().also { java.util.Collections.shuffle(it, ThreadLocalRandom.current()) }
    private fun <T> List<T>.randomLocal(): T = this[ThreadLocalRandom.current().nextInt(size)]

    private const val MAX_CANDIDATES = 24
    private val KNIGHT = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val BISHOP = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val ROOK = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val QUEEN = BISHOP + ROOK
    private val KING = QUEEN
    private val ORTHO = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAG = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val ELEPHANT = arrayOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)
    private val HORSE = arrayOf(
        (1 to 0) to (2 to 1), (1 to 0) to (2 to -1), (-1 to 0) to (-2 to 1), (-1 to 0) to (-2 to -1),
        (0 to 1) to (1 to 2), (0 to 1) to (-1 to 2), (0 to -1) to (1 to -2), (0 to -1) to (-1 to -2)
    )
}
