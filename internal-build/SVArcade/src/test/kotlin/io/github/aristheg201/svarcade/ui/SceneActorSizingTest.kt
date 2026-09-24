package io.github.aristheg201.svarcade.ui

import kotlin.test.*

class SceneActorSizingTest {
    @Test fun extremeSpeciesRemainWithinOneCellAtAnyYaw() {
        val sizing=SceneActorSizing()
        for(width in listOf(.1,.5,1.0,4.0,40.0)) for(depth in listOf(.15,.8,2.0,18.0)) for(height in listOf(.2,1.0,3.0,25.0)) {
            val bounds=SceneActorBounds(SceneVec3(-width/3,-depth/2,-.4),SceneVec3(width*2/3,depth/2,height-.4))
            val fit=sizing.fit(bounds)
            assertTrue(fit.height<=sizing.maxHeight+1e-9)
            assertTrue(fit.footprint<=sizing.maxFootprint+1e-9)
            assertEquals(0.0,bounds.min.z*fit.scale+fit.offset.z,1e-9)
            for(yaw in 0..350 step 10) {
                val transform=SceneTransform(rotationDegrees=SceneVec3(0.0,0.0,yaw.toDouble()))
                for(x in listOf(bounds.min.x,bounds.max.x)) for(y in listOf(bounds.min.y,bounds.max.y)) {
                    val corner=transform.apply(SceneVec3(x*fit.scale+fit.offset.x,y*fit.scale+fit.offset.y,0.0))
                    assertTrue(kotlin.math.abs(corner.x)<=sizing.maxFootprint/2+1e-9)
                    assertTrue(kotlin.math.abs(corner.y)<=sizing.maxFootprint/2+1e-9)
                }
            }
        }
    }
    @Test fun adjacentNormalizedUnitsHaveAVisibleGap() {
        val fit=SceneActorSizing().fit(SceneActorBounds(SceneVec3(-5.0,-3.0,0.0),SceneVec3(5.0,3.0,8.0)))
        assertTrue(1.0-fit.footprint>=.079999)
    }
}
