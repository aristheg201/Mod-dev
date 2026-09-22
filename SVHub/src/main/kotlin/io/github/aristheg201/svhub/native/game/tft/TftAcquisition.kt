package io.github.aristheg201.svhub.native.game.tft

/** Pure final-state simulation. No currency, pool, serial or live unit is mutated here. */
object TftAcquisition {
    enum class Outcome { PLACE_ON_BENCH, STAR_UP, CHAIN_STAR_UP, REJECT }
    data class Upgrade(val instanceId: String, val unitId: String, val star: Int)
    data class Resolution(
        val outcome: Outcome,
        val bench: List<TftOwnedUnit?> = emptyList(),
        val board: Map<Int, TftOwnedUnit> = emptyMap(),
        val returnedItems: List<String> = emptyList(),
        val upgrades: List<Upgrade> = emptyList()
    )
    private data class Located(val origin: Int, val index: Int, val unit: TftOwnedUnit)

    fun resolve(bench: List<TftOwnedUnit?>, board: Map<Int, TftOwnedUnit>, incoming: TftOwnedUnit,
                mergeItems: (List<String>) -> Pair<List<String>, List<String>> = { it.take(3) to it.drop(3) }): Resolution {
        val rejected = Resolution(Outcome.REJECT)
        val existing = board.values + bench.filterNotNull()
        if (incoming.star !in 1..3 || existing.any { it.star !in 1..3 } ||
            (existing + incoming).map { it.instanceId }.distinct().size != existing.size + 1) return rejected
        fun copy(unit: TftOwnedUnit) = unit.copy(items = unit.items.toMutableList())
        val units = (board.toSortedMap().map { Located(0, it.key, copy(it.value)) } +
            bench.mapIndexedNotNull { i, unit -> unit?.let { Located(1, i, copy(it)) } } +
            Located(2, 0, copy(incoming))).toMutableList()
        val returned = mutableListOf<String>()
        val upgrades = mutableListOf<Upgrade>()
        for (star in 1..2) {
            while (true) {
                val group = units.filter { it.unit.unitId == incoming.unitId && it.unit.star == star }
                    .sortedWith(compareBy<Located> { it.origin }.thenBy { it.index }.thenBy { it.unit.instanceId }).take(3)
                if (group.size != 3) break
                val primary = group.first()
                val (items, overflow) = mergeItems(group.flatMap { it.unit.items })
                returned += overflow
                val upgraded = primary.unit.copy(star = star + 1, items = items.toMutableList(),
                    poolCopies = group.sumOf { it.unit.reservedCopies() })
                units.removeAll(group.toSet())
                units += primary.copy(unit = upgraded)
                upgrades += Upgrade(upgraded.instanceId, upgraded.unitId, upgraded.star)
            }
        }
        val resultBench = MutableList<TftOwnedUnit?>(bench.size) { null }
        val resultBoard = linkedMapOf<Int, TftOwnedUnit>()
        units.filter { it.origin == 0 }.forEach { resultBoard[it.index] = it.unit }
        units.filter { it.origin == 1 }.forEach { resultBench[it.index] = it.unit }
        for (unit in units.filter { it.origin == 2 }) {
            val empty = resultBench.indexOfFirst { it == null }
            if (empty < 0) return rejected
            resultBench[empty] = unit.unit
        }
        return Resolution(when (upgrades.size) {
            0 -> Outcome.PLACE_ON_BENCH
            1 -> Outcome.STAR_UP
            else -> Outcome.CHAIN_STAR_UP
        }, resultBench, resultBoard, returned, upgrades)
    }
}

fun TftOwnedUnit.reservedCopies(): Int = poolCopies ?: when (star) { 2 -> 3; 3 -> 9; else -> 1 }
fun TftOwnedUnit.poolSourceUnitId(): String = poolUnitId?.takeIf(String::isNotBlank) ?: unitId
