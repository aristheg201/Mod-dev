package io.github.aristheg201.svarcade.native

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeArcadeLifecyclePolicyTest {
    @Test
    fun `async TFT match promotes queued lobby into game but normal updates do not reopen it`() {
        assertEquals(
            true,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "arcade",
                message = "gui.svarcade.arcade.matched",
                activeViewPresent = true
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "game",
                message = "gui.svarcade.arcade.matched",
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
            true,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "arcade",
                message = "",
                activeViewPresent = true
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAutoOpenMatchedGame(
                currentModule = "arcade",
                message = "gui.svarcade.arcade.matched",
                activeViewPresent = false
            )
        )
    }


    @Test
    fun `active game promotion survives stale lobby view lineage`() {
        assertEquals(
            true,
            NativeArcadeLifecyclePolicy.shouldAcceptServerOpen(
                targetModule = "game",
                targetHasActiveView = true,
                currentViewId = "client-lobby-newer",
                incomingViewId = "server-game",
                replacesViewId = "client-lobby-older",
                explicitlyClosed = false
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAcceptServerOpen(
                targetModule = "arcade",
                targetHasActiveView = false,
                currentViewId = "client-newer",
                incomingViewId = "server-stale",
                replacesViewId = "client-older",
                explicitlyClosed = false
            )
        )
        assertEquals(
            false,
            NativeArcadeLifecyclePolicy.shouldAcceptServerOpen(
                targetModule = "game",
                targetHasActiveView = true,
                currentViewId = "client-lobby",
                incomingViewId = "server-game",
                replacesViewId = "client-lobby",
                explicitlyClosed = true
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
