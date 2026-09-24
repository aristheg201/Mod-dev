package io.github.aristheg201.svarcade.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V011ContentPatchTest {
    @Test
    fun `installs bundled modules once`() {
        val base = DefaultContent.create()
        val patched = assertNotNull(V011ContentPatch.apply(base))
        assertNotNull(patched.page("gacha"))
        assertNotNull(patched.page("skins/showcase"))
        assertNotNull(patched.page("companions"))
        assertTrue("sv_arcade" in patched.themes)
        assertNull(V011ContentPatch.apply(patched))
    }

    @Test
    fun `does not overwrite arbitrary admin content`() {
        val custom = HubContent(
            revision = 42,
            defaultTheme = "custom",
            themes = mapOf("custom" to HubTheme("custom")),
            pages = listOf(HubPage("home", "home", "custom", LocalizedText.of("Custom")))
        )
        assertNull(V011ContentPatch.apply(custom))
        assertEquals(42, custom.revision)
    }
}
