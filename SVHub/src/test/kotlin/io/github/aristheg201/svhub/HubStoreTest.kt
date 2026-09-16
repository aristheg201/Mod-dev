package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.content.HubStore
import io.github.aristheg201.svhub.content.LocalizedText
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HubStoreTest {
    @Test
    fun `history entries survive reload and parse revision correctly`() {
        val root = createTempDirectory("svhub-store-history")
        var expectedHistory = emptyList<Pair<Long, String>>()

        HubStore(root).use { store ->
            val initial = store.initializeAsync().join().content
            val historyBeforeCommit = store.history(10)
            val changed = initial.copy(defaultLocale = "en_us")
            val result = store.commitAsync(initial.revision, changed).join()
            assertTrue(result.ok)

            val history = store.history(10)
            assertEquals(historyBeforeCommit.size + 1, history.size)
            assertTrue(history.any { it.revision == initial.revision })
            assertTrue(history.any { it.fileName.startsWith("rev-${initial.revision}-") })
            expectedHistory = history.map { it.revision to it.fileName }
        }

        HubStore(root).use { reloaded ->
            reloaded.initializeAsync().join()
            val history = reloaded.history(10)
            assertEquals(expectedHistory, history.map { it.revision to it.fileName })
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

    @Test
    fun `closed store rejects new work and persisted revision reopens cleanly`() {
        val root = createTempDirectory("svhub-store-close")
        val store = HubStore(root)
        val initial = store.initializeAsync().join().content
        val committed = store.commitAsync(initial.revision, initial.copy(defaultLocale = "en_us")).join()
        assertTrue(committed.ok)
        store.close()

        assertFalse(store.isReady())
        assertFailsWith<Exception> {
            store.commitAsync(committed.revision, store.snapshot().content.copy(defaultLocale = "vi_vn")).join()
        }

        HubStore(root).use { reopened ->
            val loaded = reopened.initializeAsync().join().content
            assertEquals(committed.revision, loaded.revision)
            assertEquals("en_us", loaded.defaultLocale)
        }
    }
}
