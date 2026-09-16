package io.github.aristheg201.svhub

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.content.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledContentMigrationTest {
    @Test
    fun `new default is a useful player handbook without mod inventory page`() {
        val content = DefaultContent.create()
        HubValidator.validate(content).requireValid()

        assertNull(content.page("mods"))
        assertNotNull(content.page("commands"))
        assertNotNull(content.page("shop/pokemon"))
        assertNotNull(content.page("guide/how-to-play"))
        assertTrue(content.page("commands")!!.components.any { component ->
            component.props.entrySet().any { (_, value) -> value.isJsonPrimitive && value.asString.contains("/gts") }
        })
        assertTrue(content.page("shop/pokemon")!!.components.any { it.id == "shop_key_prices" })
        assertFalse(content.pages.any { page -> page.components.any { it.props.toString().contains("environment:mods") } })
    }

    @Test
    fun `legacy bundled showcase is migrated and custom wiki settings survive`() {
        val explicit = FakemonEntry("custom_one", "cobblemon:comice")
        val old = legacySeed().copy(
            cobblemonWiki = CobblemonWikiConfig(
                autoPokemon = true,
                autoFakemonNamespaces = true,
                fakemonNamespaces = setOf("legacy_namespace"),
                explicitFakemon = listOf(explicit),
                hiddenSpecies = setOf("cobblemon:missingno")
            )
        )

        val migrated = BundledContentMigration.migrate(old)
        assertNotNull(migrated)
        assertEquals(1L, migrated.revision)
        assertNull(migrated.page("mods"))
        assertNotNull(migrated.page("shop/pokemon"))
        assertEquals(listOf(explicit), migrated.cobblemonWiki.explicitFakemon)
        assertTrue("legacy_namespace" in migrated.cobblemonWiki.fakemonNamespaces)
        assertTrue("cobblemon:missingno" in migrated.cobblemonWiki.hiddenSpecies)
        HubValidator.validate(migrated).requireValid()
    }

    @Test
    fun `admin content is not replaced by bundled migration`() {
        val custom = DefaultContent.create().copy(
            revision = 3,
            pages = listOf(HubPage("home", "home", "custom", LocalizedText.of("My Hub")))
        )
        assertNull(BundledContentMigration.migrate(custom))
    }

    private fun legacySeed(): HubContent {
        val legacyThemes = listOf("pixel_classic", "pixel_wiki", "pixel_commands", "pixel_updates")
            .associateWith { HubTheme(it) }
        val home = HubPage(
            "home", "home", "home", LocalizedText.of("Home"),
            components = listOf(
                HubComponent(
                    "mods_btn",
                    "button",
                    JsonObject().apply { addProperty("label", "Mods") },
                    action = HubActionSpec("mods_open", "open_page", "mods")
                )
            )
        )
        val mods = HubPage(
            "mods", "mods", "system", LocalizedText.of("Mods"),
            components = listOf(
                HubComponent(
                    "mods_grid",
                    "grid",
                    JsonObject().apply { addProperty("provider", "environment:mods") }
                )
            )
        )
        val others = listOf("how_to_play", "pokemon", "fakemon", "essential_commands", "commands", "updates")
            .map { HubPage(it, it, "legacy", LocalizedText.of(it)) }
        return HubContent(
            revision = 0,
            defaultTheme = "pixel_classic",
            themes = legacyThemes,
            pages = listOf(home) + others + mods
        )
    }
}
