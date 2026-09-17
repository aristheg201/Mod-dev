package io.github.aristheg201.svhub.native.game

import kotlin.math.abs

/** Pure planner for the TFT session. Reads only NativeGameView snapshots. */
object TftBotPlanner {
    fun shouldThink(view: NativeGameView): Boolean = !view.finished && view.fields["eliminated"] != "true" && view.phase in setOf("planning", "draft")

    fun plan(view: NativeGameView, difficulty: NativeBotDifficulty): NativeBotPlan {
        if (!shouldThink(view)) return NativeBotPlan(emptyList())
        return if (view.phase == "draft") planDraft(view, difficulty) else planPlanning(view, difficulty)
    }

    private fun planDraft(view: NativeGameView, difficulty: NativeBotDifficulty): NativeBotPlan {
        val offers = view.fields["draft"].orEmpty().split(';').mapNotNull { raw ->
            if (raw.isBlank()) return@mapNotNull null
            val p = raw.split('~')
            if (p.size < 8 || p[4].isNotBlank() || p[5] != "1") return@mapNotNull null
            Draft(p[0].toIntOrNull() ?: return@mapNotNull null, p[1], p[6].toIntOrNull() ?: 1, p[7])
        }
        val ordered = when (difficulty) {
            NativeBotDifficulty.EASY -> offers.sortedBy { it.index }
            NativeBotDifficulty.NORMAL -> offers.sortedWith(compareByDescending<Draft> { it.cost }.thenBy { it.index })
            NativeBotDifficulty.HARD -> offers.sortedWith(compareByDescending<Draft> { it.cost * 20 + traitFit(view, it.traits) }.thenBy { it.index })
        }
        return NativeBotPlan(ordered.map { NativeBotAction("draft_pick", mapOf("index" to it.index.toString())) })
    }

    private fun planPlanning(view: NativeGameView, difficulty: NativeBotDifficulty): NativeBotPlan {
        val out = mutableListOf<NativeBotAction>()
        val gold = view.fields["gold"]?.toIntOrNull() ?: 0
        val cap = view.fields["unitCap"]?.toIntOrNull() ?: 1
        val boardCount = view.fields["boardCount"]?.toIntOrNull() ?: 0
        val bench = parseBench(view.fields["bench"].orEmpty())

        val augments = view.fields["augmentChoices"].orEmpty().split(';').mapNotNull { raw ->
            if (raw.isBlank()) return@mapNotNull null
            val p = raw.split('~')
            if (p.size < 4) return@mapNotNull null
            Augment(p[0], p[3].toIntOrNull() ?: 50)
        }
        if (augments.isNotEmpty()) {
            val ordered = when (difficulty) {
                NativeBotDifficulty.EASY -> augments.sortedBy { it.id }
                NativeBotDifficulty.NORMAL -> augments.sortedByDescending { it.weight }
                NativeBotDifficulty.HARD -> augments.sortedWith(compareByDescending<Augment> { it.weight }.thenBy { it.id })
            }
            out += ordered.map { NativeBotAction("choose_augment", mapOf("id" to it.id)) }
        }

        if (boardCount < cap && bench.isNotEmpty()) {
            val chosen = when (difficulty) {
                NativeBotDifficulty.EASY -> bench.minByOrNull { it.cost * 10 + it.star }
                NativeBotDifficulty.NORMAL -> bench.maxByOrNull { it.cost * 10 + it.star * 8 }
                NativeBotDifficulty.HARD -> bench.maxByOrNull { it.cost * 12 + it.star * 12 + roleScore(it.role) }
            }
            if (chosen != null) {
                formationSlots(chosen.role, difficulty).forEach { slot ->
                    out += NativeBotAction("deploy", mapOf("bench" to chosen.index.toString(), "slot" to slot.toString()))
                }
            }
        }

        val shop = view.cards.filter { it.value <= gold }.mapNotNull { card ->
            val idx = card.id.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            Shop(idx, card.value, card.meta["traits"].orEmpty(), card.meta["role"].orEmpty())
        }
        val orderedShop = when (difficulty) {
            NativeBotDifficulty.EASY -> shop.sortedWith(compareBy<Shop> { it.cost }.thenBy { it.index })
            NativeBotDifficulty.NORMAL -> shop.sortedWith(compareByDescending<Shop> { it.cost }.thenByDescending { traitFit(view, it.traits) })
            NativeBotDifficulty.HARD -> shop.sortedWith(compareByDescending<Shop> { it.cost * 20 + traitFit(view, it.traits) * 5 + roleScore(it.role) }.thenBy { it.index })
        }
        out += orderedShop.map { NativeBotAction("buy", mapOf("index" to it.index.toString())) }

        val xpNext = view.fields["xpNext"]?.toIntOrNull() ?: 0
        val level = view.fields["level"]?.toIntOrNull() ?: 2
        val interestFloor = when (difficulty) { NativeBotDifficulty.EASY -> 0; NativeBotDifficulty.NORMAL -> 10; NativeBotDifficulty.HARD -> 30 }
        if (gold >= 4 + interestFloor && xpNext > 0 && level < 10 && (difficulty == NativeBotDifficulty.HARD || boardCount >= cap)) {
            out += NativeBotAction("buy_xp")
        }
        if (gold >= 2 + interestFloor && shop.isEmpty()) out += NativeBotAction("refresh")
        return NativeBotPlan(out.take(16))
    }

    private fun parseBench(raw: String): List<Bench> = raw.split(';').mapNotNull { value ->
        if (value.isBlank()) return@mapNotNull null
        val p = value.split('~')
        if (p.size < 9) return@mapNotNull null
        Bench(
            index = p[0].toIntOrNull() ?: return@mapNotNull null,
            star = p[4].toIntOrNull() ?: 1,
            cost = p[7].toIntOrNull() ?: 1,
            role = p[8]
        )
    }

    private fun traitFit(view: NativeGameView, traits: String): Int {
        val active = view.fields["traits"].orEmpty().split(';').mapNotNull { row ->
            val p = row.split('~')
            p.firstOrNull()?.takeIf(String::isNotBlank)?.let { it to (p.getOrNull(2)?.toIntOrNull() ?: 0) }
        }.toMap()
        return traits.split(',').sumOf { trait -> if (trait.isBlank()) 0 else 1 + (active[trait] ?: 0) }
    }

    private fun formationSlots(role: String, difficulty: NativeBotDifficulty): List<Int> {
        val preferred = when (role.lowercase()) {
            "guardian", "tank", "fighter", "striker" -> listOf(3, 2, 4, 1, 5, 0, 6, 10, 9, 11)
            "ranger", "caster", "support" -> listOf(24, 23, 25, 22, 26, 21, 27, 17, 16, 18)
            else -> listOf(10, 9, 11, 17, 16, 18, 3, 24)
        }
        return if (difficulty == NativeBotDifficulty.EASY) preferred.reversed() else preferred
    }

    private fun roleScore(role: String): Int = when (role.lowercase()) {
        "guardian", "tank" -> 12
        "caster", "ranger" -> 10
        "striker", "fighter" -> 9
        else -> 5
    }

    private data class Bench(val index: Int, val star: Int, val cost: Int, val role: String)
    private data class Shop(val index: Int, val cost: Int, val traits: String, val role: String)
    private data class Draft(val index: Int, val unitId: String, val cost: Int, val traits: String)
    private data class Augment(val id: String, val weight: Int)
}
