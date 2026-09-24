package io.github.aristheg201.svarcade.native.game.tft

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svarcade.native.game.*
import kotlin.test.*

class TftSettlementRegressionTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private fun restore(state: JsonObject) = NativeGameRestorer.restore("tft", seats, "regression", state)
    private fun fields(s: NativeGameSession) = s.viewFor("a").fields
    private fun finish(s: NativeGameSession) {
        var now = fields(s).getValue("phaseEndsAt").toLong() + 1
        s.tick(now)
        assertEquals("combat", s.viewFor("a").phase)
        repeat(2000) {
            now += 50
            if (s.viewFor("a").phase == "combat") s.tick(now)
        }
        assertTrue(s.viewFor("a").phase in setOf("post", "finished"))
    }

    @Test fun disabledRoundXpIsNotReenabledByAugmentModifiers() {
        for (round in base.roundSchedule.filter { !it.passiveXp && it.type == "augment" }) {
            val s = TftSession(seats, 23L, definition = base)
            val saved = s.snapshotState()
            saved.addProperty("roundIndex", base.roundSchedule.indexOf(round))
            saved.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("augments")
                .add(base.augments.first { TftPlayerModifier.XP_GAIN_FLAT in it.playerModifiers }.id)
            val recovered = restore(saved)
            finish(recovered)
            assertEquals("0", fields(recovered)["xpGranted"], round.label)
            assertEquals("2", fields(recovered)["level"], round.label)
            assertEquals(fields(recovered)["xp"], fields(restore(recovered.snapshotState()))["xp"])
        }
    }

    @Test fun disabledRoundIncomeDoesNotGrantGoldOrFlatIncome() {
        val definition = base.copy(roundSchedule = base.roundSchedule.map { it.copy(income = false) })
        val saved = TftSession(seats, 23L, definition = definition).snapshotState()
        saved.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("augments")
            .add(base.augments.first { TftPlayerModifier.INCOME_FLAT in it.playerModifiers }.id)
        val s = restore(saved)
        finish(s)
        assertEquals("5", fields(s)["gold"])
        assertEquals("0", fields(s)["lastIncome"])
    }

    @Test fun postRoundHealingCannotRescueLethalDamage() {
        val saved = TftSession(seats, 23L, definition = base).snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        player.addProperty("hp", 1)
        player.getAsJsonArray("augments").add(base.augments.first { it.playerModifiers[TftPlayerModifier.POST_ROUND_HEAL] == 2.0 }.id)
        val s = restore(saved)
        finish(s)
        assertEquals("true", fields(s)["eliminated"])
        assertEquals("5", fields(s)["gold"])
        assertEquals("0", fields(s)["xpGranted"])
    }

    private fun winningPve(reward: TftLootEntryDefinition, bonus: Int = 0): NativeGameSession {
        val champion = base.units.first().copy(stats = base.units.first().stats.copy(hp = 100000, attackDamage = 100000, range = 6))
        val definition = base.copy(units = listOf(champion) + base.units.drop(1),
            lootTables = base.lootTables.map { it.copy(entries = listOf(reward)) })
        val saved = TftSession(seats, 23L, definition = definition).snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        player.getAsJsonArray("board").add(JsonObject().apply {
            addProperty("slot", 3)
            add("unit", Gson().toJsonTree(TftOwnedUnit("champion", champion.id)))
        })
        if (bonus > 0) player.getAsJsonArray("augments").add(base.augments.first { it.playerModifiers[TftPlayerModifier.PVE_DROP_COUNT] == bonus.toDouble() }.id)
        return restore(saved).also(::finish)
    }

    @Test fun extraPveComponentsAreGrantedAlongsideConfiguredLootTable() {
        for (bonus in 1..2) {
            val s = winningPve(TftLootEntryDefinition(type = "gold", amount = 3), bonus)
            val tray = fields(s).getValue("itemBench").split(',').filter(String::isNotBlank)
            assertEquals(bonus, tray.size)
            assertTrue(tray.all { id -> base.components.any { it.id == id } })
            val recovered = restore(s.snapshotState())
            assertEquals(tray.joinToString(","), fields(recovered)["itemBench"])
        }
    }

    @Test fun lootRefreshSurvivesUntilTheNextPlanningPhase() {
        val s = winningPve(TftLootEntryDefinition(type = "free_reroll", amount = 2))
        s.tick(fields(s).getValue("phaseEndsAt").toLong() + 1)
        val gold = fields(s)["gold"]
        repeat(2) { assertTrue(s.act("a", "refresh", emptyMap()).accepted) }
        assertEquals(gold, fields(s)["gold"])
    }
}
