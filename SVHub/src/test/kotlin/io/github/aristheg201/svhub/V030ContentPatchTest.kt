package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.FieldGuideContent
import io.github.aristheg201.svhub.content.HubValidator
import io.github.aristheg201.svhub.content.V011ContentPatch
import io.github.aristheg201.svhub.content.V020ContentPatch
import io.github.aristheg201.svhub.content.V030ContentPatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class V030ContentPatchTest {
    @Test
    fun `visual cleanup replaces legacy launch copy once`() {
        val first = FieldGuideContent.create()
        val v011 = V011ContentPatch.apply(first)!!
        val v020 = V020ContentPatch.apply(v011)!!
        val v030 = V030ContentPatch.apply(v020)
        assertNotNull(v030)
        assertEquals("sv_hub", v030.defaultTheme)
        assertTrue(v030.pages.any { it.id == "svhub_v030" })
        assertFalse(v030.page("home")!!.components.any { it.id.startsWith("v020_") || it.id.startsWith("v011_") })
        HubValidator.validate(v030).requireValid()
        assertEquals(null, V030ContentPatch.apply(v030))
    }
}
