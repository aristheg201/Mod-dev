package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonParser
import io.github.aristheg201.svhub.ui.UiRect
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotSame
import io.github.aristheg201.svhub.ui.SceneMeshNode
import io.github.aristheg201.svhub.ui.SceneBlockModelNode
import io.github.aristheg201.svhub.ui.SceneItemModelNode
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.SceneCameraFraming
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.PerspectiveBoardTransform

class CompiledArenaSceneTest {
    @Test fun everyBundledArenaPassesTransactionalReloadValidation() {
        java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/assets/svhub/arenas")).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.forEach { file ->
                java.nio.file.Files.newBufferedReader(file).use { reader ->
                    try { MinecraftArenaRegistry.parse(JsonParser.parseReader(reader).asJsonObject) }
                    catch(failure:Exception) { throw AssertionError("Invalid bundled arena $file",failure) }
                }
            }
        }
    }

    @Test fun malformedTransformsAndRegionsRejectTheDefinition() {
        for(raw in listOf("{\"metadata\":{\"boardOrigin\":[0,0,\"NaN\"]}}","{\"metadata\":{\"tacticianRegion\":[1,1,0,0]}}","{\"metadata\":{\"benchAnchors\":[[0]]}}")) {
            kotlin.test.assertFailsWith<IllegalArgumentException> { MinecraftArenaRegistry.parse(JsonParser.parseString(raw).asJsonObject) }
        }
    }

    @Test fun authoredArenasFrameTheBoardAndEveryBenchSlot() {
        for(id in listOf("gotham_rooftops","sector_2814","kanto_stadium","monster_island","dragon_shrine","distortion_rift","ultra_lab","ancient_ruins","temporal_observatory","abyssal_sanctum","crimson_caldera","arkham_asylum","wayne_manor","infinite_void","sukuna_domain")) {
            val arena=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json")).bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
            for((w,h) in listOf(632 to 270,952 to 414,340 to 250)) {
                val area=UiRect(102,40,w,h)
                val preset=arena.camera(ArenaCameraRole.PREPARATION,io.github.aristheg201.svhub.ui.SceneCameras.TFT)
                val framed=io.github.aristheg201.svhub.ui.SceneCameraFraming.board(preset,area,SceneVec3(0.0,0.0,0.0),7,8,arena.benchAnchors.map { SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble()) },cellSize=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),1.0))
                val camera=PerspectiveBoardTransform(area,framed.position,framed.target,framed.fov,framed.near,framed.far)
                val b=arena.logicalBoardBounds
                val points=listOf(SceneVec3(b.minX.toDouble(),b.minY.toDouble(),0.0),SceneVec3(b.maxX.toDouble(),b.minY.toDouble(),0.0),SceneVec3(b.minX.toDouble(),b.maxY.toDouble(),0.0),SceneVec3(b.maxX.toDouble(),b.maxY.toDouble(),0.0)).map { checkNotNull(camera.project(it)) }
                val coverage=(points.maxOf { it.x }-points.minOf { it.x })/w
                assertTrue(coverage in .65f.. .80001f,"$id $w x $h coverage=$coverage")
                val direction=(framed.position-framed.target).normalized()
                assertTrue(direction.z in .5.. .85,"$id must retain a rear-elevated camera")
                assertTrue(kotlin.math.abs(points[3].x-points[2].x)>kotlin.math.abs(points[1].x-points[0].x)*1.15,"$id must show perspective depth")
                val field=checkNotNull(arena.battlefieldBounds)
                assertTrue(field.minX<=b.minX-.5f && field.maxX>=b.maxX+.5f && field.minY<=b.minY-.5f && field.maxY>=b.maxY+.5f)
                val obstacles=arena.geometry.map { it.id to io.github.aristheg201.svhub.ui.SceneBounds.enclosing(it.corners()) }.filter { it.second.max.z>.15 }
                obstacles.forEach { (name,box) ->
                    assertTrue(box.max.x<field.minX || box.min.x>field.maxX || box.max.y<field.minY || box.min.y>field.maxY,"$id $name encroaches on battlefield safety margin")
                    repeat(56) { index ->
                        val p=arena.boardAnchor(index)
                        for(z in listOf(.1,.65,1.5)) assertTrue(!box.blocksSegment(framed.position,SceneVec3(p.x.toDouble(),p.y.toDouble(),z)),"$id $name obscures cell $index at height $z")
                    }
                }
                for(anchor in arena.benchAnchors) for(z in listOf(0.0,.9)) {
                    val p=checkNotNull(camera.project(SceneVec3(anchor.x.toDouble(),anchor.y.toDouble(),anchor.z+z)))
                    assertTrue(area.contains(p.x.toDouble(),p.y.toDouble()),"$id cropped bench at $anchor")
                }
            }
        }
    }
    @Test fun texturedBattlefieldsDoNotRetainTheLegacyFlatFloor() {
        val layout=PokemonSceneLayout(UiRect(0,0,800,600),7,8,400f,30f,28,14)
        for(id in listOf("gotham_rooftops","sector_2814","kanto_stadium","monster_island","dragon_shrine","distortion_rift","ultra_lab","ancient_ruins","temporal_observatory","abyssal_sanctum","crimson_caldera","arkham_asylum","wayne_manor","infinite_void","sukuna_domain")) {
            val arena=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json")).bufferedReader().use {
                MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject)
            }
            assertTrue(arena.texturedBattlefield,"$id must stay on the textured battlefield pipeline")
            val nodes=MinecraftArenaRenderer.compiledScene(layout,arena).scene.nodes
            assertTrue(nodes.none { it is SceneMeshNode && it.id=="floor" },"$id retained the legacy coplanar floor mesh")
            assertTrue(nodes.any { it is SceneBlockModelNode && it.id.startsWith("terrain:") },"$id must compile real block-model terrain")
        }
    }

    // Authored model kinds keep arena scene compilation independent of Minecraft registry bootstrap.
    @Test fun premiumArenaPropsUseAuthoredModelKindsWithoutRegistryBootstrap() {
        val layout=PokemonSceneLayout(UiRect(0,0,800,600),7,8,400f,30f,28,14)
        fun nodes(id:String)=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json")).bufferedReader().use {
            MinecraftArenaRenderer.compiledScene(layout,MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject)).scene.nodes
        }
        assertEquals(3,nodes("arkham_asylum").count { it is SceneBlockModelNode && it.id.startsWith("prop:") })
        assertEquals(3,nodes("wayne_manor").count { it is SceneBlockModelNode && it.id.startsWith("prop:") })
        assertEquals(3,nodes("infinite_void").count { it is SceneItemModelNode && it.id.startsWith("prop:") })
        assertEquals(3,nodes("sukuna_domain").count { it is SceneBlockModelNode && it.id.startsWith("prop:") })
    }

    @Test fun floorSupportsAllCellCentersIncludingOffsetElevatedBoards() {
        val layout=PokemonSceneLayout(UiRect(0,0,800,600),7,8,400f,30f,28,14)
        for (origin in listOf(ArenaPoint(0f,0f),ArenaPoint(12f,-7f,3f))) {
            val definition=MinecraftArenaDefinition(boardOrigin=origin)
            val floor=MinecraftArenaRenderer.compiledScene(layout,definition).scene.nodes.filterIsInstance<SceneMeshNode>().first { it.id=="floor" }
            repeat(56) { index ->
                val point=definition.boardAnchor(index)
                val local=floor.transform.inverse(SceneVec3(point.x.toDouble(),point.y.toDouble(),point.z.toDouble()))
                assertTrue(local.x in -floor.size.x/2..floor.size.x/2)
                assertTrue(local.y in -floor.size.y/2..floor.size.y/2)
                assertEquals(floor.size.z/2,local.z,1e-6)
            }
        }
    }

    @Test fun authoredChangesAndResourceReloadInvalidateSceneButEqualDefinitionsReuseIt() {
        val layout=PokemonSceneLayout(UiRect(0,0,320,180),7,8,160f,30f,28,14)
        val definition=MinecraftArenaDefinition(id="retention")
        val first=MinecraftArenaRenderer.compiledScene(layout,definition)
        assertSame(first,MinecraftArenaRenderer.compiledScene(layout,definition.copy()))
        assertNotSame(first,MinecraftArenaRenderer.compiledScene(layout,definition.copy(boardOrigin=ArenaPoint(1f,0f))))
        assertNotSame(first,MinecraftArenaRenderer.compiledScene(layout,definition.copy(definitionRevision="2")))
        MinecraftArenaRenderer.clearCompiledScenes()
        assertNotSame(first,MinecraftArenaRenderer.compiledScene(layout,definition))
    }

    @Test fun perspectiveCullingNeverFallsBackToIsometricProjectionOrPicking() {
        val camera=PerspectiveBoardTransform(UiRect(0,0,800,600),SceneVec3(0.0,0.0,10.0),SceneVec3(0.0,0.0,0.0),60.0,.1,5.0)
        val layout=PokemonSceneLayout(camera.viewport,7,8,400f,30f,28,14,camera)
        assertNull(layout.project(0f,0f))
        assertNull(layout.project(0f,0f,11f))
        assertNull(layout.pick(400.0,30.0))
    }

    @Test fun staticArenaSceneIsRetainedForStableDefinitionAndLayout() {
        val definition = MinecraftArenaDefinition(props = emptyList())
        val layout = PokemonSceneLayout(UiRect(0, 0, 320, 180), 7, 8, 160f, 30f, 28, 14)
        val first = MinecraftArenaRenderer.compiledScene(layout, definition)
        val second = MinecraftArenaRenderer.compiledScene(layout, definition)
        assertSame(first, second)
    }

    @Test fun viewportAndCameraChangesReuseTheSameSceneSpaceCompilation() {
        val definition=MinecraftArenaDefinition(props=emptyList())
        val compact=PokemonSceneLayout(UiRect(0,0,320,180),7,8,160f,30f,28,14)
        val wide=PokemonSceneLayout(UiRect(50,20,1280,720),7,8,640f,80f,72,36)
        assertSame(MinecraftArenaRenderer.compiledScene(compact,definition),MinecraftArenaRenderer.compiledScene(wide,definition))
    }


    @Test fun premiumHunterCoinArenasHaveMateriallyDistinctSceneIdentity() {
        fun load(id:String)=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            .bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        val markers=mapOf(
            "arkham_asylum" to "structure:tactician-guard-balcony-main",
            "wayne_manor" to "structure:tactician-manor-terrace-main",
            "infinite_void" to "structure:tactician-asteroid-core",
            "sukuna_domain" to "structure:tactician-cursed-overlook"
        )
        val heroMarkers=mapOf(
            "arkham_asylum" to "structure:north-cell-block",
            "wayne_manor" to "structure:manor-main",
            "infinite_void" to "structure:signature-black-hole-core",
            "sukuna_domain" to "structure:signature-shrine-core"
        )
        val ids=markers.keys.toList()
        val arenas=ids.associateWith(::load)
        ids.forEach { id ->
            val arena=arenas.getValue(id)
            assertTrue(arena.geometry.size>=14,id+" must have authored premium geometry")
            assertTrue(arena.texturedBattlefield,id+" must use textured battlefield rendering")
            assertTrue(arena.geometry.any { it.id=="structure:"+markers.getValue(id).removePrefix("structure:") },id+" must author an integrated tactician perch")
            assertTrue(arena.geometry.any { it.id=="structure:"+heroMarkers.getValue(id).removePrefix("structure:") },id+" must author a signature hero landmark")
            val home=arena.tacticianMovementBounds
            val spawn=arena.tacticianSpawn
            assertTrue(spawn.x in home.minX..home.maxX && spawn.y in home.minY..home.maxY,id+" tactician spawn must be inside its movement pocket")
            val field=checkNotNull(arena.battlefieldBounds)
            assertTrue(home.maxX<=field.minX || home.minX>=field.maxX || home.maxY<=field.minY || home.minY>=field.maxY,id+" tactician home must not overlap the battlefield")
            assertTrue(arena.framingAnchors().contains(spawn),id+" tactician home must participate in camera framing")
        }
        for(i in ids.indices) for(j in i+1 until ids.size) {
            val a=arenas.getValue(ids[i]); val b=arenas.getValue(ids[j])
            assertNotEquals(a.geometry.map{it.material}.toSet(),b.geometry.map{it.material}.toSet())
            assertNotEquals(a.geometry.map{it.id}.toSet(),b.geometry.map{it.id}.toSet())
            assertNotEquals(a.benchAnchors,b.benchAnchors)
            assertNotEquals(a.camera(ArenaCameraRole.PREPARATION,SceneCameras.TFT),b.camera(ArenaCameraRole.PREPARATION,SceneCameras.TFT))
            assertNotEquals(a.tacticianMovementBounds,b.tacticianMovementBounds)
        }
    }

    @Test fun premiumHeroLandmarksAreVisibleAndReadAsMajorSilhouettes() {
        fun load(id:String)=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            .bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        val heroes=mapOf(
            "arkham_asylum" to "structure:north-cell-block",
            "wayne_manor" to "structure:manor-main",
            "infinite_void" to "structure:signature-black-hole-core",
            "sukuna_domain" to "structure:signature-shrine-core"
        )
        val viewport=UiRect(0,0,1280,720)
        heroes.forEach { (id,marker) ->
            val arena=load(id)
            val node=checkNotNull(arena.geometry.firstOrNull { it.id==marker }) { id+" missing hero landmark "+marker }
            val framed=SceneCameraFraming.board(
                arena.camera(ArenaCameraRole.PREPARATION,SceneCameras.TFT),
                viewport,
                SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble()),
                arena.boardColumns,
                arena.boardRows,
                arena.framingAnchors().map { SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble()) },
                cellSize=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble())
            )
            val camera=PerspectiveBoardTransform(viewport,framed.position,framed.target,framed.fov,framed.near,framed.far)
            val projected=node.corners().mapNotNull(camera::project)
            assertEquals(8,projected.size,id+" hero landmark must remain inside the preparation frustum")
            val width=projected.maxOf { it.x }-projected.minOf { it.x }
            val height=projected.maxOf { it.y }-projected.minOf { it.y }
            assertTrue(width>=viewport.width*.055f,id+" hero landmark is too small horizontally: "+width)
            assertTrue(height>=viewport.height*.075f,id+" hero landmark is too small vertically: "+height)
            val centerX=(projected.minOf { it.x }+projected.maxOf { it.x })*.5f
            val centerY=(projected.minOf { it.y }+projected.maxOf { it.y })*.5f
            assertTrue(centerX in viewport.x.toFloat()..viewport.right.toFloat(),id+" hero landmark must stay on screen")
            assertTrue(centerY in viewport.y.toFloat()..viewport.bottom.toFloat(),id+" hero landmark must stay on screen")
        }
    }

    // Premium staging acceptance guards both the authored home pad and gameplay framing.
    @Test fun premiumArenaPreparationCamerasKeepTacticianHomeOnScreen() {
        fun load(id:String)=checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            .bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        val viewport=UiRect(0,0,1280,720)
        for(id in listOf("arkham_asylum","wayne_manor","infinite_void","sukuna_domain")) {
            val arena=load(id)
            val framed=SceneCameraFraming.board(
                arena.camera(ArenaCameraRole.PREPARATION,SceneCameras.TFT),
                viewport,
                SceneVec3(arena.boardOrigin.x.toDouble(),arena.boardOrigin.y.toDouble(),arena.boardOrigin.z.toDouble()),
                arena.boardColumns,
                arena.boardRows,
                arena.framingAnchors().map { SceneVec3(it.x.toDouble(),it.y.toDouble(),it.z.toDouble()) },
                cellSize=SceneVec3(arena.cellSize.x.toDouble(),arena.cellSize.y.toDouble(),arena.cellSize.z.toDouble())
            )
            val transform=PerspectiveBoardTransform(viewport,framed.position,framed.target,framed.fov,framed.near,framed.far)
            val projected=transform.project(SceneVec3(arena.tacticianSpawn.x.toDouble(),arena.tacticianSpawn.y.toDouble(),arena.tacticianSpawn.z.toDouble()))
            assertTrue(projected!=null,id+" tactician home must project into the preparation camera")
            assertTrue(projected!!.x>=viewport.width*.035 && projected.x<=viewport.right-viewport.width*.035,id+" tactician home must remain horizontally visible")
            assertTrue(projected.y>=viewport.height*.035 && projected.y<=viewport.bottom-viewport.height*.035,id+" tactician home must remain vertically visible")
        }
    }

    @Test fun gothamAndSectorCompileFromMateriallyDifferentAuthoredScenes() {
        fun load(id: String): MinecraftArenaDefinition {
            val stream = checkNotNull(javaClass.getResourceAsStream("/assets/svhub/arenas/$id.json"))
            return stream.bufferedReader().use { MinecraftArenaRegistry.parse(JsonParser.parseReader(it).asJsonObject) }
        }
        val gotham = load("gotham_rooftops")
        val sector = load("sector_2814")
        assertNotEquals(gotham.geometry, sector.geometry)
        assertNotEquals(gotham.cameras, sector.cameras)
        assertNotEquals(gotham.tacticianMovementBounds, sector.tacticianMovementBounds)
        assertNotEquals(gotham.benchAnchors, sector.benchAnchors)
        assertTrue(gotham.geometry.isNotEmpty() && sector.geometry.isNotEmpty())
    }
}
