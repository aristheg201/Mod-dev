package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonParser
import kotlin.test.*

class MinecraftArenaDefinitionTest {
    @Test
    fun parsesAndConsumesCompleteNonDefaultMetadata() {
        val json = """{"style":"terrain","metadata":{"boardColumns":9,"boardRows":6,"boardOrigin":[2,3,4],"boardAnchors":[[2,3,4]],"benchAnchors":[[1,8,0]],"itemBenchAnchors":[[2,9,0]],"tacticianSpawn":[5,7,0],"tacticianRegion":[1,2,8,9],"humanSpawn":[4,9,0],"opponentSpawn":[4,1,0],"camera":{"spectator":[1,2,3],"scouting":[4,5,6],"carousel":[7,8,9]},"carouselCenter":[4,4,0],"arenaBounds":[0,0,10,10],"lighting":"night","ambientVfx":"rain","music":"x:y","lootAnchors":[[3,3,0]],"combatStartVfx":"start","victoryVfx":"win","defeatVfx":"lose","interactionRegions":[{"id":"shop","bounds":[1,1,2,2],"action":"buy"}]}}"""
        val d = MinecraftArenaRegistry.parse(JsonParser.parseString(json).asJsonObject)
        assertEquals(9,d.boardColumns)
        assertEquals(ArenaPoint(2f,3f,4f),d.boardOrigin)
        assertEquals(ArenaPoint(5f,9f,0f),d.tacticianMovementBounds.clamp(ArenaPoint(5f,99f,0f)))
        assertEquals(6f,d.cameras.scouting.z)
        assertEquals("rain",d.ambientVfx)
        assertEquals("buy",d.interactionRegions.single().action)
    }

    @Test
    fun runtimeBoardDimensionsRepairLegacyArenaDefaults() {
        val legacy = MinecraftArenaDefinition(id="legacy",boardColumns=7,boardRows=8)
        val chess = legacy.forBoardDimensions(8,8)
        assertEquals(8,chess.boardColumns)
        assertEquals(8,chess.boardRows)
        assertEquals(64,chess.boardColumns*chess.boardRows)
        assertEquals(ArenaPoint(7f,7f,0f),chess.boardAnchor(63))

        val xiangqi = legacy.forBoardDimensions(9,10)
        assertEquals(9,xiangqi.boardColumns)
        assertEquals(10,xiangqi.boardRows)
        assertEquals(ArenaPoint(8f,9f,0f),xiangqi.boardAnchor(89))

        val td = legacy.forBoardDimensions(12,8)
        assertEquals(12,td.boardColumns)
        assertEquals(8,td.boardRows)
        assertEquals(ArenaPoint(11f,7f,0f),td.boardAnchor(95))
    }

    @Test
    fun authoredAnchorTopologyIsNeverReinterpretedAsRectangularRuntimeGrid() {
        val authored = MinecraftArenaDefinition(
            id="ludo",
            boardColumns=15,
            boardRows=15,
            boardAnchors=listOf(ArenaPoint(6f,0f),ArenaPoint(6f,1f))
        )
        assertSame(authored,authored.forBoardDimensions(13,4))
        assertEquals(15,authored.boardColumns)
        assertEquals(15,authored.boardRows)
    }

    @Test
    fun bundledNonTftBoardArenasDeclareTheirAuthoritativeBaseDimensions() {
        val expected = mapOf(
            "chess" to (8 to 8),
            "xiangqi" to (9 to 10),
            "tower_defense" to (20 to 12)
        )
        expected.forEach { (id,size) ->
            val stream=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            val arena=stream.bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
            assertEquals(size.first,arena.boardColumns,"$id columns")
            assertEquals(size.second,arena.boardRows,"$id rows")
            assertEquals(size.first*size.second,arena.boardColumns*arena.boardRows,"$id capacity")
        }
    }
}
