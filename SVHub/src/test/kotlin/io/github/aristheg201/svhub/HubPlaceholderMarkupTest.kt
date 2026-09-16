package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.FieldGuideContent
import io.github.aristheg201.svhub.server.HubPlaceholderResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubPlaceholderMarkupTest {
    @Test
    fun `safe formatting survives placeholder materialization and command args stay literal`() {
        val source = "<bold>Hello</bold> %player:name% /tpa <tên>"
        val transformed = HubPlaceholderResolver.transformMarkup(source) { plain ->
            plain.replace("%player:name%", "Aris")
        }
        assertEquals("<bold>Hello</bold> Aris /tpa \\<tên>", transformed)
    }

    @Test
    fun `interactive MiniMessage tags are escaped instead of becoming actions`() {
        val source = "<click:run_command:'/op'>danger</click>"
        val transformed = HubPlaceholderResolver.transformMarkup(source) { it }
        assertTrue(transformed.contains("\\<click:"))
        assertTrue(transformed.contains("\\</click>"))
        assertFalse(HubPlaceholderResolver.isAllowedMiniMessageTag("click:run_command:'/op'"))
        assertTrue(HubPlaceholderResolver.isAllowedMiniMessageTag("color:#2E7168"))
        assertTrue(HubPlaceholderResolver.isAllowedMiniMessageTag("gradient:#2E7168:#C58A35"))
    }

    @Test
    fun `field guide advertises dynamic placeholders`() {
        assertTrue(HubPlaceholderResolver.hasDynamicPlaceholders(FieldGuideContent.create()))
    }
}
