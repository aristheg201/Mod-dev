package io.github.aristheg201.svarcade.client.cobblemon

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakemonCatalogContractTest {
    @Test
    fun `lively mons catalog preserves all supplied custom species`() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("assets/svarcade/fakemon_catalog/lively_mons_1_11.json"))
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val species = root.getAsJsonArray("species").map { it.asString }
        assertEquals(73, species.size)
        assertTrue("cobblemon:peccareck" in species)
        assertTrue("cobblemon:porygondelta" in species)
        assertTrue(root.getAsJsonArray("forms").size() >= 12)
    }
}
