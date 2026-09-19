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
        val strategy=Strategy.parse(view.fields["botStrategy"].orEmpty())
        val gold = view.fields["gold"]?.toIntOrNull() ?: 0
        val level = view.fields["level"]?.toIntOrNull() ?: 2
        val cap = view.fields["unitCap"]?.toIntOrNull() ?: 1
        val boardCount = view.fields["boardCount"]?.toIntOrNull() ?: 0
        val bench = parseBench(view.fields["bench"].orEmpty())

        val augments = view.fields["augmentChoices"].orEmpty().split(';').mapNotNull { raw ->
            if (raw.isBlank()) return@mapNotNull null
            val p = raw.split('~')
            if (p.size < 4) return@mapNotNull null
            Augment(p[0], p[3].toIntOrNull() ?: 50, p.getOrNull(4).orEmpty())
        }
        if (augments.isNotEmpty()) {
            val ordered = when (difficulty) {
                NativeBotDifficulty.EASY -> augments.sortedBy { it.id }
                NativeBotDifficulty.NORMAL -> augments.sortedByDescending { it.weight }
                NativeBotDifficulty.HARD -> augments.sortedWith(compareByDescending<Augment> { it.weight + strategy.augmentScore(it.tags) }.thenBy { it.id })
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
                formationSlots(chosen.role, difficulty, view.boardWidth.coerceAtLeast(1), view.boardHeight.coerceAtLeast(1),strategy.positioning).forEach { slot ->
                    out += NativeBotAction("deploy", mapOf("bench" to chosen.index.toString(), "slot" to slot.toString()))
                }
            }
        }

        val shop = view.cards.filter { it.value <= gold }.mapNotNull { card ->
            val idx = card.id.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            Shop(idx, card.meta["unit"].orEmpty(), card.value, card.meta["traits"].orEmpty(), card.meta["role"].orEmpty(),card.meta["team"].orEmpty(),card.meta["tags"].orEmpty(),card.meta["ownedCopies"]?.toIntOrNull()?:0)
        }
        val orderedShop = when (difficulty) {
            NativeBotDifficulty.EASY -> shop.sortedWith(compareBy<Shop> { it.cost }.thenBy { it.index })
            NativeBotDifficulty.NORMAL -> shop.sortedWith(compareByDescending<Shop> { it.cost }.thenByDescending { traitFit(view, it.traits) })
            NativeBotDifficulty.HARD -> shop.sortedWith(compareByDescending<Shop> { it.cost * 20 + traitFit(view, it.traits) * 5 + roleScore(it.role) + strategy.shopScore(it,level,contestedUnitIds(view)) }.thenBy { it.index })
        }
        out += orderedShop.map { NativeBotAction("buy", mapOf("index" to it.index.toString())) }

        // Item actions use only this participant's public snapshot. The authoritative session
        // validates ownership, recipes, capacity, and target legality for every candidate.
        val itemSlots=view.fields["itemBench"].orEmpty().split(';').mapIndexedNotNull{i,v->v.takeIf(String::isNotBlank)?.let{i}}
        val carries=bench.sortedByDescending { it.cost*20+it.star*15+roleScore(it.role) }
        for(item in itemSlots) for(carry in carries) out+=NativeBotAction("equip_item",mapOf("item" to item.toString(),"bench" to carry.index.toString()))

        val xpNext = view.fields["xpNext"]?.toIntOrNull() ?: 0
        val health=view.fields["health"]?.toIntOrNull()?:100
        val emergency=health <= (strategy.economy["emergencyHp"]?:0.0)
        val interestFloor = when (difficulty) { NativeBotDifficulty.EASY -> 0; NativeBotDifficulty.NORMAL -> 10; NativeBotDifficulty.HARD -> if(emergency) 0 else strategy.economy["interestFloor"]?.toInt() ?: 30 }
        val capLevel=(strategy.level["capLevel"]?:10.0).toInt()
        val fastLevelGold=(strategy.level["fastLevelGold"]?:Double.MAX_VALUE).toInt()
        if (gold >= 4 + interestFloor && xpNext > 0 && level < capLevel && (difficulty == NativeBotDifficulty.HARD && (gold>=fastLevelGold||emergency) || boardCount >= cap)) {
            out += NativeBotAction("buy_xp")
        }
        val streak=view.fields["streak"]?.toIntOrNull()?:0
        val contested=contestedUnitIds(view).size
        val stabilize=(strategy.roll["stabilizeGold"]?:20.0).toInt()
        val slowRoll=(strategy.economy["slowRollLevel"]?:-1.0).toInt()==level
        if (gold >= 2 + interestFloor && (shop.isEmpty() || difficulty==NativeBotDifficulty.HARD && (emergency&&gold>=stabilize || slowRoll || boardCount<cap && (streak<0 || contested>2)))) out += NativeBotAction("refresh")
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

    internal fun formationSlots(role: String, difficulty: NativeBotDifficulty, columns:Int, rows:Int,profile:Map<String,Double> = emptyMap()): List<Int> {
        val center=(columns-1)/2.0
        val cells=(0 until columns*rows).toList()
        val front=cells.sortedWith(compareBy<Int>{it/columns}.thenBy{ kotlin.math.abs(it%columns-center) })
        val back=cells.sortedWith(compareByDescending<Int>{it/columns}.thenBy{ kotlin.math.abs(it%columns-center) })
        val preferred = when (role.lowercase()) {
            "guardian", "tank", "fighter", "striker" -> front
            "ranger", "caster", "support" -> back
            else -> cells.sortedBy { kotlin.math.abs(it%columns-center)+kotlin.math.abs(it/columns-(rows-1)/2.0) }
        }
        val spread=profile["spread"]?:0.0
        val protection=profile["carryProtection"]?:0.0
        val adjusted=if(profile.isEmpty()) preferred else preferred.sortedBy { cell ->
            val x=cell%columns;val y=cell/columns
            val edge=if(spread>0) minOf(x,columns-1-x).toDouble() else 0.0
            val protected=if(protection>0&&role.lowercase() in setOf("caster","ranger","support")) kotlin.math.abs(x-center)-protection else 0.0
            preferred.indexOf(cell)+edge+protected
        }
        return if (difficulty == NativeBotDifficulty.EASY) adjusted.reversed() else adjusted
    }

    private fun roleScore(role: String): Int = when (role.lowercase()) {
        "guardian", "tank" -> 12
        "caster", "ranger" -> 10
        "striker", "fighter" -> 9
        else -> 5
    }

    private data class Bench(val index: Int, val star: Int, val cost: Int, val role: String)
    private data class Shop(val index: Int,val unitId:String, val cost: Int, val traits: String, val role: String,val team:String,val tags:String,val ownedCopies:Int)
    private data class Draft(val index: Int, val unitId: String, val cost: Int, val traits: String)
    private data class Augment(val id: String, val weight: Int,val tags:String)
    private fun contestedUnitIds(view:NativeGameView)=view.fields["contestedUnits"].orEmpty().split(',').filter(String::isNotBlank).toSet()
    private data class Transition(val phase:String,val minLevel:Int,val maxLevel:Int,val team:String,val minimumCopies:Int,val maximumContested:Int)
    private data class Strategy(val preferredTeams:Set<String>,val fallbackTeams:Set<String>,val traits:Set<String>,val carryRoles:Set<String>,val itemTags:Set<String>,val augmentTags:Set<String>,val economy:Map<String,Double>,val roll:Map<String,Double>,val level:Map<String,Double>,val positioning:Map<String,Double>,val transitions:List<Transition>){
        fun shopScore(s:Shop,level:Int,contested:Set<String>):Int {val transition=transitions.firstOrNull{level in it.minLevel..it.maxLevel&&s.ownedCopies>=it.minimumCopies&&contested.size<=it.maximumContested};val targetTeams=transition?.let{setOf(it.team)}?:preferredTeams;return (if(s.team in targetTeams)45 else if(s.team in fallbackTeams)15 else 0)+s.traits.split(',').count{it in traits}*18+(if(s.role in carryRoles)14 else 0)+s.tags.split(',').count{it in itemTags}*3+s.ownedCopies*(roll["upgradeWeight"]?.toInt()?:9)-(if(s.unitId in contested)roll["contestedPenalty"]?.toInt()?:0 else 0)}
        fun augmentScore(tags:String)=tags.split(',').count{it in augmentTags}*20
        companion object {
            fun parse(raw: String): Strategy {
                val parts = raw.split('~')
                fun values(index: Int) = parts.getOrNull(index).orEmpty().split(',').filter(String::isNotBlank).toSet()
                fun profile(index:Int)=parts.getOrNull(index).orEmpty().split(',').mapNotNull { value ->
                    val key = value.substringBefore('=').takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val amount = value.substringAfter('=', "").toDoubleOrNull() ?: return@mapNotNull null
                    key to amount
                }.toMap()
                val transitions=parts.getOrNull(11).orEmpty().split(',').mapNotNull { value->val p=value.split(':');if(p.size!=6)return@mapNotNull null;Transition(p[0],p[1].toIntOrNull()?:return@mapNotNull null,p[2].toIntOrNull()?:return@mapNotNull null,p[3],p[4].toIntOrNull()?:0,p[5].toIntOrNull()?:99)}
                return Strategy(values(1), values(2), values(3), values(4), values(5), values(6), profile(7),profile(8),profile(9),profile(10),transitions)
            }
        }
    }
}
