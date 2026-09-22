package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import io.github.aristheg201.svhub.engine.Stat
import io.github.aristheg201.svhub.native.game.*
import kotlin.test.*

class TftEliteAndConvergenceTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private val elite = base.units.first { it.elite != null }
    private fun board(vararg ids: String) = ids.mapIndexed { i, id -> i to TftOwnedUnit("a:$i", id) }.toMap()
    private fun combat(set: TftSetDefinition = base, own: Map<Int,TftOwnedUnit>, enemy: Map<Int,TftOwnedUnit> = mapOf(3 to TftOwnedUnit("enemy", "snorlax"))) =
        TftCombatEngine(set, "a", own, emptyList(), "b", enemy, emptyList(), 99L)

    @Test fun authoredShopCostsAndSummonExclusion() {
        assertEquals(listOf(4, 3, 5), listOf("giratina", "dialga", "palkia").map { id -> base.units.first { it.id == id }.cost })
        assertFalse(base.units.first { it.id == "arceus" }.shopEligible)
        assertEquals(5, base.units.count { it.elite != null })
        assertEquals((1..5).toList(), base.units.filter { it.elite != null }.map { it.cost }.sorted())
        base.units.filter { it.elite != null }.forEach { assertEquals(2,it.purchaseStar); assertTrue(it.price > it.cost) }
    }
    @Test fun allThreeDistinctDeployedMembersSynchronizeIntoExactlyOneArceus() {
        val combat = combat(own = board("giratina", "dialga", "palkia", "giratina"))
        assertEquals(1, combat.units.count { it.definition.id == "arceus" && it.alive })
        assertEquals(2, combat.units.count { !it.alive && it.definition.id in setOf("giratina","dialga","palkia") })
        val summoned = combat.units.single { it.definition.id == "arceus" }
        assertEquals("cobblemon:arceus", summoned.definition.presentation.species)
        assertEquals("a", summoned.ownerId)
    }
    @Test fun arceusDeathRestoresTheThreeSynchronizedMembers() {
        val fragile = base.copy(units = base.units.map {
            when (it.id) {
                "arceus" -> it.copy(stats = it.stats.copy(hp = 1, defense = 0, specialDefense = 0))
                "snorlax" -> it.copy(stats = it.stats.copy(attackDamage = 5000, attackSpeed = 5.0, range = 6))
                else -> it
            }
        })
        val combat = combat(fragile, own = board("giratina","dialga","palkia"), enemy = board("snorlax"))
        repeat(100) { if (!combat.finished) combat.step(50) }
        assertFalse(combat.units.any { it.definition.id == "arceus" && it.alive })
        val restored = combat.units.filter { it.ownerId == "a" && it.alive }.map { it.definition.id }.toSet()
        assertEquals(setOf("giratina","dialga","palkia"), restored)
    }

    @Test fun missingTrioMemberNeverQualifiesThroughDuplicates() {
        for (missing in listOf("giratina", "dialga", "palkia")) {
            val ids = listOf("giratina", "dialga", "palkia").filterNot { it == missing }
            assertFalse(combat(own = board(*(ids + ids).toTypedArray())).units.any { it.definition.id == "arceus" })
        }
    }
    @Test fun bothTeamsMayConvergeOnceAndRecoveryDoesNotResummon() {
        val ally = board("giratina", "dialga", "palkia")
        val enemy = ally.mapValues { it.value.copy(instanceId = "b:${it.key}") }
        val running = combat(own = ally, enemy = enemy)
        assertEquals(2, running.units.count { it.definition.id == "arceus" })
        val gson = Gson()
        val restored = TftCombatEngine(base, gson.fromJson(gson.toJson(running.snapshotState()), TftCombatSnapshot::class.java))
        repeat(10) { running.step(50); restored.step(50) }
        assertEquals(running.snapshotState(), restored.snapshotState())
        assertEquals(2, restored.units.count { it.definition.id == "arceus" })
        assertEquals(3, ally.size, "Combat summons must never enter owned deployment state")
    }
    @Test fun convergenceStillWorksWithMoreThanTwelveCombatants() {
        val ally = board("giratina", "dialga", "palkia", "pikachu", "eevee", "snorlax", "gengar", "lucario", "charizard")
        val enemy = ally.mapValues { it.value.copy(instanceId = "b:${it.key}") }
        val running = combat(own = ally, enemy = enemy)
        assertEquals(20, running.units.size)
        assertEquals(2, running.units.count { it.definition.id == "arceus" })
    }
    @Test fun eliteBoostIsRestrictedToDeployedFranchiseAlliesAndSurvivesRecovery() {
        val ally = base.units.first { it.team == elite.team && it.elite == null }
        val own = board(elite.id, ally.id, "pikachu")
        val noBoost = base.copy(units = base.units.map { if (it.id == elite.id) it.copy(elite = null) else it })
        val control = combat(noBoost, own)
        val boosted = combat(own = own)
        assertEquals(control.units.first { it.instanceId == "a:1" }.maxHp * 2, boosted.units.first { it.instanceId == "a:1" }.maxHp)
        for (id in listOf("a:0", "a:2", "enemy")) assertEquals(control.units.first { it.instanceId == id }.maxHp, boosted.units.first { it.instanceId == id }.maxHp)
        val stats = boosted.snapshotState().effects!!.units().associateBy { it.id() }
        assertEquals(1.0, stats.getValue("a:1").stats()[Stat.DAMAGE_AMPLIFICATION])
        for (id in listOf("a:0", "a:2", "enemy")) assertEquals(0.0, stats.getValue(id).stats()[Stat.DAMAGE_AMPLIFICATION] ?: 0.0)
        val recovered = TftCombatEngine(base, boosted.snapshotState())
        assertEquals(boosted.snapshotState(), recovered.snapshotState())
    }
    @Test fun elitePurchaseHidesEveryEliteUntilItIsSoldAndPersistsOwnership() {
        val definition = base.copy(shopOdds = base.shopOdds.map { it.copy(odds = listOf(0,0,0,0,100)) })
        val saved = TftSession(seats, 91L, "elite-shop", definition).snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        player.addProperty("gold", 999)
        player.getAsJsonArray("shop").set(0, Gson().toJsonTree(elite.id))
        val s = NativeGameRestorer.restore("tft", seats, "elite-shop", saved)
        assertTrue(s.buyOffer("a", "0").accepted)
        val bench = s.viewFor("a").fields.getValue("bench").split('~')
        assertEquals("2", bench[4])
        assertEquals((999-elite.price).toString(),s.viewFor("a").fields["gold"])
        val recovered = NativeGameRestorer.restore("tft", seats, "elite-shop", s.snapshotState())
        repeat(30) {
            assertTrue(recovered.viewFor("a").cards.none { it.meta["elite"] == "true" })
            assertTrue(recovered.act("a", "refresh", emptyMap()).accepted)
        }
        assertTrue(recovered.act("a", "sell", mapOf("origin" to "bench", "index" to "0")).accepted)
        var appeared = false
        repeat(50) {
            assertTrue(recovered.act("a", "refresh", emptyMap()).accepted)
            appeared = appeared || recovered.viewFor("a").cards.any { it.meta["elite"] == "true" }
        }
        assertTrue(appeared, "Selling elite must restore elite eligibility")
    }
    @Test fun riskyRosterAndPersistentHoopaEvolutionAreAuthored() {
        assertTrue(base.units.count { "risky" in it.tags } >= 20)
        for (trait in listOf("glass_cannon","blood_pact","void_contract","wild_gambit","summon_spirit","hoopa_domain")) assertTrue(base.traits.any { it.id == trait })
        val hoopa = base.units.first { it.id == "hoopa_sukuna" }
        val evolution = assertNotNull(hoopa.permanentEvolution)
        assertEquals(3, evolution.afterCombats)
        assertEquals("hoopa_unbound_sukuna", evolution.targetUnit)
        assertFalse(base.units.first { it.id == evolution.targetUnit }.shopEligible)
        assertTrue(base.units.first { it.id == "regiraga" }.presentation.resolverAspects().contains("regiraga"))
    }
    @Test fun invalidConvergenceAndEliteRulesAreRejected() {
        assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(base.copy(convergences = listOf(TftConvergenceDefinition("bad", setOf("pikachu","missing"),"arceus")))) }
        assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(base.copy(units = base.units.map { if(it.id == elite.id) it.copy(purchaseStar=1) else it })) }
    }
}
