package io.github.aristheg201.svhub

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.content.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun `default content validates`() {
        assertTrue(HubValidator.validate(DefaultContent.create()).ok)
    }

    @Test
    fun `duplicate page component action and fakemon ids are rejected`() {
        val action = HubActionSpec("same_action", "open_page", "home")
        val component = HubComponent("same_component", "button", JsonObject(), action = action)
        val pageA = HubPage("same_page", "route-a", "test", LocalizedText.of("A"), components = listOf(component, component))
        val pageB = HubPage("same_page", "route-a", "test", LocalizedText.of("B"), components = listOf(component))
        val fakemon = FakemonEntry("same_fakemon", "cobblemon:pikachu")
        val content = DefaultContent.create().copy(
            pages = listOf(pageA, pageB),
            cobblemonWiki = CobblemonWikiConfig(explicitFakemon = listOf(fakemon, fakemon))
        )
        val result = HubValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { "Duplicate page id" in it })
        assertTrue(result.errors.any { "Duplicate route" in it })
        assertTrue(result.errors.any { "Duplicate component id" in it })
        assertTrue(result.errors.any { "Duplicate action id" in it })
        assertTrue(result.errors.any { "Duplicate Fakemon id" in it })
    }

    @Test
    fun `invalid dimensions cooldown and references fail validation`() {
        val asset = HubAsset("bad_asset", "image", "resource", width = 20_000, height = 1)
        val component = HubComponent(
            "button",
            "button",
            action = HubActionSpec("bad_action", "run_command", "spawn", cooldownMs = 999_999)
        )
        val page = HubPage("home", "home", "test", LocalizedText.of("Home"), theme = "missing", components = listOf(component))
        val content = HubContent(
            themes = mapOf("pixel" to HubTheme("pixel")),
            assets = mapOf(asset.id to asset),
            pages = listOf(page)
        )
        val result = HubValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { "Asset dimensions" in it })
        assertTrue(result.errors.any { "Unknown theme" in it })
        assertTrue(result.errors.any { "Cooldown" in it })
    }

    @Test
    fun `generated background recipe validates with bounded fields`() {
        val recipe = JsonObject().apply {
            addProperty("version", 1)
            addProperty("preset", "pixel_forest")
            addProperty("seed", 42L)
            addProperty("density", 3)
        }
        val asset = HubAsset("generated_bg_home", "background", "generated", generator = recipe)
        val content = DefaultContent.create().copy(assets = DefaultContent.create().assets + (asset.id to asset))
        assertTrue(HubValidator.validate(content).ok)
    }

    @Test
    fun `invalid generated background recipe is rejected`() {
        val recipe = JsonObject().apply {
            addProperty("version", 999)
            addProperty("preset", "pixel_unbounded")
            addProperty("seed", "not-a-number")
            addProperty("density", 99)
        }
        val asset = HubAsset("bad_generated", "background", "generated", generator = recipe)
        val content = DefaultContent.create().copy(assets = DefaultContent.create().assets + (asset.id to asset))
        val result = HubValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { "preset" in it.lowercase() })
        assertTrue(result.errors.any { "density" in it.lowercase() })
        assertTrue(result.errors.any { "seed" in it.lowercase() })
        assertTrue(result.errors.any { "version" in it.lowercase() })
    }

    @Test
    fun `unknown extension component is warning not fatal`() {
        val page = HubPage(
            "home", "home", "test", LocalizedText.of("Home"),
            components = listOf(HubComponent("extension", "thirdparty_widget"))
        )
        val result = HubValidator.validate(HubContent(pages = listOf(page)))
        assertTrue(result.ok)
        assertTrue(result.issues.any { !it.fatal && "Unknown component type" in it.message })
    }
}
