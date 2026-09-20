package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonParser
import io.github.aristheg201.svhub.ui.UiRect
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompiledArenaSceneTest {
    @Test fun staticArenaSceneIsRetainedForStableDefinitionAndLayout() {
        val definition = MinecraftArenaDefinition(props = emptyList())
        val layout = PokemonSceneLayout(UiRect(0, 0, 320, 180), 7, 8, 160f, 30f, 28, 14)
        val first = MinecraftArenaRenderer.compiledScene(layout, definition)
        val second = MinecraftArenaRenderer.compiledScene(layout, definition)
        assertSame(first, second)
    }

    @Test fun gothamAndSectorCompileFromMateriallyDifferentAuthoredScenes() {
        fun load(id: String): MinecraftArenaDefinition {
            val stream = checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            return stream.bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        }
        val gotham = load("gotham_rooftops")
        val sector = load("sector_2814")
        assertNotEquals(gotham.props.map { it.item to (it.x to it.y) }, sector.props.map { it.item to (it.x to it.y) })
        assertNotEquals(gotham.cameras, sector.cameras)
        assertNotEquals(gotham.tacticianMovementBounds, sector.tacticianMovementBounds)
        assertNotEquals(gotham.benchAnchors, sector.benchAnchors)
        assertTrue(gotham.props.isNotEmpty() && sector.props.isNotEmpty())
    }
}
