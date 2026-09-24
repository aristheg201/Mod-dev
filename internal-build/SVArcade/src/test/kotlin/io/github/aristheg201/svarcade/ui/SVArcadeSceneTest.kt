package io.github.aristheg201.svarcade.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SVArcadeSceneTest {
    @Test fun transformsApplyScaleThenRotationThenTranslationAndInvert() {
        val transform=SceneTransform(SceneVec3(10.0,20.0,30.0),SceneVec3(0.0,0.0,90.0),SceneVec3(2.0,3.0,4.0))
        val result=transform.apply(SceneVec3(1.0,2.0,3.0))
        assertEquals(4.0,result.x,1e-9);assertEquals(22.0,result.y,1e-9);assertEquals(42.0,result.z,1e-9)
        val arbitrary=transform.copy(rotationDegrees=SceneVec3(37.0,-24.0,119.0))
        val original=SceneVec3(-3.0,7.0,1.5)
        val restored=arbitrary.inverse(arbitrary.apply(original))
        assertEquals(original.x,restored.x,1e-9);assertEquals(original.y,restored.y,1e-9);assertEquals(original.z,restored.z,1e-9)
    }

    @Test fun offsetElevatedSurfacePicksEveryCellAndRejectsOutside() {
        val camera=PerspectiveBoardTransform(UiRect(30,40,1000,800),SceneVec3(13.0,34.0,18.0),SceneVec3(13.0,23.5,2.0),60.0)
        val surface=SceneInteractionSurface("board",SceneVec3(9.5,19.5,2.0),7.0,8.0,7,8)
        repeat(56) { index ->
            val point=assertNotNull(camera.project(SceneVec3(10.0+index%7,20.0+index/7,2.0)))
            assertEquals(index,surface.pick(camera,point.x.toDouble(),point.y.toDouble()))
        }
        for (outside in listOf(SceneVec3(9.4,23.0,2.0),SceneVec3(16.6,23.0,2.0),SceneVec3(13.0,27.6,2.0))) {
            val point=assertNotNull(camera.project(outside))
            assertNull(surface.pick(camera,point.x.toDouble(),point.y.toDouble()))
        }
        assertNull(surface.pick(camera,Double.NaN,100.0))
        assertNull(surface.pick(camera,-10.0,100.0))
    }

    @Test fun surfacesCannotBePickedBeyondClipPlanesOrBehindCamera() {
        val camera=PerspectiveBoardTransform(UiRect(0,0,800,600),SceneVec3(0.0,0.0,10.0),SceneVec3(0.0,0.0,0.0),60.0,1.0,5.0)
        assertNull(SceneInteractionSurface("far",SceneVec3(-1.0,-1.0,0.0),2.0,2.0,2,2).pick(camera,400.0,300.0))
        assertNull(SceneInteractionSurface("behind",SceneVec3(-1.0,-1.0,12.0),2.0,2.0,2,2).pick(camera,400.0,300.0))
        assertNull(SceneInteractionSurface("near",SceneVec3(-1.0,-1.0,9.5),2.0,2.0,2,2).pick(camera,400.0,300.0))
    }

    @Test fun cameraTransitionsDoNotMutateOrRecompileSceneNodes() {
        val node=SceneMeshNode("floor",SceneTransform(position=SceneVec3(1.0,2.0,0.0)),SceneVec3(7.0,8.0,.25),"roof")
        val scene=SVArcadeScene("arena",1,listOf(node),emptyList())
        val from=SVArcadeSceneCamera(SceneVec3(3.0,-7.0,9.0),SceneVec3(3.0,3.5,0.0),48.0,.1,100.0)
        val to=SVArcadeSceneCamera(SceneVec3(4.0,-4.0,6.0),SceneVec3(3.0,3.5,1.0),36.0,.1,80.0)
        assertEquals(SceneVec3(3.5,-5.5,7.5),from.interpolate(to,.5).position)
        assertEquals(node,scene.nodes.single())
    }

    @Test fun duplicateSceneNodeIdsAreRejected() {
        val node=SceneEffectNode("same",SceneTransform(),"fx",0)
        assertFailsWith<IllegalArgumentException>{SVArcadeScene("arena",1,listOf(node,node),emptyList())}
    }
}
