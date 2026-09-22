package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import com.google.gson.JsonArray
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.native.game.TftSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TftRuntimeIntegrityTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))

    private fun restore(state: com.google.gson.JsonObject, definition: TftSetDefinition = base) =
        TftSession(seats, seed = 91L, sessionId = "integrity", definition = definition, restoreState = state)

    @Test
    fun `carousel expiry advances to the next scheduled round without phantom carousel combat`() {
        val initial = TftSession(seats, 91L, definition = base)
        val saved = initial.snapshotState()
        saved.addProperty("phase", "POST_COMBAT")
        saved.addProperty("roundIndex", base.roundSchedule.indexOfFirst { it.label == "2-3" })
        saved.addProperty("phaseRemainingMs", 0)
        saved.add("combats", JsonArray())

        val session = restore(saved)
        session.tick(System.currentTimeMillis() + 1)
        assertEquals("draft", session.viewFor("a").phase)
        assertEquals("2-4", session.viewFor("a").fields["round"])

        val draftEnd = session.viewFor("a").fields.getValue("phaseEndsAt").toLong()
        session.tick(draftEnd + 1)
        assertEquals("planning", session.viewFor("a").phase)
        assertEquals("2-5", session.viewFor("a").fields["round"])
    }

    @Test
    fun `scheduled augment round offers three unique choices from its authored tier`() {
        val initial = TftSession(seats, 96L, definition = base)
        val saved = initial.snapshotState()
        saved.addProperty("phase", "POST_COMBAT")
        saved.addProperty("roundIndex", base.roundSchedule.indexOfFirst { it.label == "1-3" })
        saved.addProperty("phaseRemainingMs", 0)
        saved.add("combats", JsonArray())

        val session = restore(saved)
        session.tick(System.currentTimeMillis() + 1)
        assertEquals("2-1", session.viewFor("a").fields["round"])
        val choices = session.viewFor("a").fields.getValue("augmentChoices").split(';').filter(String::isNotBlank)
        assertEquals(3, choices.size)
        assertEquals(3, choices.map { it.substringBefore('~') }.toSet().size)
        assertTrue(choices.all { raw ->
            val parts = raw.split('~')
            base.augments.single { it.id == parts[0] }.tier == "Silver" && parts.getOrNull(5) == "Silver"
        })
    }

    @Test
    fun `augment recovery deduplicates owned choices and never reoffers an owned augment`() {
        val saved = TftSession(seats, 92L, definition = base).snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        val owned = base.augments.first().id
        val offered = base.augments.first { it.id != owned }.id
        player.getAsJsonArray("augments").apply { add(owned); add(owned) }
        player.getAsJsonArray("augmentChoices").apply { add(owned); add(owned); add(offered); add(offered) }

        val restored = restore(saved)
        assertEquals(listOf(owned), restored.viewFor("a").fields.getValue("augments").split(',').filter(String::isNotBlank))
        val choices = restored.viewFor("a").fields.getValue("augmentChoices").split(';').filter(String::isNotBlank)
            .map { it.substringBefore('~') }
        assertEquals(listOf(offered), choices)
        assertTrue(restored.act("a", "choose_augment", mapOf("id" to offered)).accepted)
        assertEquals(setOf(owned, offered), restored.viewFor("a").fields.getValue("augments").split(',').filter(String::isNotBlank).toSet())
    }

    @Test
    fun `selling a unit preserves authored completed item identity`() {
        val initial = TftSession(seats, 93L, definition = base)
        val card = initial.viewFor("a").cards.first { it.value <= initial.viewFor("a").fields.getValue("gold").toInt() }
        assertTrue(initial.buyOffer("a", card.id.substringAfter(':')).accepted)
        val saved = initial.snapshotState()
        val bench = saved.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("bench")
        val index = (0 until bench.size()).first { !bench[it].isJsonNull }
        val unit = bench[index].asJsonObject
        unit.add("items", JsonArray().apply { add("full:deathblade") })
        val instance = unit.get("instanceId").asString

        val restored = restore(saved)
        assertTrue(restored.act("a", "sell", mapOf("origin" to "bench", "index" to index.toString(), "instanceId" to instance)).accepted)
        assertEquals("full:deathblade", restored.viewFor("a").fields["itemBench"])
    }

    @Test
    fun `star up combines compatible components and preserves completed items atomically`() {
        val initial = TftSession(seats, 94L, definition = base)
        val view = initial.viewFor("a")
        val card = view.cards.first { it.value <= view.fields.getValue("gold").toInt() }
        val unitId = card.meta.getValue("unit")
        val saved = initial.snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        val bench = player.getAsJsonArray("bench")
        bench.set(0, Gson().toJsonTree(TftOwnedUnit("copy-a", unitId, items = mutableListOf("bf_sword"))))
        bench.set(1, Gson().toJsonTree(TftOwnedUnit("copy-b", unitId, items = mutableListOf("recurve_bow", "full:deathblade"))))

        val restored = restore(saved)
        assertTrue(restored.buyOffer("a", card.id.substringAfter(':')).accepted)
        val upgraded = restored.viewFor("a").fields.getValue("bench").split(';').single { row ->
            val parts = row.split('~')
            parts.getOrNull(2) == unitId && parts.getOrNull(4) == "2"
        }
        val items = upgraded.split('~').getOrNull(6).orEmpty().split(',').filter(String::isNotBlank).toSet()
        assertEquals(setOf("full:giant_slayer", "full:deathblade"), items)
        assertEquals("", restored.viewFor("a").fields["itemBench"])
    }

    @Test
    fun `modified XP purchase values are authoritative in actions and view fields`() {
        val augment = base.augments.first { TftPlayerModifier.XP_PURCHASE_AMOUNT in it.playerModifiers }
        val saved = TftSession(seats, 95L, definition = base).snapshotState()
        saved.getAsJsonArray("players")[0].asJsonObject.getAsJsonArray("augments").add(augment.id)
        val restored = restore(saved)
        assertEquals(
            (base.progression!!.buyXp.xpGranted + augment.playerModifiers.getValue(TftPlayerModifier.XP_PURCHASE_AMOUNT).toInt()).toString(),
            restored.viewFor("a").fields["buyXpAmount"]
        )
    }

    @Test
    fun `validator rejects authored player modifiers without runtime consumers`() {
        val bad = base.augments.first().copy(
            id = "unsupported_modifier_test",
            playerModifiers = mapOf(TftPlayerModifier.PLAYER_RESOURCE_FLAT to 1.0)
        )
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(base.copy(augments = base.augments + bad))
        }
    }
}
