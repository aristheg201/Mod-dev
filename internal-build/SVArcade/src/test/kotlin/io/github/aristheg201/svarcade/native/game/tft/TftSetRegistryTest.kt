package io.github.aristheg201.svarcade.native.game.tft

import com.google.gson.Gson
import io.github.aristheg201.svarcade.native.game.NativeGameRestorer
import io.github.aristheg201.svarcade.native.game.NativeSeat
import io.github.aristheg201.svarcade.native.game.TftSession
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class TftSetRegistryTest {
    @Test fun bundledEquipmentUsesPokemonProviderAssetsAndKeepsRecipeIdentities() {
        val set=TftSetRegistry.bundled("kanto_rising")
        val stacks=set.components.map { it.stack }+set.fullItems.map { it.stack }
        assertTrue(stacks.all { it.startsWith("cobblemon:") || it.startsWith("mega_showdown:") })
        assertTrue(stacks.any { it.startsWith("mega_showdown:") })
        assertEquals("cobblemon:muscle_band",set.components.single { it.id=="bf_sword" }.stack)
        assertEquals("cobblemon:choice_band",set.fullItems.single { it.id=="deathblade" }.stack)
    }
    @Test fun bundledSetLoadsAllOriginalContent() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertEquals(116, set.units.size)
        assertEquals(13, set.teams.size)
        assertEquals(41, set.traits.size)
        assertEquals(8, set.components.size)
        assertEquals(36, set.fullItems.size)
        assertEquals(32, set.augments.size)
        assertEquals(9, set.pveRounds.size)
        assertEquals((2..10).toSet(), set.shopOdds.map { it.level }.toSet())
        assertTrue(set.units.any { it.id == "ho_oh" })
        val actual = set.fullItems.map { it.components.sorted() }.toSet()
        val expected = set.components.flatMap { a -> set.components.map { b -> listOf(a.id, b.id).sorted() } }.toSet()
        assertEquals(expected, actual, "Every unordered component pair must have a recipe")
    }

    @Test fun expandedPokemonTraitsAndTieredAugmentsAreFullyConnected() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val pokemonTypes = setOf(
            "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"
        )
        val traitIds = set.traits.map { it.id }.toSet()
        assertTrue(pokemonTypes.all(traitIds::contains))
        assertTrue(setOf("support", "bruiser").all(traitIds::contains))
        assertEquals(emptySet(), traitIds - set.units.flatMap { it.traits }.toSet())

        val roleTrait = mapOf(
            "guardian" to "guardian", "caster" to "caster", "striker" to "striker",
            "ranger" to "ranger", "support" to "support", "fighter" to "bruiser"
        )
        assertTrue(set.units.all { unit -> roleTrait[unit.role]?.let(unit.traits::contains) ?: true })
        val unitIds = set.units.map { it.id }.toSet()
        assertTrue(setOf("weedle", "diglett", "swinub", "beedrill", "sneasel", "froslass", "toxtricity", "mamoswine", "nidoking", "articuno").all(unitIds::contains))

        assertEquals(mapOf("Silver" to 10, "Gold" to 14, "Prismatic" to 8), set.augments.groupingBy { it.tier }.eachCount())
        assertTrue(set.augments.all { it.tags.orEmpty().isNotEmpty() })
        assertTrue(set.augments.flatMap { it.traitEffects.orEmpty().keys }.all(traitIds::contains))
        assertEquals("Silver", set.roundSchedule.single { it.label == "2-1" }.augmentTier)
        assertEquals("Gold", set.roundSchedule.single { it.label == "3-2" }.augmentTier)
        assertEquals("Prismatic", set.roundSchedule.single { it.label == "4-2" }.augmentTier)
    }

    @Test fun finalJarGateLoadsClassesAndResourcesFromTheSameJar() {
        if (System.getProperty("svarcade.test.packaged") != "true") return
        val origin = TftSetRegistry::class.java.protectionDomain.codeSource.location
        assertTrue(origin.path.substringAfterLast('/').startsWith("SVArcade-fabric-"), origin.toString())
        assertTrue(origin.path.endsWith(".jar"), origin.toString())
        for (name in listOf("set", "units", "teams", "traits", "components", "full_items", "augments", "pve", "bosses")) {
            val url = assertNotNull(TftSetRegistry::class.java.getResource("/data/svarcade/tft/sets/kanto_rising/$name.json"))
            assertEquals("jar", url.protocol)
            assertEquals(origin, (url.openConnection() as JarURLConnection).jarFileURL)
        }
    }

    @Test fun rejectsTheReportedArrayAsManifestInsteadOfGuessing() {
        val error = assertFailsWith<IllegalArgumentException> {
            TftSetRegistry.decodeSet("[]".reader(), "set.json")
        }
        assertTrue(error.message.orEmpty().contains("set.json: expected a TFT set object"))
    }

    @Test fun rejectsEmptyManifest() {
        assertFailsWith<IllegalArgumentException> { TftSetRegistry.decodeSet("{}".reader(), "set.json") }
    }

    @Test fun rejectsExplicitNullBeforeGsonCanBreakKotlinFields() {
        val json = Gson().toJson(TftSetRegistry.bundled("kanto_rising")).replace("\"units\":[", "\"unused\":[")
            .dropLast(1) + ",\"units\":null}"
        val error = assertFailsWith<IllegalArgumentException> { TftSetRegistry.decodeSet(json.reader(), "override.json") }
        assertTrue(error.message.orEmpty().contains("$.units"))
    }

    @Test fun freshInstallStartsAndCreatesEightSeatSession() = inTempDirectory { root ->
        TftSetRegistry.start(root)
        val session = TftSession((1..8).map { NativeSeat("p$it", "Trainer $it") }, seed = 162L)
        val view = session.viewFor("p1")
        assertEquals("planning", view.phase)
        assertEquals(56, view.board.size)
        assertEquals(5, view.cards.size)
        assertTrue(session.buyOffer("p1", "0").accepted)
        assertTrue(session.act("p1", "deploy", mapOf("bench" to "0", "slot" to "3")).accepted)
        assertEquals("1", session.viewFor("p1").fields["boardCount"])
    }

    @Test fun tftSnapshotRestoresPlanningAndLiveCombat() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val seats = (1..4).map { NativeSeat("p" + it, "Trainer " + it) }
        val session = TftSession(seats, seed = 162L, definition = set)

        assertTrue(session.buyOffer("p1", "0").accepted)
        assertTrue(session.act("p1", "deploy", mapOf("bench" to "0", "slot" to "3")).accepted)

        val planning = session.snapshotState()
        val restoredPlanning = NativeGameRestorer.restore("tft", seats, session.sessionId, planning)
        assertEquals("planning", restoredPlanning.viewFor("p1").phase)
        assertEquals(session.viewFor("p1").board, restoredPlanning.viewFor("p1").board)
        assertEquals(session.viewFor("p1").cards, restoredPlanning.viewFor("p1").cards)
        assertEquals(session.viewFor("p1").fields["gold"], restoredPlanning.viewFor("p1").fields["gold"])
        assertEquals(session.viewFor("p1").fields["boardCount"], restoredPlanning.viewFor("p1").fields["boardCount"])

        val start = System.currentTimeMillis()
        session.tick(start + set.planningSeconds * 1_000L + 100L)
        assertEquals("combat", session.viewFor("p1").phase)
        val combatView = session.viewFor("p1")
        val combatToken = assertNotNull(combatView.board.firstOrNull { it.isNotBlank() })
        val combatParts = combatToken.split('~')
        assertTrue(combatParts.size >= 17, "TFT combat token must expose target/cast/damage/heal metadata")
        assertNotNull(combatParts[14].toIntOrNull())
        assertNotNull(combatParts[15].toLongOrNull())
        assertNotNull(combatParts[16].toLongOrNull())

        val combat = session.snapshotState()
        val restoredCombat = NativeGameRestorer.restore("tft", seats, session.sessionId, combat)
        assertEquals("combat", restoredCombat.viewFor("p1").phase)
        assertEquals(session.viewFor("p1").board, restoredCombat.viewFor("p1").board)
        assertEquals(
            combat.getAsJsonArray("combats").toString(),
            restoredCombat.snapshotState().getAsJsonArray("combats").toString(),
            "Live TFT combat runtime must round-trip exactly"
        )
    }

    @Test fun normalCombatKeepsConfiguredShopEconomyAuthoritative() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val seats = (1..2).map { NativeSeat("p$it", "P$it") }
        fun combat(): TftSession {
            val session = TftSession(seats, seed = 71L, definition = set)
            session.tick(System.currentTimeMillis() + set.planningSeconds * 1_000L + 100L)
            assertEquals("combat", session.viewFor("p1").phase)
            return session
        }
        val buy = combat()
        val view = buy.viewFor("p1")
        val affordable = view.cards.indexOfFirst { it.value <= view.fields.getValue("gold").toInt() }
        assertTrue(affordable >= 0)
        assertTrue(buy.buyOffer("p1", affordable.toString()).accepted)
        assertTrue(combat().act("p1","refresh",emptyMap()).accepted)
        assertTrue(combat().act("p1","buy_xp",emptyMap()).accepted)
        val combatView = combat().viewFor("p1")
        assertTrue("CAN_OPEN_SHOP" in combatView.fields.getValue("capabilities"))
        assertEquals("true", combatView.cards.first().meta["enabled"])
    }

    @Test fun componentCombinationIsAtomicAndPersistsAsCompletedItem() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val seats = listOf(NativeSeat("p1", "P1"), NativeSeat("p2", "P2"))
        val initial = TftSession(seats, seed = 91L, definition = set)
        assertTrue(initial.buyOffer("p1", "0").accepted)
        val snapshot = initial.snapshotState()
        val player = snapshot.getAsJsonArray("players")[0].asJsonObject
        player.add("itemBench", com.google.gson.JsonArray().apply { add("bf_sword");add("recurve_bow") })
        val restored = TftSession(seats, seed = 91L, definition = set, restoreState = snapshot)

        assertTrue(restored.act("p1", "equip_item", mapOf("item" to "0", "origin" to "bench", "index" to "0")).accepted)
        val combined = restored.act("p1", "equip_item", mapOf("item" to "0", "origin" to "bench", "index" to "0"))
        assertTrue(combined.accepted)
        val view = restored.viewFor("p1")
        assertTrue(view.fields.getValue("bench").contains("full:giant_slayer"))
        assertTrue(view.fields.getValue("lastItemEvent").startsWith("combine:bf_sword+recurve_bow->giant_slayer:"))

        val recovered = NativeGameRestorer.restore("tft", seats, restored.sessionId, restored.snapshotState())
        assertTrue(recovered.viewFor("p1").fields.getValue("bench").contains("full:giant_slayer"))
    }

    @Test fun missingRecipeRejectsWithoutDestroyingEitherComponent() {
        val bundled = TftSetRegistry.bundled("kanto_rising")
        val set = bundled.copy(fullItems = bundled.fullItems.filterNot { it.components.toSet() == setOf("bf_sword", "recurve_bow") })
        val seats = listOf(NativeSeat("p1", "P1"), NativeSeat("p2", "P2"))
        val initial = TftSession(seats, seed = 92L, definition = set)
        assertTrue(initial.buyOffer("p1", "0").accepted)
        val snapshot = initial.snapshotState()
        snapshot.getAsJsonArray("players")[0].asJsonObject.add("itemBench", com.google.gson.JsonArray().apply { add("bf_sword");add("recurve_bow") })
        val restored = TftSession(seats, seed = 92L, definition = set, restoreState = snapshot)
        assertTrue(restored.act("p1", "equip_item", mapOf("item" to "0", "origin" to "bench", "index" to "0")).accepted)
        assertFalse(restored.act("p1", "equip_item", mapOf("item" to "0", "origin" to "bench", "index" to "0")).accepted)
        val view = restored.viewFor("p1")
        assertEquals("recurve_bow", view.fields.getValue("itemBench"))
        assertTrue(view.fields.getValue("bench").contains("bf_sword"))
    }

    @Test fun fullThreeSlotsStillAllowInPlaceComponentCombination() {
        val set=TftSetRegistry.bundled("kanto_rising");val seats=listOf(NativeSeat("p1","P1"),NativeSeat("p2","P2"))
        val initial=TftSession(seats,seed=93L,definition=set);assertTrue(initial.buyOffer("p1", "0").accepted)
        val snapshot=initial.snapshotState();val player=snapshot.getAsJsonArray("players")[0].asJsonObject
        player.getAsJsonArray("bench")[0].asJsonObject.add("items",com.google.gson.JsonArray().apply{add("full:deathblade");add("full:warmogs_armor");add("bf_sword")})
        player.add("itemBench",com.google.gson.JsonArray().apply{add("recurve_bow")})
        val restored=TftSession(seats,seed=93L,definition=set,restoreState=snapshot)
        assertTrue(restored.act("p1","equip_item",mapOf("item" to "0","origin" to "bench","index" to "0")).accepted)
        val bench=restored.viewFor("p1").fields.getValue("bench")
        assertTrue(bench.contains("full:giant_slayer"));assertFalse(bench.contains("bf_sword"))
        assertEquals("",restored.viewFor("p1").fields.getValue("itemBench"))
    }

    @Test fun malformedOverrideFallsBackWithoutOverwritingUserFile() = inTempDirectory { root ->
        val path = root.resolve("active-set.json")
        Files.writeString(path, "[]")
        TftSetRegistry.start(root)
        assertEquals(116, TftSetRegistry.active().units.size)
        assertEquals("[]", Files.readString(path))
    }

    @Test fun semanticallyInvalidOverrideAlsoFallsBack() = inTempDirectory { root ->
        val bad = TftSetRegistry.bundled("kanto_rising").copy(poolSizeByCost = emptyMap())
        Files.writeString(root.resolve("active-set.json"), Gson().toJson(bad))
        TftSetRegistry.start(root)
        assertEquals(29, TftSetRegistry.active().poolSizeByCost["1"])
    }

    @Test fun validOverrideIsUsed() = inTempDirectory { root ->
        val custom = TftSetRegistry.bundled("kanto_rising").copy(id = "custom_set", planningSeconds = 45)
        Files.writeString(root.resolve("active-set.json"), Gson().toJson(custom))
        TftSetRegistry.start(root)
        assertEquals("custom_set", TftSetRegistry.active().id)
        assertEquals(45, TftSetRegistry.active().planningSeconds)
    }

    @Test fun validatesProgressionAndPveReferences() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(set.copy(schema = 999)) }
        assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(set.copy(progression = set.progression!!.copy(xpToNextByLevel = emptyMap()))) }
        assertFailsWith<IllegalArgumentException> { TftDefinitionValidator.validate(set.copy(shopOdds = set.shopOdds.dropLast(1))) }
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(pveRounds = listOf(TftPveRoundDefinition(enemies = listOf(TftPveEnemyDefinition("missing"))))))
        }
    }

    @Test fun pokemonPresentationIdentityPreservesResolverStateAndLegacyContent() {
        val legacy = TftUnitDefinition(species = "cobblemon:mewtwo", aspects = listOf("greenlantern"))
        assertEquals("cobblemon:mewtwo", legacy.presentation.species)
        assertEquals(setOf("greenlantern"), legacy.presentation.resolverAspects())

        val identity = PokemonPresentationIdentity(
            species = "cobblemon:mewtwo",
            form = "mega-x",
            aspects = setOf("greenlantern"),
            shiny = true,
            gender = "genderless",
            cosmeticAspects = setOf("event-cape"),
            features = mapOf("marking" to "corps")
        )
        assertEquals(
            setOf("greenlantern", "mega-x", "shiny", "genderless", "event-cape", "marking=corps"),
            identity.resolverAspects()
        )
    }

    @Test fun rejectsBrokenTeamReferencesAndOverlappingPositions() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val team = set.teams.first { it.id == "svarcade:kanto_vanguard" }
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(teams = listOf(team.copy(members = listOf(TftTeamMemberDefinition("missing", 0))))))
        }
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(teams = listOf(team.copy(members = listOf(
                TftTeamMemberDefinition("pikachu", 0), TftTeamMemberDefinition("eevee", 0)
            )))))
        }
    }

    @Test fun shipsProductionTeamsWithExactFranchisePresentation() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertTrue(setOf("svarcade:monsterverse", "svarcade:dc_universe", "svarcade:green_lantern_corps", "svarcade:than_tai", "svarcade:one_piece")
            .all(set.teams.map { it.id }.toSet()::contains))
        val units = set.units.associateBy { it.id }
        assertEquals(setOf("cosmetic_item-godzilla"), units.getValue("mv_godzilla").presentation.aspects)
        assertEquals(setOf("op"), units.getValue("op_sunny").presentation.aspects)
        assertEquals("mega-x", units.getValue("gl_mewtwo_x").presentation.form)
        assertEquals("mega-y", units.getValue("gl_mewtwo_y").presentation.form)
        assertEquals(setOf("greenlantern", "mega-x"), units.getValue("gl_mewtwo_x").presentation.resolverAspects())
        assertEquals(setOf("greenlantern", "mega-y"), units.getValue("gl_mewtwo_y").presentation.resolverAspects())
    }

    @Test fun validatesNonDefaultGeometryAndDataDrivenSchedule() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val resized = set.copy(rules = set.rules.copy(shopSlots = 7, benchSlots = 12, boardColumns = 8, boardRows = 5, maxBoardCapacity = 14))
        assertEquals(40, TftDefinitionValidator.validate(resized).rules.formationCells)
        assertEquals("pve", set.roundSchedule.first().type)
        assertTrue(set.roundSchedule.any { it.type == "augment" })
        assertTrue(set.roundSchedule.any { it.type == "carousel" })
        assertTrue(set.roundSchedule.any { it.type == "boss" })
    }

    @Test fun explicitItemTargetsRejectStaleIdentityAndMalformedSlotsWithoutLoss() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val seats = listOf(NativeSeat("p1", "P1"), NativeSeat("p2", "P2"))
        val initial = TftSession(seats, seed = 94L, definition = set)
        assertTrue(initial.buyOffer("p1", "0").accepted)
        val snapshot = initial.snapshotState()
        val player = snapshot.getAsJsonArray("players")[0].asJsonObject
        player.add("itemBench", com.google.gson.JsonArray().apply { add("recurve_bow") })
        player.getAsJsonArray("bench")[0].asJsonObject.add("items", com.google.gson.JsonArray().apply { add("bf_sword") })
        val session = TftSession(seats, seed = 94L, definition = set, restoreState = snapshot)
        val base = mapOf("item" to "0", "origin" to "bench", "index" to "0", "itemSlot" to "0")
        val auditTime = System.currentTimeMillis()
        val before = session.snapshotState(auditTime).toString()
        for (extra in listOf(mapOf("instanceId" to "departed-unit"), mapOf("itemId" to "needlessly_large_rod"), mapOf("itemSlot" to "NaN"), mapOf("itemSlot" to "3"))) {
            assertFalse(session.act("p1", "equip_item", base + extra).accepted)
            assertEquals(before, session.snapshotState(auditTime).toString())
        }
        assertTrue(session.act("p1", "equip_item", base).accepted)
        assertEquals("", session.viewFor("p1").fields.getValue("itemBench"))
        assertTrue(session.viewFor("p1").fields.getValue("bench").contains("full:giant_slayer"))
    }

    private fun inTempDirectory(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("svarcade-tft-regression-")
        try { block(root) } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
