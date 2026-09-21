package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.JsonParser
import io.github.aristheg201.svhub.native.game.*
import kotlin.test.*

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
        assertTrue(s.act("b", "buy", mapOf("index" to "0")).accepted)
        val ownShop = s.viewFor("a").cards
        assertTrue(s.act("a", "scout", mapOf("target" to "b")).accepted)
        val observed = s.viewFor("a")
        assertEquals(s.viewFor("b").board, observed.board)
        assertEquals(s.viewFor("b").fields["bench"], observed.fields["bench"])
        assertEquals("minecraft:allay", observed.fields["tacticianEntity"])
        assertEquals(ownShop, observed.cards)
        assertEquals("false", observed.fields["canEditBoard"])
        assertEquals(1, JsonParser.parseString(observed.fields.getValue("unitCatalog")).asJsonObject.size())
        assertTrue(s.act("a", "scout", mapOf("target" to "home")).accepted)
        assertEquals("minecraft:fox", s.viewFor("a").fields["tacticianEntity"])
    }

    @Test fun authoredPokemonScaleSurvivesShopBenchAndBoardPresentation() {
        val scaled=set.copy(units=set.units.map { unit -> unit.copy(pokemon=unit.presentation.copy(scale=1.75)) })
        val s=TftSession(seats,seed=913,definition=scaled)
        val shop=s.viewFor("a").cards.first()
        assertEquals("1.75",shop.meta["scale"])
        assertTrue(s.act("a","buy",mapOf("index" to shop.id.substringAfter(':'))).accepted)
        val bench=s.viewFor("a").fields.getValue("bench").split('~')
        assertEquals(1.75,bench[9].toDouble())
        assertTrue(s.act("a","deploy",mapOf("bench" to bench[0],"slot" to "0")).accepted)
        val board=s.viewFor("a").board[scaled.rules.formationCells].split('~')
        assertEquals(1.75,board[18].toDouble())
        val catalog=JsonParser.parseString(s.viewFor("a").fields.getValue("unitCatalog")).asJsonObject
        assertEquals(1.75,catalog.getAsJsonObject(shop.meta.getValue("unit")).get("scale").asDouble)
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
        assertTrue(s.act("a", "buy", mapOf("index" to "0")).accepted)
        assertTrue(s.act("a", "deploy", mapOf("bench" to "0", "slot" to "0")).accepted)
        val first = s.viewFor("a").board[28].substringBefore('~')
        assertTrue(s.act("a", "buy", mapOf("index" to "1")).accepted)
        assertTrue(s.act("a", "bench", mapOf("slot" to "0", "bench" to "0")).accepted)
        assertTrue(s.viewFor("a").fields.getValue("bench").contains(first))
        assertNotEquals(first, s.viewFor("a").board[28].substringBefore('~'))
    }

    @Test fun benchMoveKeepsExactInstanceIdentityAndRejectsInvalidSlots() {
        val s = create()
        assertTrue(s.act("a", "buy", mapOf("index" to "0")).accepted)
        val before = s.viewFor("a").fields.getValue("bench").substringAfter('~')
        assertTrue(s.act("a", "swap_bench", mapOf("from" to "0", "to" to "8")).accepted)
        assertEquals(before, s.viewFor("a").fields.getValue("bench").substringAfter('~'))
        assertFalse(s.act("a", "swap_bench", mapOf("from" to "8", "to" to "9")).accepted)
    }
}
