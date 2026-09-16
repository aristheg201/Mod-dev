package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.FieldGuideContent
import io.github.aristheg201.svhub.search.SearchIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIndexMiniMessageTest {
    @Test
    fun `formatting tags do not pollute normalized search text`() {
        assertEquals(
            "sv hub",
            SearchIndex.normalize("<gradient:#2E7168:#C58A35><bold>SV HUB</bold></gradient>")
        )
        assertEquals("tpa ten", SearchIndex.normalize("/tpa <tên>"))
    }

    @Test
    fun `formatted page title remains searchable by visible text`() {
        val index = SearchIndex.build(FieldGuideContent.create())
        val hits = index.search("sv hub", 10)
        assertTrue(hits.any { it.id == "home" })
    }
}
