package io.github.aristheg201.svarcade

import io.github.aristheg201.svarcade.content.BundledHandbookPatch
import io.github.aristheg201.svarcade.content.DefaultContent
import io.github.aristheg201.svarcade.content.HubValidator
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledHandbookPatchTest {
    @Test
    fun `clean bundled seed gains missing player service commands once`() {
        val patched = BundledHandbookPatch.apply(DefaultContent.create())
        assertNotNull(patched)
        HubValidator.validate(patched).requireValid()

        val commands = patched.page("commands")!!
        val quick = patched.page("commands/essential")!!
        val shop = patched.page("shop/pokemon")!!
        assertTrue(commands.components.any { it.id == "cmd_wt" && it.action?.value == "/wt" })
        assertTrue(commands.components.any { it.id == "cmd_sts" && it.action?.value == "/sts" })
        assertTrue(quick.components.any { it.id == "quick_hub" && it.action?.value == "/hub" })
        assertTrue(quick.components.any { it.id == "quick_wiki" && it.action?.value == "/wiki" })
        assertTrue(shop.components.any { it.id == "shop_wt" })
        assertTrue(shop.components.any { it.id == "shop_sts" })
    }

    @Test
    fun `patch is idempotent and ignores administrator revisions`() {
        val first = BundledHandbookPatch.apply(DefaultContent.create())!!
        assertNull(BundledHandbookPatch.apply(first))
        assertNull(BundledHandbookPatch.apply(first.copy(revision = 7)))
    }
}
