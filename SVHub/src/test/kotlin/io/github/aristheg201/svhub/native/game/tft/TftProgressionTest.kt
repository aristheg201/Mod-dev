package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.*
import kotlin.test.*

class TftProgressionTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private val rules = base.progression!!
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private fun create(set: TftSetDefinition = base) = TftSession(seats, 724L, definition = set)
    private fun restore(state: JsonObject) = NativeGameRestorer.restore("tft", seats, "test", state)
    private fun fields(s: NativeGameSession) = s.viewFor("a").fields

    /** Empty boards finish naturally on the first combat step in PvP and PvE. */
    private fun finishRound(s: NativeGameSession): Long {
        val end = fields(s).getValue("phaseEndsAt").toLong()
        s.tick(end + 1)
        assertEquals("combat", s.viewFor("a").phase)
        s.tick(end + 51)
        assertEquals("post", s.viewFor("a").phase)
        return end + 51
    }

    @Test fun noXpBeforeFirstCompletedRoundOrFromReopening() {
        val s = create()
        repeat(10) { assertEquals("0", fields(s)["xp"]) }
        assertEquals("2", fields(s)["level"])
        assertEquals("-1", fields(s)["settledRound"])
    }

    @Test fun passiveXpAfterPve() {
        val s = create(); finishRound(s)
        assertEquals("3", fields(s)["level"])
        assertEquals("0", fields(s)["xp"])
        assertEquals("2", fields(s)["xpGranted"])
        assertEquals("3", fields(s)["unitCap"])
    }

    @Test fun passiveXpAfterPvpExactlyOnce() {
        val s = create(base.copy(pveRounds = emptyList()))
        val now = finishRound(s)
        val expected = fields(s).filterKeys { it in setOf("xp", "level", "gold", "settledRound") }
        repeat(10) { s.tick(now + 100); s.viewFor("a") }
        assertEquals(expected, fields(s).filterKeys { it in expected })
    }

    @Test fun restartAfterSettlementDoesNotReplayGoldOrXp() {
        val s = create(); val now = finishRound(s)
        val checkpoint = s.snapshotState(now)
        repeat(3) {
            val recovered = restore(checkpoint.deepCopy())
            assertEquals(fields(s)["xp"], fields(recovered)["xp"])
            assertEquals(fields(s)["gold"], fields(recovered)["gold"])
            recovered.tick(fields(recovered).getValue("phaseEndsAt").toLong() + 1)
            assertEquals("planning", recovered.viewFor("a").phase)
            assertEquals(fields(s)["xp"], fields(recovered)["xp"])
            assertEquals(fields(s)["gold"], fields(recovered)["gold"])
        }
    }

    @Test fun restartDuringCombatSettlesOnce() {
        val s = create()
        val end = fields(s).getValue("phaseEndsAt").toLong()
        s.tick(end + 1)
        val recovered = restore(s.snapshotState(end + 1))
        recovered.tick(System.currentTimeMillis() + 100)
        assertEquals("3", fields(recovered)["level"])
        assertEquals("0", fields(recovered)["settledRound"])
    }

    @Test fun overflowWorks() {
        assertEquals(TftXpResult(5, 4, 1, 6), TftProgression.grant(rules, 4, 8, 6))
    }

    @Test fun multipleLevelsAndMaxClamp() {
        assertEquals(TftXpResult(5, 2, 3, 20), TftProgression.grant(rules, 2, 0, 20))
        assertEquals(TftXpResult(10, 0, 1, 10000), TftProgression.grant(rules, 9, 90, 10000))
        assertEquals(TftXpResult(10, 0, 0, 0), TftProgression.grant(rules, 10, 0, 100))
    }

    @Test fun passiveAndPurchasedXpInteract() {
        val s = create()
        assertTrue(s.act("a", "buy_xp", emptyMap()).accepted)
        assertEquals("3", fields(s)["level"])
        assertEquals("2", fields(s)["xp"])
        finishRound(s)
        assertEquals("4", fields(s)["xp"])
    }

    @Test fun purchaseCostAndAmountComeFromConfig() {
        val s = create(base.copy(progression = rules.copy(buyXp = TftXpPurchase(3, 8))))
        assertTrue(s.act("a", "buy_xp", emptyMap()).accepted)
        assertEquals("2", fields(s)["gold"])
        assertEquals("4", fields(s)["level"])
        assertEquals("0", fields(s)["xp"])
    }

    @Test fun levelAndBoardCapChangeBeforeNewShopOdds() {
        val odds = base.shopOdds.map { it.copy(odds = if (it.level == 2) listOf(100,0,0,0,0) else listOf(0,100,0,0,0)) }
        val s = create(base.copy(shopOdds = odds))
        assertTrue(s.viewFor("a").cards.all { it.value == 1 })
        finishRound(s)
        assertEquals("3", fields(s)["unitCap"])
        assertEquals("0,100,0,0,0", fields(s)["shopOdds"])
        s.tick(fields(s).getValue("phaseEndsAt").toLong() + 1)
        assertEquals(5, s.viewFor("a").cards.size)
        assertTrue(s.viewFor("a").cards.all { it.value == 2 })
    }

    @Test fun augmentModifiersAffectPassiveXp() {
        val augment = TftAugmentDefinition("scholar", "Scholar", effects = mapOf("passive_xp_bonus" to 2.0, "passive_xp_multiplier" to 0.5))
        val state = create(base.copy(augments = base.augments + augment)).snapshotState()
        state.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("augments").add("scholar")
        val s = restore(state); finishRound(s)
        assertEquals("6", fields(s)["xpGranted"])
        assertEquals("3", fields(s)["level"])
        assertEquals("4", fields(s)["xp"])
    }

    @Test fun configCanDisablePveGrants() {
        val s = create(base.copy(progression = rules.copy(passiveRoundTypes = setOf("pvp"))))
        finishRound(s)
        assertEquals("2", fields(s)["level"])
        assertEquals("0", fields(s)["xp"])
    }

    @Test fun completedRoundBeforeCarouselReceivesIncomeAndXp() {
        val state = create(base.copy(pveRounds = emptyList())).snapshotState()
        state.addProperty("roundIndex", 5) // 2-3; next round is shared draft 2-4.
        val s = restore(state); finishRound(s)
        val gold = fields(s)["gold"]
        assertEquals("3", fields(s)["level"])
        s.tick(fields(s).getValue("phaseEndsAt").toLong() + 1)
        assertEquals("draft", s.viewFor("a").phase)
        assertEquals(gold, fields(s)["gold"])
        s.tick(fields(s).getValue("phaseEndsAt").toLong() + 1)
        assertEquals("planning", s.viewFor("a").phase)
        assertEquals(gold, fields(s)["gold"])
        assertEquals("3", fields(s)["level"])
    }

    @Test fun migrationDoesNotGrantRetroactiveXpOrDuplicatePendingIncome() {
        val old = create().snapshotState()
        old.addProperty("schema", 1); old.addProperty("phase", "POST_COMBAT")
        old.getAsJsonObject("setDefinition").remove("progression")
        old.getAsJsonArray("players").forEach {
            val p = it.asJsonObject
            listOf("lastSettledRound", "lastXpGranted", "lastLevelsGained", "legacyIncomePending").forEach(p::remove)
        }
        val s = restore(old)
        assertEquals("0", fields(s)["xp"])
        val saved = s.snapshotState()
        val again = restore(saved)
        again.tick(fields(again).getValue("phaseEndsAt").toLong() + 1)
        assertEquals("10", fields(again)["gold"])
        assertEquals("0", fields(again)["xp"])
        assertEquals("2", fields(again)["level"])
    }
}
