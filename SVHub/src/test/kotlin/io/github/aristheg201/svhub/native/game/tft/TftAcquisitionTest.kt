package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.native.game.*
import kotlin.test.*

internal fun NativeGameSession.buyOffer(player: String, index: String): NativeGameResult {
    val card = viewFor(player).cards.first { it.id == "shop:$index" }
    return act(player, "buy", mapOf("index" to index, "offerId" to card.meta.getValue("offerId")))
}

class TftAcquisitionTest {
    private val base = TftSetRegistry.bundled("kanto_rising")
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private val target = base.units.first { it.id == "gl_mewtwo_x" }
    private val filler = base.units.first { it.id != target.id }
    private val item = base.components[0].id
    private val gson = Gson()
    private fun unit(id: String, star: Int = 1, items: List<String> = emptyList(), definition: String = target.id) =
        TftOwnedUnit(id, definition, star, items.toMutableList())
    private fun state(full: Boolean = true, definition: TftSetDefinition = base): JsonObject {
        val saved = TftSession(seats, 934L, sessionId = "transactions", definition = definition).snapshotState()
        val player = saved.getAsJsonArray("players")[0].asJsonObject
        player.addProperty("gold", 100)
        player.getAsJsonArray("shop").set(0, gson.toJsonTree(target.id))
        if (full) player.add("bench", gson.toJsonTree(List(base.rules.benchSlots) { unit("filler:$it", 3, definition = filler.id) }))
        return saved
    }
    private fun player(saved: JsonObject) = saved.getAsJsonArray("players")[0].asJsonObject
    private fun bench(saved: JsonObject, index: Int, owned: TftOwnedUnit) { player(saved).getAsJsonArray("bench").set(index, gson.toJsonTree(owned)) }
    private fun board(saved: JsonObject, index: Int, owned: TftOwnedUnit) {
        player(saved).getAsJsonArray("board").add(JsonObject().apply { addProperty("slot", index); add("unit", gson.toJsonTree(owned)) })
    }
    private fun restore(saved: JsonObject): NativeGameSession = NativeGameRestorer.restore("tft", seats, "transactions", saved)
    private fun owned(s: NativeGameSession): List<TftOwnedUnit> {
        val p = player(s.snapshotState())
        return (p.getAsJsonArray("bench").filterNot { it.isJsonNull } + p.getAsJsonArray("board").map { it.asJsonObject.get("unit") })
            .map { gson.fromJson(it, TftOwnedUnit::class.java) }
    }
    private fun fields(s: NativeGameSession) = s.viewFor("a").fields
    private fun offers(s: NativeGameSession) = s.viewFor("a").cards
    private fun carousel(saved: JsonObject): NativeGameSession {
        saved.addProperty("phase", "DRAFT")
        saved.addProperty("roundIndex", base.roundSchedule.indexOfFirst { it.type == "carousel" })
        saved.addProperty("phaseRemainingMs", 10000L)
        saved.add("draftOffers", JsonArray().apply { add(JsonObject().apply {
            addProperty("index", 0); addProperty("unitId", target.id); addProperty("itemId", item)
            addProperty("x", 0.0); addProperty("y", 0.0)
        }) })
        saved.getAsJsonArray("players").forEach {
            it.asJsonObject.addProperty("draftUnlockRemainingMs", 0L)
            it.asJsonObject.addProperty("carouselX", 0.0); it.asJsonObject.addProperty("carouselY", 0.0)
        }
        return restore(saved)
    }
    private fun pick(s: NativeGameSession) = s.act("a", "carousel_pick", mapOf("index" to "0", "revision" to s.viewFor("a").revision.toString()))

    @Test fun normalPurchaseUsesBenchEvenWithFullBoard() {
        val saved = state(false)
        repeat(2) { board(saved, it, unit("board:$it", 3, definition = filler.id)) }
        val s = restore(saved)
        assertTrue(s.buyOffer("a", "0").accepted)
        assertEquals(3, owned(s).size)
        assertEquals((100 - target.price).toString(), fields(s)["gold"])
    }
    @Test fun fullBenchRejectsUnrelatedPurchaseWithoutAnyMutation() {
        val s = restore(state())
        val before = s.snapshotState(0)
        assertFalse(s.buyOffer("a", "0").accepted)
        assertEquals(before, s.snapshotState(0), "gold, offer, pool, serials and units must remain unchanged")
    }
    @Test fun purchaseDoesNotRequireFreeBenchWhenConsumedByStarUp() {
        val saved = state(); bench(saved, 0, unit("first")); bench(saved, 1, unit("second"))
        val s = restore(saved)
        assertTrue(s.buyOffer("a", "0").accepted)
        assertEquals(8, owned(s).size)
        assertEquals(2, owned(s).single { it.unitId == target.id }.star)
        assertEquals("first", owned(s).single { it.unitId == target.id }.instanceId)
    }
    @Test fun fullBoardAndBenchAllowChainedThreeStarPurchase() {
        val saved = state(); bench(saved, 0, unit("one")); bench(saved, 1, unit("two"))
        board(saved, 0, unit("veteran", 2)); board(saved, 1, unit("veteran2", 2))
        val s = restore(saved)
        assertTrue(s.buyOffer("a", "0").accepted)
        val merged = owned(s).single { it.unitId == target.id }
        assertEquals(3, merged.star); assertEquals("veteran", merged.instanceId)
        assertEquals(9, merged.reservedCopies())
        assertEquals("2", fields(s)["acquisitionSerial"])
    }
    @Test fun finalTwoStarAcquisitionCanMergeWithoutBenchSpace() {
        val bench = List(9) { unit("f:$it", 3, definition = filler.id) }
        val result = TftAcquisition.resolve(bench, mapOf(0 to unit("first", 2), 1 to unit("second", 2)), unit("third", 2))
        assertEquals(TftAcquisition.Outcome.STAR_UP, result.outcome)
        assertEquals(3, result.board.getValue(0).star)
        assertEquals(9, result.bench.filterNotNull().size)
    }
    @Test fun insufficientGoldDoesNotMutateEvenWhenMergeWouldFit() {
        val saved = state(); bench(saved, 0, unit("one")); bench(saved, 1, unit("two")); player(saved).addProperty("gold", 0)
        val s = restore(saved); val before = s.snapshotState(0)
        assertFalse(s.buyOffer("a", "0").accepted); assertEquals(before, s.snapshotState(0))
    }
    @Test fun duplicateShopPacketCannotBuyAgainEvenAfterRecovery() {
        val s = restore(state(false)); val token = offers(s).first { it.id == "shop:0" }.meta.getValue("offerId")
        val args = mapOf("index" to "0", "offerId" to token)
        assertTrue(s.act("a", "buy", args).accepted)
        val recovered = restore(s.snapshotState()); val before = recovered.snapshotState(0)
        assertFalse(recovered.act("a", "buy", args).accepted); assertEquals(before, recovered.snapshotState(0))
    }
    @Test fun oldOfferTokenCannotBuyReplacementAndMissingTokenIsRejected() {
        val s = restore(state(false)); val token = offers(s).first { it.id == "shop:0" }.meta.getValue("offerId")
        assertTrue(s.act("a", "refresh", emptyMap()).accepted)
        val before = s.snapshotState(0)
        assertFalse(s.act("a", "buy", mapOf("index" to "0", "offerId" to token)).accepted)
        assertFalse(s.act("a", "buy", mapOf("index" to "0")).accepted)
        assertEquals(before, s.snapshotState(0))
    }
    @Test fun authoredIdentityAndRecipeItemsSurviveMergeAndRecovery() {
        val saved = state(); val recipe = base.fullItems.first()
        bench(saved, 0, unit("first", items = listOf(recipe.components[0])))
        bench(saved, 1, unit("second", items = listOf(recipe.components[1])))
        val s = restore(saved); assertTrue(s.buyOffer("a", "0").accepted)
        val merged = owned(s).single { it.unitId == target.id }
        assertEquals(listOf("full:${recipe.id}"), merged.items)
        val recovered = restore(s.snapshotState())
        assertEquals(merged, owned(recovered).single { it.unitId == target.id })
        assertTrue(fields(recovered).getValue("bench").contains("greenlantern"))
        assertTrue(fields(recovered).getValue("bench").contains("mega-x"))
    }
    @Test fun mergeOverflowPreservesEveryCompletedItem() {
        val saved = state(); val items = base.fullItems.take(6).map { "full:${it.id}" }
        bench(saved, 0, unit("first", items = items.take(3))); bench(saved, 1, unit("second", items = items.drop(3)))
        val s = restore(saved); assertTrue(s.buyOffer("a", "0").accepted)
        assertEquals(items.take(3), owned(s).single { it.unitId == target.id }.items)
        assertEquals(items.drop(3).joinToString(","), fields(s)["itemBench"])
    }
    @Test fun maxStarDoesNotMergeIntoIllegalTier() {
        val saved = state(); bench(saved, 0, unit("first", 3)); bench(saved, 1, unit("second", 3))
        val s = restore(saved); val before = s.snapshotState(0)
        assertFalse(s.buyOffer("a", "0").accepted); assertEquals(before, s.snapshotState(0))
    }
    @Test fun mergeOrderingDoesNotDependOnBoardMapInsertionOrder() {
        val a = unit("a"); val b = unit("b"); val c = unit("c")
        val one = TftAcquisition.resolve(listOf(null), linkedMapOf(3 to a, 1 to b), c)
        val two = TftAcquisition.resolve(listOf(null), linkedMapOf(1 to b, 3 to a), c)
        assertEquals(one, two); assertEquals("b", one.board.getValue(1).instanceId)
    }
    @Test fun invalidDuplicateInstanceRejectsWithoutChangingInputs() {
        val first = unit("same"); val bench = listOf(first)
        assertEquals(TftAcquisition.Outcome.REJECT, TftAcquisition.resolve(bench, emptyMap(), unit("same")).outcome)
        assertEquals(1, first.star)
    }
    @Test fun carouselWithSpaceAcquiresUnitAndItem() {
        val s = carousel(state(false)); assertTrue(pick(s).accepted)
        assertEquals(listOf(item), owned(s).single().items)
        assertEquals("100", fields(s)["gold"])
    }
    @Test fun fullCarouselMergesAndKeepsHeldItem() {
        val saved = state(); bench(saved, 0, unit("first")); bench(saved, 1, unit("second"))
        val s = carousel(saved); assertTrue(pick(s).accepted)
        val merged = owned(s).single { it.unitId == target.id }
        assertEquals(2, merged.star); assertEquals(listOf(item), merged.items)
    }
    @Test fun carouselAcquisitionIsNeverLostDueSolelyToUnitCapacity() {
        val s = carousel(state()); val beforeUnits = owned(s)
        assertTrue(pick(s).accepted)
        assertEquals(beforeUnits, owned(s))
        assertEquals((100 + target.cost).toString(), fields(s)["gold"])
        assertEquals(item, fields(s)["itemBench"])
        assertTrue(fields(s).getValue("acquisitionEvent").startsWith("CAROUSEL_AUTO_SELL~"))
    }
    @Test fun fullItemTrayQueuesRewardAndRecoversItAfterEquip() {
        val saved = state(); player(saved).add("itemBench", gson.toJsonTree(List(128) { item }))
        val s = carousel(saved); assertTrue(pick(s).accepted)
        assertEquals(item, fields(s)["pendingItems"])
        val recovered = restore(s.snapshotState())
        assertEquals(item, fields(recovered)["pendingItems"])
        val next = recovered.snapshotState(); next.addProperty("phase", "PLANNING")
        val planning = restore(next)
        assertTrue(planning.act("a", "equip_item", mapOf("item" to "0", "origin" to "bench", "index" to "0")).accepted)
        assertEquals("", fields(planning)["pendingItems"])
        assertEquals(128, fields(planning).getValue("itemBench").split(',').size)
        assertEquals(listOf(item), owned(planning).first().items)
    }
    @Test fun legacyOversizedItemTrayMigratesWithoutTruncation() {
        val saved = state(false); player(saved).add("itemBench", gson.toJsonTree(List(140) { item })); player(saved).remove("pendingItems")
        val s = restore(saved)
        assertEquals(128, fields(s).getValue("itemBench").split(',').size)
        assertEquals(12, fields(s).getValue("pendingItems").split(',').size)
    }
    @Test fun duplicateCarouselClaimCannotDuplicateGoldOrItems() {
        val s = carousel(state()); val revision = s.viewFor("a").revision
        assertTrue(pick(s).accepted)
        val recovered = restore(s.snapshotState()); val before = recovered.snapshotState(0)
        assertFalse(pick(recovered).accepted)
        assertFalse(recovered.act("a", "carousel_pick", mapOf("index" to "0", "revision" to revision.toString())).accepted)
        assertEquals(before, recovered.snapshotState(0))
    }
    @Test fun automaticCarouselSettlementAlsoAutosellsWhenFull() {
        val s = carousel(state()); s.tick(fields(s).getValue("phaseEndsAt").toLong() + 1)
        assertEquals((100 + target.cost).toString(), fields(s)["gold"])
        assertEquals(item, fields(s)["itemBench"])
    }
    @Test fun shopLockPersistsAndKeepsOffersAcrossRoundSettlement() {
        val s = restore(state(false)); val before = offers(s)
        assertTrue(s.act("a", "shop_lock", mapOf("locked" to "true")).accepted)
        val recovered = restore(s.snapshotState())
        var now = fields(recovered).getValue("phaseEndsAt").toLong() + 1
        recovered.tick(now); recovered.tick(now + 50)
        recovered.tick(fields(recovered).getValue("phaseEndsAt").toLong() + 1)
        assertEquals("true", fields(recovered)["shopLocked"])
        assertEquals(before, offers(recovered))
        assertTrue(recovered.act("a", "shop_lock", mapOf("locked" to "false")).accepted)
        assertTrue(recovered.act("a", "refresh", emptyMap()).accepted)
        assertNotEquals(before.first().meta["offerId"], offers(recovered).first().meta["offerId"])
    }
}
