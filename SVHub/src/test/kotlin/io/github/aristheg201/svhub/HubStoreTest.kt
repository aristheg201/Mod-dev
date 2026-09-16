package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.DefaultContent
import io.github.aristheg201.svhub.content.HubStore
import io.github.aristheg201.svhub.content.LocalizedText
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubStoreTest {
    @Test
    fun `history entries survive reload and parse revision correctly`() {
        val root = createTempDirectory("svhub-store-history")
        HubStore(root).use { store ->
            val initial = store.initializeAsync().join().content
            val changed = initial.copy(defaultLocale = "en_us")
            val result = store.commitAsync(initial.revision, changed).join()
            assertTrue(result.ok)
            val history = store.history(10)
            assertEquals(1, history.size)
            assertEquals(initial.revision, history.single().revision)
            assertTrue(history.single().fileName.startsWith("rev-${initial.revision}-"))
        }

        HubStore(root).use { reloaded ->
            reloaded.initializeAsync().join()
            val history = reloaded.history(10)
            assertEquals(1, history.size)
            assertEquals(DefaultContent.create().revision, history.single().revision)
        }
    }

    @Test
    fun `stale editor publish is rejected without overwriting latest revision`() {
        val root = createTempDirectory("svhub-store-conflict")
        HubStore(root).use { store ->
            val initial = store.initializeAsync().join().content
            val first = initial.copy(defaultLocale = "en_us")
            val second = initial.copy(defaultLocale = "vi_vn", pages = initial.pages.mapIndexed { index, page ->
                if (index == 0) page.copy(title = LocalizedText.of("Khác")) else page
            })

            val firstResult = store.commitAsync(initial.revision, first).join()
            assertTrue(firstResult.ok)

            val staleResult = store.commitAsync(initial.revision, second).join()
            assertFalse(staleResult.ok)
            assertEquals(firstResult.revision, staleResult.revision)
            assertEquals("en_us", store.snapshot().content.defaultLocale)
        }
    }

    @Test
    fun `rollback creates a new revision instead of rewinding revision counter`() {
        val root = createTempDirectory("svhub-store-rollback")
        HubStore(root).use { store ->
            val initial = store.initializeAsync().join().content
            val first = store.commitAsync(initial.revision, initial.copy(defaultLocale = "en_us")).join()
            assertTrue(first.ok)
            val second = store.commitAsync(first.revision, store.snapshot().content.copy(defaultLocale = "vi_vn")).join()
            assertTrue(second.ok)

            val rollback = store.rollbackAsync(initial.revision).join()
            assertTrue(rollback.ok)
            assertEquals(second.revision + 1, rollback.revision)
            assertEquals(initial.defaultLocale, store.snapshot().content.defaultLocale)
        }
    }
}
