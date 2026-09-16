package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScopedEditMergeTest {
    private fun page(id: String, title: String = id) = HubPage(id, id, "test", LocalizedText.of(title))

    private val permissions = ScopedEditPermissions(
        editSettings = true,
        editAssets = true,
        createPages = true,
        editPage = { true }
    )

    @Test
    fun `omitted hidden pages survive scoped publish`() {
        val visible = page("visible", "Before")
        val hidden = page("hidden", "Secret")
        val current = DefaultContent.create().copy(pages = listOf(visible, hidden), revision = 9)
        val baseline = current.copy(pages = listOf(visible))
        val candidate = baseline.copy(pages = listOf(visible.copy(title = LocalizedText.of("After"))))

        val result = ScopedEditMerge.merge(current, baseline, candidate, permissions)

        assertTrue(result.ok)
        val merged = assertNotNull(result.content)
        assertEquals(listOf("visible", "hidden"), merged.pages.map { it.id })
        assertEquals("After", merged.pages.first().title.resolve("vi_vn"))
        assertEquals("Secret", merged.pages.last().title.resolve("vi_vn"))
        assertEquals(9, merged.revision)
    }

    @Test
    fun `missing baseline page is treated as intentional scoped deletion only`() {
        val a = page("a")
        val hidden = page("hidden")
        val b = page("b")
        val current = DefaultContent.create().copy(pages = listOf(a, hidden, b))
        val baseline = current.copy(pages = listOf(a, b))
        val candidate = baseline.copy(pages = listOf(b))

        val result = ScopedEditMerge.merge(current, baseline, candidate, permissions)

        assertTrue(result.ok)
        assertEquals(listOf("b", "hidden"), result.content!!.pages.map { it.id })
    }

    @Test
    fun `unauthorized page edit is rejected`() {
        val visible = page("visible")
        val current = DefaultContent.create().copy(pages = listOf(visible))
        val baseline = current
        val candidate = baseline.copy(pages = listOf(visible.copy(category = "changed")))
        val denied = permissions.copy(editPage = { false })

        val result = ScopedEditMerge.merge(current, baseline, candidate, denied)

        assertFalse(result.ok)
        assertTrue(result.error!!.contains("không có quyền sửa page"))
    }

    @Test
    fun `new scoped id cannot collide with hidden server page`() {
        val hidden = page("secret")
        val current = DefaultContent.create().copy(pages = listOf(hidden))
        val baseline = current.copy(pages = emptyList())
        val candidate = baseline.copy(pages = listOf(page("secret", "Injected")))

        val result = ScopedEditMerge.merge(current, baseline, candidate, permissions)

        assertFalse(result.ok)
        assertTrue(result.error!!.contains("trùng với page ẩn"))
    }

    @Test
    fun `omitted hidden assets are preserved while visible asset delta applies`() {
        val visible = HubAsset("svhub:visible", "image", "resource", "svhub:old.png")
        val hidden = HubAsset("svhub:hidden", "image", "resource", "svhub:hidden.png")
        val current = DefaultContent.create().copy(assets = mapOf(visible.id to visible, hidden.id to hidden))
        val baseline = current.copy(assets = mapOf(visible.id to visible))
        val edited = visible.copy(resource = "svhub:new.png")
        val candidate = baseline.copy(assets = mapOf(edited.id to edited))

        val result = ScopedEditMerge.merge(current, baseline, candidate, permissions)

        assertTrue(result.ok)
        assertEquals("svhub:new.png", result.content!!.assets[visible.id]!!.resource)
        assertEquals("svhub:hidden.png", result.content!!.assets[hidden.id]!!.resource)
    }
}
