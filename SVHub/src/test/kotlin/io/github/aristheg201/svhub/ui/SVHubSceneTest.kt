package io.github.aristheg201.svhub.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SVHubSceneTest {
    @Test fun cameraTransitionsDoNotMutateOrRecompileSceneNodes() {
        val node=SceneMeshNode("floor",SceneTransform(position=SceneVec3(1.0,2.0,0.0)),SceneVec3(7.0,8.0,.25),"roof")
        val scene=SVHubScene("arena",1,listOf(node),emptyList())
        val from=SVHubSceneCamera(SceneVec3(3.0,-7.0,9.0),SceneVec3(3.0,3.5,0.0),48.0,.1,100.0)
        val to=SVHubSceneCamera(SceneVec3(4.0,-4.0,6.0),SceneVec3(3.0,3.5,1.0),36.0,.1,80.0)
        assertEquals(SceneVec3(3.5,-5.5,7.5),from.interpolate(to,.5).position)
        assertEquals(node,scene.nodes.single())
    }

    @Test fun duplicateSceneNodeIdsAreRejected() {
        val node=SceneEffectNode("same",SceneTransform(),"fx",0)
        assertFailsWith<IllegalArgumentException>{SVHubScene("arena",1,listOf(node,node),emptyList())}
    }
}
