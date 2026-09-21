package io.github.aristheg201.svhub.native

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeArcadeLifecyclePolicyTest {
    @Test
    fun `async TFT match promotes queued lobby into game but normal updates do not reopen it`() {
        assertEquals(
            true,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "arcade",
                message = "gui.svhub.arcade.matched",
                activeViewPresent = true
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "game",
                message = "gui.svhub.arcade.matched",
                activeViewPresent = true
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = null,
                message = "planning updated",
                activeViewPresent = true
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "arcade",
                message = "gui.svhub.arcade.matched",
                activeViewPresent = false
            )
        )
    }

    @Test
    fun `live active session resumes instead of blocking a new arcade click`() {
        assertEquals(
            ActiveSessionResolution.RESUME,
            NativeArcadeLifecyclePolicy.resolve("chess-session", sessionPresent = true, viewPresent = true)
        )
    }

    @Test
    fun `orphaned active mapping is stale and must be removed`() {
        assertEquals(
            ActiveSessionResolution.STALE,
            NativeArcadeLifecyclePolicy.resolve("dead-session", sessionPresent = false, viewPresent = false)
        )
    }

    @Test
    fun `no active mapping starts normally`() {
        assertEquals(
            ActiveSessionResolution.NONE,
            NativeArcadeLifecyclePolicy.resolve(null, sessionPresent = false, viewPresent = false)
        )
    }
}
