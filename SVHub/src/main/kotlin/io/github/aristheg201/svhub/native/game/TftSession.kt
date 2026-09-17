package io.github.aristheg201.svhub.native.game

import kotlin.random.Random

/** Lightweight deterministic server-authoritative auto battler. */
class TftSession(
    override val seats: List<NativeSeat>,
    seed: Long = Random.nextLong(),
    override val sessionId: String = NativeIds.session("tft")
) : NativeGameSession {
    override val gameId = "tft"
    private val rng = Random(seed)
    private val gold = mutableMapOf<String, Int>()
    private val hp = mutableMapOf<String, Int>()
    private val bench = mutableMapOf<String, MutableList<UnitCard>>()
    private val board = mutableMapOf<String, MutableMap<Int, UnitCard>>()
    private val shops = mutableMapOf<String, MutableList<String>>()
    private val ready = mutableSetOf<String>()
    private var round = 1
    private var phase = "shop"
    private var revision = 0L
    private var result: String? = null
    private var winner: String? = null
    private val log = ArrayDeque<String>()

    init {
        require(seats.size == 2)
        seats.forEach { s ->
            gold[s.id] = 10; hp[s.id] = 100; bench[s.id] = mutableListOf(); board[s.id] = linkedMapOf(); shops[s.id] = rollShop()
        }
    }
    override val finished get() = result != null
    override val winnerSeatId get() = winner

    override fun viewFor(viewerId: String): NativeGameView {
        val mine = board[viewerId].orEmpty()
        val cells = MutableList(28) { "" }
        mine.forEach { (slot, u) -> if (slot in cells.indices) cells[slot] = "${u.species}:${u.star}" }
        return NativeGameView(
            sessionId, gameId, "Pokemon TFT", if (finished) "finished" else phase,
            status = result ?: "Round $round • ${gold[viewerId] ?: 0}g • ${hp[viewerId] ?: 0} HP",
            boardWidth = 7, boardHeight = 4, board = cells,
            cards = shops[viewerId].orEmpty().mapIndexed { i, id -> NativeCardView("shop:$i", id, "Cost ${cost(id)}g", "shop", cost(id)) },
            actions = listOf(
                NativeActionView("refresh", "Refresh shop", "2 gold", !finished && phase == "shop"),
                NativeActionView("ready", "Ready", "Start combat", !finished && phase == "shop" && viewerId !in ready),
                NativeActionView("resign", "Resign", "", !finished)
            ),
            fields = linkedMapOf(
                "gold" to (gold[viewerId] ?: 0).toString(), "hp" to (hp[viewerId] ?: 0).toString(), "round" to round.toString(),
                "bench" to bench[viewerId].orEmpty().joinToString(",") { "${it.species}:${it.star}" }
            ), log = log.toList().takeLast(12), revision = revision, finished = finished,
            winner = winner?.let { id -> seats.firstOrNull { it.id == id }?.name }
        )
    }

    override fun act(viewerId: String, action: String, args: Map<String, String>): NativeGameResult {
        if (finished) return NativeGameResult(false, message = "Game finished")
        if (viewerId !in seats.map { it.id }) return NativeGameResult(false, message = "Spectator")
        return when (action) {
            "buy" -> buy(viewerId, args["index"]?.toIntOrNull())
            "deploy" -> deploy(viewerId, args["bench"]?.toIntOrNull(), args["slot"]?.toIntOrNull())
            "refresh" -> refresh(viewerId)
            "ready" -> { ready += viewerId; bump("${name(viewerId)} ready"); maybeCombat(); NativeGameResult(true, true, "Ready") }
            "resign" -> { finish("${name(viewerId)} resigned", seats.first { it.id != viewerId }.id); NativeGameResult(true, true, result.orEmpty()) }
            else -> NativeGameResult(false, message = "Unknown TFT action")
        }
    }

    override fun tick(nowMillis: Long): Boolean {
        if (finished) return false
        val bots = seats.filter { it.bot && it.id !in ready }
        if (phase == "shop" && bots.isNotEmpty()) {
            bots.forEach { bot -> botShop(bot.id) }
            maybeCombat()
            return true
        }
        return false
    }

    private fun buy(id: String, index: Int?): NativeGameResult {
        if (phase != "shop") return NativeGameResult(false, message = "Not shop phase")
        val i = index ?: return NativeGameResult(false, message = "Missing index")
        val shop = shops[id] ?: return NativeGameResult(false, message = "No shop")
        val species = shop.getOrNull(i) ?: return NativeGameResult(false, message = "Bad index")
        val c = cost(species)
        if ((gold[id] ?: 0) < c) return NativeGameResult(false, message = "Not enough gold")
        if ((bench[id]?.size ?: 0) >= 9) return NativeGameResult(false, message = "Bench full")
        gold[id] = gold.getValue(id) - c
        bench.getValue(id) += UnitCard(species, 1)
        shop[i] = POOL.random(rng)
        combine(id, species)
        bump("${name(id)} bought $species")
        return NativeGameResult(true, true, "Bought $species")
    }

    private fun deploy(id: String, benchIndex: Int?, slot: Int?): NativeGameResult {
        if (phase != "shop") return NativeGameResult(false, message = "Not shop phase")
        val bi = benchIndex ?: return NativeGameResult(false, message = "Missing bench")
        val s = slot ?: return NativeGameResult(false, message = "Missing slot")
        if (s !in 0 until 28) return NativeGameResult(false, message = "Bad slot")
        val b = bench[id] ?: return NativeGameResult(false, message = "No bench")
        val unit = b.getOrNull(bi) ?: return NativeGameResult(false, message = "Bad bench index")
        val current = board.getValue(id)[s]
        board.getValue(id)[s] = unit
        b.removeAt(bi)
        if (current != null) b += current
        bump("${name(id)} deployed ${unit.species}")
        return NativeGameResult(true, true, "Deployed")
    }

    private fun refresh(id: String): NativeGameResult {
        if (phase != "shop" || (gold[id] ?: 0) < 2) return NativeGameResult(false, message = "Cannot refresh")
        gold[id] = gold.getValue(id) - 2; shops[id] = rollShop(); bump("${name(id)} refreshed")
        return NativeGameResult(true, true, "Refreshed")
    }

    private fun combine(id: String, species: String) {
        for (star in 1..2) {
            val all = mutableListOf<Pair<String, Int>>()
            bench.getValue(id).forEachIndexed { i, u -> if (u.species == species && u.star == star) all += "b" to i }
            board.getValue(id).forEach { (s, u) -> if (u.species == species && u.star == star) all += "d" to s }
            if (all.size < 3) continue
            repeat(3) {
                val (where, pos) = all[it]
                if (where == "b") bench.getValue(id)[pos] = UnitCard("__consumed__", 0) else board.getValue(id).remove(pos)
            }
            bench.getValue(id).removeIf { it.star == 0 }
            bench.getValue(id) += UnitCard(species, star + 1)
            bump("$species -> ${star + 1} star")
        }
    }

    private fun maybeCombat() {
        if (ready.size < seats.size) return
        phase = "combat"
        val a = seats[0].id; val b = seats[1].id
        val pa = power(a); val pb = power(b)
        when {
            pa > pb -> damage(b, ((pa - pb) / 12 + 5).coerceAtMost(25))
            pb > pa -> damage(a, ((pb - pa) / 12 + 5).coerceAtMost(25))
            else -> { damage(a, 2); damage(b, 2) }
        }
        if (!finished) {
            round++; seats.forEach { seat -> gold[seat.id] = (gold[seat.id] ?: 0) + 5 + (round / 5).coerceAtMost(5); shops[seat.id] = rollShop() }
            ready.clear(); phase = "shop"; bump("Round $round")
        }
    }

    private fun botShop(id: String) {
        repeat(3) { if ((gold[id] ?: 0) >= 1 && (bench[id]?.size ?: 0) < 9) buy(id, rng.nextInt(5)) }
        val b = bench.getValue(id)
        while (b.isNotEmpty() && board.getValue(id).size < 7) deploy(id, 0, (0 until 28).first { it !in board.getValue(id) })
        ready += id
    }
    private fun power(id: String): Int {
        val units = board[id].orEmpty().values
        val traits = units.groupingBy { trait(it.species) }.eachCount()
        val synergy: Int = traits.values.fold(0) { acc, count -> acc + if (count >= 4) 18 else if (count >= 2) 7 else 0 }
        return units.sumOf { cost(it.species) * it.star * it.star * 10 } + synergy
    }
    private fun damage(id: String, amount: Int) { hp[id] = (hp[id] ?: 100) - amount; bump("${name(id)} -$amount HP"); if ((hp[id] ?: 0) <= 0) finish("${name(id)} eliminated", seats.first { it.id != id }.id) }
    private fun rollShop() = MutableList(5) { POOL.random(rng) }
    private fun cost(s: String) = COST[s] ?: 1
    private fun trait(s: String) = TRAIT[s] ?: "normal"
    private fun name(id: String) = seats.firstOrNull { it.id == id }?.name ?: id
    private fun bump(s: String) { revision++; log += s; while (log.size > 30) log.removeFirst() }
    private fun finish(s: String, w: String?) { result = s; winner = w; phase = "finished"; bump(s) }
    private data class UnitCard(val species: String, val star: Int)
    companion object {
        private val COST = linkedMapOf("pikachu" to 1,"eevee" to 1,"bulbasaur" to 1,"charmander" to 1,"squirtle" to 1,"lucario" to 2,"gardevoir" to 2,"gengar" to 2,"dragonite" to 3,"tyranitar" to 3,"metagross" to 3,"garchomp" to 3,"mewtwo" to 5,"rayquaza" to 5)
        private val POOL = COST.keys.toList()
        private val TRAIT = mapOf("pikachu" to "electric","eevee" to "normal","bulbasaur" to "grass","charmander" to "fire","squirtle" to "water","lucario" to "fighting","gardevoir" to "psychic","gengar" to "ghost","dragonite" to "dragon","tyranitar" to "dark","metagross" to "steel","garchomp" to "dragon","mewtwo" to "psychic","rayquaza" to "dragon")
    }
}
