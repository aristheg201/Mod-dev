package io.github.aristheg201.svarcade.native.game.tft

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import org.junit.jupiter.api.Test
import kotlin.test.*

class TftReloadPublicationTest {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    private fun withRegistry(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("svarcade-reload-publication")
        try {
            TftSetRegistry.start(root)
            test(root)
        } finally {
            Files.deleteIfExists(root.resolve("active-set.json"))
            TftSetRegistry.start(root)
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun malformedLiveOverridePreservesPublishedIdentityAndInput() = withRegistry { root ->
        val before = TftSetRegistry.active()
        val path = root.resolve("active-set.json")
        Files.writeString(path, "{ broken")
        assertTrue(TftSetRegistry.reload().isFailure)
        assertSame(before, TftSetRegistry.active())
        assertEquals("{ broken", Files.readString(path))
    }

    @Test fun semanticallyInvalidLiveOverridePreservesPublishedIdentity() = withRegistry { root ->
        val before = TftSetRegistry.active()
        Files.writeString(root.resolve("active-set.json"), gson.toJson(before.copy(name = "")))
        assertTrue(TftSetRegistry.reload().isFailure)
        assertSame(before, TftSetRegistry.active())
    }

    @Test fun validCustomOverridePublishesAtomicallyWithoutChangingPinnedObject() = withRegistry { root ->
        val pinned = TftSetRegistry.active()
        Files.writeString(root.resolve("active-set.json"), gson.toJson(pinned.copy(id = "custom_reload_fixture", name = "Custom reload")))
        val published = TftSetRegistry.reload().getOrThrow()
        assertSame(published, TftSetRegistry.active())
        assertNotSame(pinned, published)
        assertEquals("custom_reload_fixture", published.id)
        assertNotEquals("custom_reload_fixture", pinned.id)
    }

    @Test fun staleBuiltInRosterCannotReplaceLiveSnapshot() = withRegistry { root ->
        val before = TftSetRegistry.active()
        Files.writeString(root.resolve("active-set.json"), gson.toJson(before.copy(fullItems = before.fullItems.dropLast(1))))
        assertTrue(TftSetRegistry.reload().isFailure)
        assertSame(before, TftSetRegistry.active())
    }

    @Test fun invalidStartupOverrideStillRecoversWithoutDeletingAdministratorFile() = withRegistry { root ->
        val path = root.resolve("active-set.json")
        Files.writeString(path, "{ broken")
        TftSetRegistry.start(root)
        assertTrue(TftSetRegistry.active().units.isNotEmpty())
        assertEquals("{ broken", Files.readString(path))
    }

    @Test fun asynchronousFailureDoesNotPublishOrSucceed() = withRegistry { root ->
        val before = TftSetRegistry.active()
        Files.writeString(root.resolve("active-set.json"), "{ broken")
        assertFailsWith<ExecutionException> { TftSetRegistry.reloadAsync().get() }
        assertSame(before, TftSetRegistry.active())
    }

    @Test fun removingOverrideExplicitlyRestoresBundledSet() = withRegistry { root ->
        val original = TftSetRegistry.active()
        val path = root.resolve("active-set.json")
        Files.writeString(path, gson.toJson(original.copy(id = "custom_reload_fixture")))
        TftSetRegistry.reload().getOrThrow()
        Files.delete(path)
        assertEquals(original.id, TftSetRegistry.reload().getOrThrow().id)
    }
}
