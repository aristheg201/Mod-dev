package io.github.aristheg201.svarcade.client.cobblemon

import kotlin.test.Test
import kotlin.test.assertEquals

class PokemonModelRendererAliasTest {
    @Test
    fun `species aliases ignore punctuation and separators`() {
        assertEquals("hooh", PokemonModelRenderer.canonicalSpeciesPath("ho_oh"))
        assertEquals("hooh", PokemonModelRenderer.canonicalSpeciesPath("Ho-Oh"))
        assertEquals("chiyu", PokemonModelRenderer.canonicalSpeciesPath("chi_yu"))
        assertEquals("chiyu", PokemonModelRenderer.canonicalSpeciesPath("Chi-Yu"))
        assertEquals("mrmime", PokemonModelRenderer.canonicalSpeciesPath("mr_mime"))
    }
}
