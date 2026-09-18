package io.github.aristheg201.svhub.native.game

import com.google.gson.JsonObject
import kotlin.random.Random

class UnoSession(
    override val seats: List<NativeSeat>,
    seed: Long = Random.nextLong(),
    override val sessionId: String = NativeIds.session("uno"),
    private val restoreState: JsonObject? = null
) : NativeGameSession {
    override val gameId = "uno"

    private val rng = Random(seed)
    private val drawPile = ArrayDeque<Card>()
    private val discard = ArrayDeque<Card>()
    private val hands = seats.associate { it.id to mutableListOf<Card>() }.toMutableMap()
    private val eliminated = linkedSetOf<String>()
    private var turnIndex = 0
    private var direction = 1
    private var activeColor = Color.RED
    private var revision = 0L
    private var winner: String? = null
    private var result: String? = null
    private val log = ArrayDeque<String>()

    init {
        require(seats.size in 2..4)
        if (restoreState != null) {
            restoreSnapshot(restoreState)
        } else {
            buildDeck().shuffled(rng).forEach(drawPile::add)
            repeat(7) { seats.forEach { hands.getValue(it.id).add(drawOne()) } }
            var first = drawOne()
            while (first.kind == Kind.WILD4) { drawPile.add(first); first = drawOne() }
            discard.add(first)
            activeColor = if (first.color == Color.WILD) Color.entries.filter { it != Color.WILD }.random(rng) else first.color
            applyOpening(first)
        }
    }

    override val finished get() = result != null
    override val winnerSeatId get() = winner

    override fun viewFor(viewerId: String): NativeGameView {
        val viewerSeat = seats.indexOfFirst { it.id == viewerId }
        val activeViewer = viewerSeat >= 0 && viewerId !in eliminated
        val top = discard.last()
        val hand = if (activeViewer) hands[viewerId].orEmpty() else emptyList()
        return NativeGameView(
            sessionId = sessionId,
            gameId = gameId,
            title = "UNO",
            phase = if (finished) "finished" else "playing",
            turn = seats[turnIndex].name,
            status = result ?: "${activeColor.name} • ${top.label()}",
            cards = hand.mapIndexed { index, card ->
                NativeCardView(
                    index.toString(),
                    card.label(),
                    if (isPlayable(card)) "Có thể đánh" else "",
                    card.color.name.lowercase(),
                    card.score,
                    mapOf("kind" to card.kind.name.lowercase(), "color" to card.color.name.lowercase())
                )
            },
            actions = listOf(
                NativeActionView(
                    "draw",
                    "Rút bài",
                    "Rút 1 lá và hết lượt",
                    !finished && activeViewer && viewerSeat == turnIndex
                ),
                NativeActionView(
                    "resign",
                    "Rời ván",
                    enabled = !finished && activeViewer
                )
            ),
            fields = linkedMapOf(
                "top" to top.label(),
                "activeColor" to activeColor.name.lowercase(),
                "direction" to if (direction > 0) "clockwise" else "counterclockwise",
                "hands" to seats.joinToString(" • ") { seat ->
                    "${seat.name}: ${if (seat.id in eliminated) 0 else hands.getValue(seat.id).size}"
                },
                "drawPile" to drawPile.size.toString(),
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
        val seatIndex = seats.indexOfFirst { it.id == viewerId }
        if (seatIndex < 0) return NativeGameResult(false, message = "Spectator")

        if (action == "resign") return resign(seatIndex)
        if (viewerId in eliminated) return NativeGameResult(false, message = "Player eliminated")
        if (seatIndex != turnIndex) return NativeGameResult(false, message = "Not your turn")

        return when (action) {
            "draw" -> {
                hands.getValue(viewerId).add(drawOne())
                bump("${seats[seatIndex].name} drew a card")
                advance()
                runBots()
                NativeGameResult(true, true, "Card drawn")
            }
            "play" -> {
                val index = args["index"]?.toIntOrNull()
                    ?: return NativeGameResult(false, message = "Missing card index")
                val hand = hands.getValue(viewerId)
                val card = hand.getOrNull(index)
                    ?: return NativeGameResult(false, message = "Card not found")
                if (!isPlayable(card)) return NativeGameResult(false, message = "Card cannot be played")
                if (card.kind == Kind.WILD4 && hand.any { it !== card && it.color == activeColor }) {
                    return NativeGameResult(
                        false,
                        message = "Wild +4 chỉ được đánh khi không có lá cùng màu đang active"
                    )
                }
                val color = args["color"]?.uppercase()?.let {
                    runCatching { Color.valueOf(it) }.getOrNull()
                }
                playCard(viewerId, index, color)
                runBots()
                NativeGameResult(true, true, result ?: "Card played")
            }
            else -> NativeGameResult(false, message = "Unknown UNO action")
        }
    }

    override fun tick(nowMillis: Long): Boolean {
        if (finished) return false
        if (seats[turnIndex].id in eliminated) {
            advance()
            return true
        }
        if (seats[turnIndex].bot) {
            runBots()
            return true
        }
        return false
    }

    override fun snapshotState(nowMillis: Long): JsonObject = NativeGamePersistence.toJson(
        Snapshot(drawPile.toList(), discard.toList(), hands.mapValues { (_, value) -> value.toList() }, eliminated.toList(),
            turnIndex, direction, activeColor, revision, winner, result, log.toList())
    )

    private fun restoreSnapshot(state: JsonObject) {
        val s = NativeGamePersistence.fromJson(state, Snapshot::class.java)
        require(s.discard.isNotEmpty()) { "UNO recovery requires a discard top" }
        drawPile.clear(); s.drawPile.forEach(drawPile::add)
        discard.clear(); s.discard.forEach(discard::add)
        hands.values.forEach { it.clear() }
        seats.forEach { seat -> s.hands[seat.id].orEmpty().forEach(hands.getValue(seat.id)::add) }
        eliminated.clear(); s.eliminated.filter { id -> seats.any { it.id == id } }.forEach(eliminated::add)
        turnIndex = s.turnIndex.coerceIn(0, seats.lastIndex)
        direction = if (s.direction < 0) -1 else 1
        activeColor = s.activeColor.takeIf { it != Color.WILD } ?: Color.RED
        revision = s.revision.coerceAtLeast(0L)
        winner = s.winner?.takeIf { id -> seats.any { it.id == id } }
        result = s.result
        log.clear(); s.log.takeLast(32).forEach(log::add)
        if (!finished && seats[turnIndex].id in eliminated) advance()
    }

    private fun resign(seatIndex: Int): NativeGameResult {
        val seat = seats[seatIndex]
        if (!eliminated.add(seat.id)) return NativeGameResult(false, message = "Player eliminated")

        val hand = hands.getValue(seat.id)
        while (hand.isNotEmpty()) drawPile.addFirst(hand.removeAt(hand.lastIndex))
        bump("${seat.name} left the game")

        val active = seats.filter { it.id !in eliminated }
        if (active.size == 1) {
            finish("${active.first().name} wins", active.first().id)
        } else if (seatIndex == turnIndex) {
            advance()
        }
        return NativeGameResult(true, true, result ?: "Player left")
    }

    private fun playCard(id: String, index: Int, requested: Color?) {
        val hand = hands.getValue(id)
        val card = hand.removeAt(index)
        discard.add(card)
        activeColor = when {
            card.color != Color.WILD -> card.color
            requested != null && requested != Color.WILD -> requested
            else -> bestColor(hand)
        }

        bump(
            "${seat(id).name} played ${card.label()}" +
                if (card.color == Color.WILD) " → ${activeColor.name}" else ""
        )

        if (hand.isEmpty()) {
            finish("UNO! ${seat(id).name} wins", id)
            return
        }

        when (card.kind) {
            Kind.SKIP -> {
                advance()
                advance()
            }
            Kind.REVERSE -> {
                direction *= -1
                if (activeSeatCount() == 2) {
                    advance()
                    advance()
                } else {
                    advance()
                }
            }
            Kind.DRAW2 -> {
                advance()
                repeat(2) { hands.getValue(seats[turnIndex].id).add(drawOne()) }
                bump("${seats[turnIndex].name} draws 2")
                advance()
            }
            Kind.WILD4 -> {
                advance()
                repeat(4) { hands.getValue(seats[turnIndex].id).add(drawOne()) }
                bump("${seats[turnIndex].name} draws 4")
                advance()
            }
            else -> advance()
        }
    }

    private fun runBots() {
        var guard = 0
        while (!finished && guard++ < 12) {
            if (seats[turnIndex].id in eliminated) {
                advance()
                continue
            }
            if (!seats[turnIndex].bot) return

            val bot = seats[turnIndex]
            val hand = hands.getValue(bot.id)
            val candidates = hand.withIndex().filter { indexed ->
                isPlayable(indexed.value) &&
                    !(indexed.value.kind == Kind.WILD4 &&
                        hand.any { it !== indexed.value && it.color == activeColor })
            }
            if (candidates.isEmpty()) {
                hand.add(drawOne())
                bump("${bot.name} drew a card")
                advance()
            } else {
                val choice = candidates.maxBy { indexed ->
                    when (indexed.value.kind) {
                        Kind.WILD4 -> 12
                        Kind.DRAW2 -> 10
                        Kind.SKIP, Kind.REVERSE -> 8
                        Kind.WILD -> 6
                        else -> indexed.value.score
                    }
                }
                playCard(
                    bot.id,
                    choice.index,
                    bestColor(hand.filterIndexed { index, _ -> index != choice.index })
                )
            }
        }
    }

    private fun isPlayable(card: Card): Boolean {
        val top = discard.last()
        return card.color == Color.WILD ||
            card.color == activeColor ||
            (card.kind == top.kind && card.number == top.number)
    }

    private fun bestColor(cards: List<Card>): Color =
        Color.entries
            .filter { it != Color.WILD }
            .maxByOrNull { color -> cards.count { it.color == color } }
            ?: Color.RED

    private fun drawOne(): Card {
        if (drawPile.isEmpty()) reshuffle()
        return drawPile.removeLast()
    }

    private fun reshuffle() {
        if (discard.size <= 1) error("UNO deck exhausted")
        val top = discard.removeLast()
        val recycled = discard.toMutableList().shuffled(rng)
        discard.clear()
        discard.add(top)
        recycled.forEach(drawPile::add)
    }

    private fun advance() {
        var checked = 0
        do {
            turnIndex = (turnIndex + direction + seats.size) % seats.size
            checked++
        } while (seats[turnIndex].id in eliminated && checked < seats.size)
    }

    private fun activeSeatCount(): Int = seats.count { it.id !in eliminated }

    private fun applyOpening(card: Card) {
        when (card.kind) {
            Kind.SKIP -> advance()
            Kind.REVERSE -> direction = -1
            Kind.DRAW2 -> {
                repeat(2) { hands.getValue(seats[0].id).add(drawOne()) }
                advance()
            }
            Kind.WILD -> {
                activeColor = Color.entries.filter { it != Color.WILD }.random(rng)
            }
            else -> Unit
        }
    }

    private fun seat(id: String) = seats.first { it.id == id }

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

    private enum class Color { RED, YELLOW, GREEN, BLUE, WILD }
    private enum class Kind { NUMBER, SKIP, REVERSE, DRAW2, WILD, WILD4 }

    private data class Snapshot(val drawPile:List<Card>,val discard:List<Card>,val hands:Map<String,List<Card>>,val eliminated:List<String>,val turnIndex:Int,val direction:Int,val activeColor:Color,val revision:Long,val winner:String?,val result:String?,val log:List<String>)

    private data class Card(
        val color: Color,
        val kind: Kind,
        val number: Int = -1,
        val serial: Int
    ) {
        val score: Int get() = when (kind) {
            Kind.NUMBER -> number
            Kind.WILD, Kind.WILD4 -> 50
            else -> 20
        }

        fun label() = when (kind) {
            Kind.NUMBER -> "${color.name.lowercase().replaceFirstChar { it.uppercase() }} $number"
            Kind.SKIP -> "${color.name} Skip"
            Kind.REVERSE -> "${color.name} Reverse"
            Kind.DRAW2 -> "${color.name} +2"
            Kind.WILD -> "Wild"
            Kind.WILD4 -> "Wild +4"
        }
    }

    private fun buildDeck(): List<Card> {
        val cards = mutableListOf<Card>()
        var serial = 0
        for (color in listOf(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE)) {
            cards += Card(color, Kind.NUMBER, 0, serial++)
            for (number in 1..9) repeat(2) {
                cards += Card(color, Kind.NUMBER, number, serial++)
            }
            repeat(2) {
                cards += Card(color, Kind.SKIP, serial = serial++)
                cards += Card(color, Kind.REVERSE, serial = serial++)
                cards += Card(color, Kind.DRAW2, serial = serial++)
            }
        }
        repeat(4) {
            cards += Card(Color.WILD, Kind.WILD, serial = serial++)
            cards += Card(Color.WILD, Kind.WILD4, serial = serial++)
        }
        return cards
    }
}
