package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.JsonParser
import io.github.aristheg201.svhub.native.game.*
import kotlin.test.*

// Visual verification includes the full set browser and TFT elimination result presentation.
class TftArenaStateTest {
    private val seats = listOf(NativeSeat("a", "A"), NativeSeat("b", "B"))
    private val set = TftSetRegistry.bundled("kanto_rising")
    private fun create() = TftSession(seats, seed = 912, definition = set,
        tacticianSelections = mapOf("a" to "fox", "b" to "minecraft:allay"))

    @Test fun selectedVanillaTacticianSurvivesRecoveryAndNeverEntersCombatPool() {
        val s = create()
        assertEquals("minecraft:fox", s.viewFor("a").fields["tacticianEntity"])
        val saved = s.snapshotState()
        val restored = NativeGameRestorer.restore("tft", seats, s.sessionId, saved)
        assertEquals("minecraft:fox", restored.viewFor("a").fields["tacticianEntity"])
        assertEquals(saved.get("poolCounts"), restored.snapshotState().get("poolCounts"))
        assertTrue(restored.viewFor("a").board.all(String::isBlank))
    }

    @Test fun pokemonTacticianReplicatesResolverIdentityAndRemainsPresentationOnly() {
        val custom=set.copy(
            defaultTactician="svhub:green_lantern_mewtwo",
            tacticians=set.tacticians + TftTacticianDefinition(
                id="svhub:green_lantern_mewtwo",
                pokemon=PokemonPresentationIdentity(
                    species="cobblemon:mewtwo",
                    cosmeticAspects=setOf("greenlantern")
                ),
                name="Green Lantern Mewtwo",
                scale=.7
            )
        )
        val session=TftSession(seats,seed=914,definition=custom)
        val view=session.viewFor("a")
        assertEquals("",view.fields["tacticianEntity"])
        assertEquals("cobblemon:mewtwo",view.fields["tacticianSpecies"])
        assertEquals("greenlantern",view.fields["tacticianAspects"])
        assertEquals("true",view.fields["tacticianPresentationOnly"])
        assertTrue(view.board.all(String::isBlank))
    }

    @Test fun tacticianMovementIsBoundedAuthoritativeAndRecoverySafe() {
        val session=create()
        assertEquals("0.5,0.5",session.viewFor("a").fields["tacticianPosition"])
        assertEquals("true",session.viewFor("a").fields["tacticianCanMove"])
        assertFalse(session.act("a","tactician_move",mapOf("u" to "1.1","v" to ".5")).accepted)
        assertFalse(session.act("a","tactician_move",mapOf("u" to ".9","v" to ".9")).accepted)
        assertTrue(session.act("a","tactician_move",mapOf("u" to ".65","v" to ".60")).accepted)
        assertEquals("0.65,0.6",session.viewFor("a").fields["tacticianPosition"])
        val restored=NativeGameRestorer.restore("tft",seats,session.sessionId,session.snapshotState())
        assertEquals("0.65,0.6",restored.viewFor("a").fields["tacticianPosition"])
        assertEquals("true",restored.viewFor("a").fields["tacticianCanMove"])
    }

    @Test fun authoritativeTacticianEmoteSurvivesRecoveryAndRemainsPresentationOnly() {
        val session=create();val before=session.snapshotState().get("poolCounts")
        assertTrue(session.act("a","tactician_emote",emptyMap()).accepted)
        assertEquals("emote",session.viewFor("a").fields["tacticianState"])
        val restored=NativeGameRestorer.restore("tft",seats,session.sessionId,session.snapshotState())
        assertEquals("emote",restored.viewFor("a").fields["tacticianState"])
        assertEquals("true",restored.viewFor("a").fields["tacticianPresentationOnly"])
        assertEquals(before,restored.snapshotState().get("poolCounts"))
    }

    @Test fun scoutingShowsPublicBoardBenchAndTacticianWithoutOpponentShop() {
        val s = create()
        assertTrue(s.buyOffer("b", "0").accepted)
        val ownShop = s.viewFor("a").cards
        assertTrue(s.act("a", "scout", mapOf("target" to "b")).accepted)
        val observed = s.viewFor("a")
        assertEquals(s.viewFor("b").board, observed.board)
        assertEquals(s.viewFor("b").fields["bench"], observed.fields["bench"])
        assertEquals("minecraft:allay", observed.fields["tacticianEntity"])
        assertEquals(ownShop, observed.cards)
        assertEquals("false", observed.fields["canEditBoard"])
        val publicUnitCatalog=JsonParser.parseString(observed.fields.getValue("unitCatalog")).asJsonObject
        val publicTraitCatalog=JsonParser.parseString(observed.fields.getValue("traitCatalog")).asJsonObject
        assertEquals(set.units.size, publicUnitCatalog.size())
        assertEquals(set.traits.size, publicTraitCatalog.size())
        assertTrue(s.act("a", "scout", mapOf("target" to "home")).accepted)
        assertEquals("minecraft:fox", s.viewFor("a").fields["tacticianEntity"])
    }

    @Test fun fullSetCatalogExposesNewRiskyUnitsAndTraitsToTheClient() {
        val s=create()
        val view=s.viewFor("a")
        val units=JsonParser.parseString(view.fields.getValue("unitCatalog")).asJsonObject
        val traits=JsonParser.parseString(view.fields.getValue("traitCatalog")).asJsonObject
        assertEquals(set.units.size,units.size())
        assertEquals(set.traits.size,traits.size())
        listOf("risk_deoxys_attack","regiraga","hoopa_sukuna","elite_mv_godzilla").forEach { id ->
            assertTrue(units.has(id),"Missing authored unit catalog entry $id")
        }
        listOf("glass_cannon","blood_pact","void_contract","wild_gambit","summon_spirit","hoopa_domain").forEach { id ->
            assertTrue(traits.has(id),"Missing authored trait catalog entry $id")
        }
    }

    @Test fun eliminatedPlayerGetsEndgamePresentationBeforeLobbyFinishes() {
        val fiveSeats=(0 until 5).map { index -> NativeSeat(('a'.code+index).toChar().toString(),"P${index+1}") }
        val initial=TftSession(fiveSeats,seed=915,definition=set)
        val saved=initial.snapshotState()
        val player=saved.getAsJsonArray("players")[0].asJsonObject
        player.addProperty("eliminated",true)
        player.addProperty("placement",5)
        val restored=NativeGameRestorer.restore("tft",fiveSeats,initial.sessionId,saved)
        val view=restored.viewFor(fiveSeats.first().id)
        assertFalse(view.finished)
        assertEquals("true",view.fields["eliminated"])
        val result=assertNotNull(view.resultPresentation)
        assertEquals("defeat",result.outcome)
        assertEquals("5",result.stats.first{it.key=="placement"}.value)
        assertFalse(result.canRematch)
    }

    @Test fun authoredPokemonScaleSurvivesShopBenchAndBoardPresentation() {
        val scaled=set.copy(units=set.units.map { unit -> unit.copy(pokemon=unit.presentation.copy(scale=1.75)) })
        val s=TftSession(seats,seed=913,definition=scaled)
        val shop=s.viewFor("a").cards.first()
        assertEquals("1.75",shop.meta["scale"])
        assertTrue(s.buyOffer("a", shop.id.substringAfter(':')).accepted)
        val bench=s.viewFor("a").fields.getValue("bench").split('~')
        assertEquals(1.75,bench[9].toDouble())
        assertTrue(s.act("a","deploy",mapOf("bench" to bench[0],"slot" to "0")).accepted)
        val board=s.viewFor("a").board[scaled.rules.formationCells].split('~')
        assertEquals(1.75,board[18].toDouble())
        val catalog=JsonParser.parseString(s.viewFor("a").fields.getValue("unitCatalog")).asJsonObject
        assertEquals(1.75,catalog.getAsJsonObject(shop.meta.getValue("unit")).get("scale").asDouble)
    }

    @Test fun pveLootPresentationSurvivesSchemaFourRecovery() {
        val session=create()
        val saved=session.snapshotState()
        saved.addProperty("schema",4)
        saved.getAsJsonArray("players").forEach { raw ->
            raw.asJsonObject.add("lastPveLoot",JsonParser.parseString("""["sword","loot:gold","loot:xp"]""").asJsonArray)
            raw.asJsonObject.addProperty("pveLootSerial",7L)
        }
        val restored=NativeGameRestorer.restore("tft",seats,session.sessionId,saved)
        assertEquals("sword,loot:gold,loot:xp",restored.viewFor("a").fields["pveLoot"])
        assertEquals("7",restored.viewFor("a").fields["pveLootSerial"])
        val roundTrip=restored.snapshotState()
        assertEquals(4,roundTrip.get("schema").asInt)
        assertEquals(7L,roundTrip.getAsJsonArray("players")[0].asJsonObject.get("pveLootSerial").asLong)
    }

    @Test fun pveEncounterSemanticsAreExplicitForPresentation() {
        val s=create()
        val planning=s.viewFor("a")
        assertEquals("pve",planning.fields["roundType"])
        assertEquals("false",planning.fields["pveActive"])
        assertTrue(s.tick(planning.fields.getValue("phaseEndsAt").toLong()+1))
        val combat=s.viewFor("a")
        assertEquals("combat",combat.phase)
        assertEquals("true",combat.fields["pveActive"])
        assertEquals("1-1",combat.fields["pveRound"])
        assertTrue(combat.fields.getValue("pveComponentDrops").toInt()>=0)
        assertEquals("false",combat.fields["bossRound"])
    }

    @Test fun scoutingRejectsForeignViewerAndForeignSessionTarget() {
        val s = create()
        assertFalse(s.act("stranger", "scout", mapOf("target" to "a")).accepted)
        assertFalse(s.act("a", "scout", mapOf("target" to "stranger")).accepted)
    }

    @Test fun boardToBenchSwapsWithTheRequestedSlot() {
        val s = create()
        assertTrue(s.buyOffer("a", "0").accepted)
        assertTrue(s.act("a", "deploy", mapOf("bench" to "0", "slot" to "0")).accepted)
        val first = s.viewFor("a").board[28].substringBefore('~')
        assertTrue(s.buyOffer("a", "1").accepted)
        assertTrue(s.act("a", "bench", mapOf("slot" to "0", "bench" to "0")).accepted)
        assertTrue(s.viewFor("a").fields.getValue("bench").contains(first))
        assertNotEquals(first, s.viewFor("a").board[28].substringBefore('~'))
    }

    @Test fun benchMoveKeepsExactInstanceIdentityAndRejectsInvalidSlots() {
        val s = create()
        assertTrue(s.buyOffer("a", "0").accepted)
        val before = s.viewFor("a").fields.getValue("bench").substringAfter('~')
        assertTrue(s.act("a", "swap_bench", mapOf("from" to "0", "to" to "8")).accepted)
        assertEquals(before, s.viewFor("a").fields.getValue("bench").substringAfter('~'))
        assertFalse(s.act("a", "swap_bench", mapOf("from" to "8", "to" to "9")).accepted)
    }

    @Test fun publicCatalogIsCachedAndCannotRevealPrivateShopOrderingWhileScouting() {
        val s = create()
        val ownView = s.viewFor("a")
        val own = ownView.fields
        assertTrue(s.act("a","scout",mapOf("target" to "b")).accepted)
        val opponent = s.viewFor("a")
        assertEquals(ownView.cards, opponent.cards)
        for (key in listOf("unitCatalog","traitCatalog")) {
            kotlin.test.assertSame(own.getValue(key), opponent.fields.getValue(key))
            assertEquals(own.getValue(key),s.viewFor("b").fields.getValue(key))
        }
    }
}
