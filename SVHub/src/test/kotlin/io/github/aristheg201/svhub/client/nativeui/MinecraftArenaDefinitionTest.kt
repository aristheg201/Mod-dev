package io.github.aristheg201.svhub.client.nativeui
import com.google.gson.JsonParser
import kotlin.test.*
class MinecraftArenaDefinitionTest {
 @Test fun parsesAndConsumesCompleteNonDefaultMetadata(){val json="""{"style":"terrain","metadata":{"boardColumns":9,"boardRows":6,"boardOrigin":[2,3,4],"boardAnchors":[[2,3,4]],"benchAnchors":[[1,8,0]],"itemBenchAnchors":[[2,9,0]],"tacticianSpawn":[5,7,0],"tacticianRegion":[1,2,8,9],"humanSpawn":[4,9,0],"opponentSpawn":[4,1,0],"camera":{"spectator":[1,2,3],"scouting":[4,5,6],"carousel":[7,8,9]},"carouselCenter":[4,4,0],"arenaBounds":[0,0,10,10],"lighting":"night","ambientVfx":"rain","music":"x:y","lootAnchors":[[3,3,0]],"combatStartVfx":"start","victoryVfx":"win","defeatVfx":"lose","interactionRegions":[{"id":"shop","bounds":[1,1,2,2],"action":"buy"}]}}""";val d=MinecraftArenaRegistry.parse(JsonParser.parseString(json).asJsonObject);assertEquals(9,d.boardColumns);assertEquals(ArenaPoint(2f,3f,4f),d.boardOrigin);assertEquals(ArenaPoint(5f,9f,0f),d.tacticianMovementBounds.clamp(ArenaPoint(5f,99f,0f)));assertEquals(6f,d.cameras.scouting.z);assertEquals("rain",d.ambientVfx);assertEquals("buy",d.interactionRegions.single().action)}
}
