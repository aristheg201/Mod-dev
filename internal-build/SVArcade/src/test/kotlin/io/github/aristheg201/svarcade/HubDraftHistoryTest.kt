package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.content.DefaultContent
import io.github.aristheg201.svarcade.content.HubDraftHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubDraftHistoryTest {
    @Test
    fun `undo redo preserve independent deep snapshots`() {
        val initial = DefaultContent.create()
        val history = HubDraftHistory(initial, 8)

        history.mutate { content ->
            val pages = content.pages.toMutableList()
            val page = pages.first()
            val components = page.components.toMutableList()
            val component = components.first()
            val props = component.props.deepCopy().apply { addProperty("text", "Edited") }
            components[0] = component.copy(props = props)
            pages[0] = page.copy(components = components)
            content.copy(pages = pages)
        }

        assertTrue(history.canUndo())
        assertEquals("Edited", history.current().pages.first().components.first().props.get("text").asString)

        val undo = history.undo()
        assertFalse(undo.pages.first().components.first().props.get("text")?.asString == "Edited")
        assertTrue(history.canRedo())

        val redo = history.redo()
        assertEquals("Edited", redo.pages.first().components.first().props.get("text").asString)
    }

    @Test
    fun `new mutation clears redo branch`() {
        val initial = DefaultContent.create()
        val history = HubDraftHistory(initial, 4)
        history.mutate { it.copy(defaultLocale = "en_us") }
        history.undo()
        assertTrue(history.canRedo())
        history.mutate { it.copy(defaultTheme = it.defaultTheme) }
        assertTrue(history.canRedo(), "No-op mutation must not clear redo")
        history.mutate { it.copy(defaultLocale = "fr_fr") }
        assertFalse(history.canRedo())
    }

    @Test
    fun `consecutive typing with same key is one undo step`() {
        val initial = DefaultContent.create()
        val history = HubDraftHistory(initial, 8)
        val original = initial.defaultLocale

        history.mutate("field:locale") { it.copy(defaultLocale = "e") }
        history.mutate("field:locale") { it.copy(defaultLocale = "en") }
        history.mutate("field:locale") { it.copy(defaultLocale = "en_us") }

        assertEquals("en_us", history.current().defaultLocale)
        assertEquals(original, history.undo().defaultLocale)
        assertFalse(history.canUndo(), "One typing burst must create exactly one undo boundary")
    }

    @Test
    fun `breaking coalescing starts a fresh undo boundary`() {
        val initial = DefaultContent.create()
        val history = HubDraftHistory(initial, 8)

        history.mutate("field:locale") { it.copy(defaultLocale = "en") }
        history.breakCoalescing()
        history.mutate("field:locale") { it.copy(defaultLocale = "en_us") }

        assertEquals("en", history.undo().defaultLocale)
        assertEquals(initial.defaultLocale, history.undo().defaultLocale)
    }
}
