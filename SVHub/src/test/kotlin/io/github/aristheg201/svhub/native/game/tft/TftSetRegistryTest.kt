package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.Gson
import io.github.aristheg201.svhub.native.game.NativeGameRestorer
import io.github.aristheg201.svhub.native.game.NativeSeat
import io.github.aristheg201.svhub.native.game.TftSession
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class TftSetRegistryTest {
    @Test fun bundledSetLoadsAllOriginalContent() {
        val set = TftSetRegistry.bundled("kanto_rising")
        assertEquals(43, set.units.size)
        assertEquals(listOf("svhub:kanto_vanguard"), set.teams.map { it.id })
        assertEquals(23, set.traits.size)
        assertEquals(8, set.components.size)
        assertEquals(36, set.fullItems.size)
        assertEquals(9, set.augments.size)
        assertEquals(7, set.pveRounds.size)
        assertEquals((2..10).toSet(), set.shopOdds.map { it.level }.toSet())
        assertTrue(set.units.any { it.id == "ho_oh" })
        val actual = set.fullItems.map { it.components.sorted() }.toSet()
        val expected = set.components.flatMap { a -> set.components.map { b -> listOf(a.id, b.id).sorted() } }.toSet()
        assertEquals(expected, actual, "Every unordered component pair must have a recipe")
    }

    @Test fun finalJarGateLoadsClassesAndResourcesFromTheSameJar() {
        if (System.getProperty("svhub.test.packaged") != "true") return
        val origin = TftSetRegistry::class.java.protectionDomain.codeSource.location
        assertTrue(origin.path.substringAfterLast('/').startsWith("SVHub-fabric-"), origin.toString())
        assertTrue(origin.path.endsWith(".jar"), origin.toString())
        for (name in listOf("set", "units", "teams", "traits", "components", "full_items", "augments", "pve")) {
            val url = assertNotNull(TftSetRegistry::class.java.getResource("/data/svhub/tft/sets/kanto_rising/$name.json"))
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
        assertTrue(session.act("p1", "buy", mapOf("index" to "0")).accepted)
        assertTrue(session.act("p1", "deploy", mapOf("bench" to "0", "slot" to "3")).accepted)
        assertEquals("1", session.viewFor("p1").fields["boardCount"])
    }

    @Test fun tftSnapshotRestoresPlanningAndLiveCombat() {
        val set = TftSetRegistry.bundled("kanto_rising")
        val seats = (1..4).map { NativeSeat("p" + it, "Trainer " + it) }
        val session = TftSession(seats, seed = 162L, definition = set)

        assertTrue(session.act("p1", "buy", mapOf("index" to "0")).accepted)
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

    @Test fun malformedOverrideFallsBackWithoutOverwritingUserFile() = inTempDirectory { root ->
        val path = root.resolve("active-set.json")
        Files.writeString(path, "[]")
        TftSetRegistry.start(root)
        assertEquals(43, TftSetRegistry.active().units.size)
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
        val team = set.teams.single()
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(teams = listOf(team.copy(members = listOf(TftTeamMemberDefinition("missing", 0))))))
        }
        assertFailsWith<IllegalArgumentException> {
            TftDefinitionValidator.validate(set.copy(teams = listOf(team.copy(members = listOf(
                TftTeamMemberDefinition("pikachu", 0), TftTeamMemberDefinition("eevee", 0)
            )))))
        }
    }

    private fun inTempDirectory(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("svhub-tft-regression-")
        try { block(root) } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
