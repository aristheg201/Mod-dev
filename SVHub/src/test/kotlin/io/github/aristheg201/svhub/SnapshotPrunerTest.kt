package io.github.aristheg201.svhub

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.server.SnapshotPruner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotPrunerTest {
    @Test
    fun `hidden page assets and themes are not serialized into player snapshot`() {
        val publicAsset = HubAsset("svhub:public", "image", "resource", "svhub:textures/public.png")
        val secretAsset = HubAsset("svhub:secret", "image", "resource", "svhub:textures/secret.png")
        val publicTheme = HubTheme("public", backgroundAsset = "svhub:public")
        val secretTheme = HubTheme("secret", backgroundAsset = "svhub:secret")
        val visible = HubPage(
            "visible", "visible", "guide", LocalizedText.of("Visible"), theme = "public",
            components = listOf(HubComponent("img", "image", JsonObject().apply { addProperty("asset", "svhub:public") }))
        )
        val hidden = HubPage(
            "hidden", "hidden", "admin", LocalizedText.of("Hidden"), theme = "secret",
            components = listOf(HubComponent("secret-img", "image", JsonObject().apply { addProperty("asset", "svhub:secret") }))
        )
        val content = HubContent(
            defaultTheme = "public",
            themes = mapOf("public" to publicTheme, "secret" to secretTheme),
            assets = mapOf(publicAsset.id to publicAsset, secretAsset.id to secretAsset),
            pages = listOf(visible, hidden)
        )

        val projected = SnapshotPruner.prune(content, listOf(visible))

        assertEquals(listOf("visible"), projected.pages.map { it.id })
        assertTrue("public" in projected.themes)
        assertFalse("secret" in projected.themes)
        assertTrue("svhub:public" in projected.assets)
        assertFalse("svhub:secret" in projected.assets)
    }

    @Test
    fun `generated asset dependency closure is retained`() {
        val child = HubAsset("svhub:child", "image", "resource", "svhub:textures/child.png")
        val parent = HubAsset(
            "svhub:parent", "generated", "generated", generator = JsonObject().apply { addProperty("source", "svhub:child") }
        )
        val page = HubPage(
            "page", "page", "guide", LocalizedText.of("Page"),
            components = listOf(HubComponent("img", "image", JsonObject().apply { addProperty("asset", "svhub:parent") }))
        )
        val theme = HubTheme("pixel_classic")
        val content = HubContent(
            defaultTheme = theme.id,
            themes = mapOf(theme.id to theme),
            assets = mapOf(parent.id to parent, child.id to child),
            pages = listOf(page)
        )

        val projected = SnapshotPruner.prune(content, listOf(page))
        assertEquals(setOf("svhub:parent", "svhub:child"), projected.assets.keys)
    }
}
