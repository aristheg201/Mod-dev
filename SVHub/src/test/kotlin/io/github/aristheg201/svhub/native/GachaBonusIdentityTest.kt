package io.github.aristheg201.svhub.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GachaBonusIdentityTest {
    @Test
    fun `bonus pokemon uuid is stable per gacha request`() {
        val request = "bdb64af5-f9ea-4ed4-9d65-f276c523c3d4"
        val first = NativeSkinService.bonusPokemonUuidForRequest(request)
        val second = NativeSkinService.bonusPokemonUuidForRequest(request)
        val other = NativeSkinService.bonusPokemonUuidForRequest("45714379-03a4-41cb-a3af-682fd83cc981")

        assertEquals(first, second)
        assertNotEquals(first, other)
    }
}
