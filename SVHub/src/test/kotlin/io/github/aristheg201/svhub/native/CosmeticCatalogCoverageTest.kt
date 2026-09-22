package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CosmeticCatalogCoverageTest {
    @Test
    fun `bundled cosmetic catalog covers every authored TFT arena and tactician`() {
        val set = checkNotNull(javaClass.getResourceAsStream("/data/svhub/tft/sets/kanto_rising/set.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val catalog = checkNotNull(javaClass.getResourceAsStream("/data/svhub/cosmetic_store.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonArray }

        val offers = catalog.map { entry ->
            val o = entry.asJsonObject
            o.get("kind").asString + ":" + o.get("id").asString
        }.toSet()

        val arenas = set.getAsJsonObject("rules").getAsJsonArray("arenas").map { it.asString }
        val tacticians = set.getAsJsonArray("tacticians").map { it.asJsonObject.get("id").asString }

        arenas.forEach { assertTrue("ARENA:$it" in offers, "Arena $it is missing from cosmetic_store.json") }
        tacticians.forEach { assertTrue("TACTICIAN:$it" in offers, "Tactician $it is missing from cosmetic_store.json") }
    }

    @Test
    fun `catalog migration preserves admin overrides and appends missing bundled offers`() {
        val existing = JsonParser.parseString(
            """[
              {"kind":"ARENA","id":"kanto_stadium","price":"0"},
              {"kind":"ARENA","id":"wayne_manor","price":"777","currency":"HunterCoin"}
            ]"""
        ).asJsonArray
        val bundled = JsonParser.parseString(
            """[
              {"kind":"ARENA","id":"kanto_stadium","price":"0"},
              {"kind":"ARENA","id":"wayne_manor","price":"900","currency":"HunterCoin"},
              {"kind":"ARENA","id":"infinite_void","price":"1200","currency":"HunterCoin"}
            ]"""
        ).asJsonArray

        val merged: JsonArray = NativeCosmeticService.mergeCatalog(existing, bundled)
        assertEquals(3, merged.size())
        val wayne = merged.map { it.asJsonObject }.first { it.get("id").asString == "wayne_manor" }
        assertEquals("777", wayne.get("price").asString)
        assertTrue(merged.map { it.asJsonObject.get("id").asString }.contains("infinite_void"))
    }
}
