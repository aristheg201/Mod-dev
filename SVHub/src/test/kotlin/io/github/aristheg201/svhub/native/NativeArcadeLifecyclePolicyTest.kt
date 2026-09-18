package io.github.aristheg201.svhub.native

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeArcadeLifecyclePolicyTest {
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
