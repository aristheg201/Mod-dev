package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.BundledVisualRefreshPatch
import io.github.aristheg201.svhub.content.DefaultContent
import io.github.aristheg201.svhub.content.FieldGuideContent
import io.github.aristheg201.svhub.content.HubPage
import io.github.aristheg201.svhub.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldGuideContentTest {
    @Test
    fun `current bundled content is light and contains MiniMessage placeholders`() {
        val content = FieldGuideContent.create()
        assertEquals(FieldGuideContent.DEFAULT_THEME, content.defaultTheme)
        assertFalse(content.themes.keys.any { it.contains("dark", ignoreCase = true) })

        val theme = assertNotNull(content.themes[FieldGuideContent.DEFAULT_THEME])
        val background = theme.palette.background
        val r = background ushr 16 and 0xFF
        val g = background ushr 8 and 0xFF
        val b = background and 0xFF
        assertTrue((r + g + b) / 3 > 190, "default background must be visibly light")
        assertTrue(theme.motionStrength == 0f)

        val home = assertNotNull(content.page("home"))
        assertTrue(home.title.resolve("vi_vn").contains("<gradient:"))
        val status = assertNotNull(home.components.firstOrNull { it.id == "home_status" })
        val text = status.props.get("text").asString
        assertTrue(text.contains("%server:online%"))
        assertTrue(text.contains("%player:ping%"))
        assertTrue(text.contains("<color:#"))
    }

    @Test
    fun `exact bundled dark handbook upgrades but admin content is preserved`() {
        val legacy = DefaultContent.create()
        val refreshed = assertNotNull(BundledVisualRefreshPatch.apply(legacy))
        assertEquals(legacy.revision + 1, refreshed.revision)
        assertEquals(FieldGuideContent.DEFAULT_THEME, refreshed.defaultTheme)

        val custom = legacy.copy(
            pages = legacy.pages + HubPage(
                id = "admin_custom",
                route = "admin/custom",
                category = "custom",
                title = LocalizedText.of("Admin custom")
            )
        )
        assertNull(BundledVisualRefreshPatch.apply(custom))
    }
}
