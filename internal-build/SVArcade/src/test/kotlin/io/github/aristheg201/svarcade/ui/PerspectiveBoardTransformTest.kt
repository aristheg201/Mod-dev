package io.github.aristheg201.svarcade.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PerspectiveBoardTransformTest {
    @Test fun projectedBoardPointRoundTripsThroughTheSameCameraRay() {
        val camera=PerspectiveBoardTransform(UiRect(0,0,800,450),SceneVec3(3.0,-7.0,9.0),SceneVec3(3.0,3.5,0.0),48.0)
        val screen=assertNotNull(camera.project(SceneVec3(4.0,5.0,0.0)))
        val hit=assertNotNull(camera.boardIntersection(screen.x.toDouble(),screen.y.toDouble()))
        assertEquals(4.0,hit.x,.001);assertEquals(5.0,hit.y,.001);assertEquals(0.0,hit.z,.001)
    }
}
