package io.github.aristheg201.svhub.ui

import kotlin.test.*

class SceneCameraFramingTest {
    @Test fun framesPlayableBoardAndBenchAcrossViewportShapes() {
        val origin=SceneVec3(0.0,0.0,0.0)
        val benches=(0..8).map { SceneVec3(it*.75,8.6,0.0) }
        val preset=SceneCameraPreset("tft",perspective=true,position=SceneVec3(4.2,15.0,9.0),target=SceneVec3(3.0,4.0,0.0),fov=45.0)
        for((width,height) in listOf(960 to 560,548 to 220,480 to 270,340 to 250)) {
            val area=UiRect(20,35,width,height)
            val framed=SceneCameraFraming.board(preset,area,origin,7,8,benches)
            val camera=PerspectiveBoardTransform(area,framed.position,framed.target,framed.fov,framed.near,framed.far)
            val corners=listOf(SceneVec3(-.5,-.5,0.0),SceneVec3(6.5,-.5,0.0),SceneVec3(-.5,7.5,0.0),SceneVec3(6.5,7.5,0.0)).map { assertNotNull(camera.project(it)) }
            val coverage=(corners.maxOf { it.x }-corners.minOf { it.x })/width
            assertTrue(coverage<=.80001f,"Board must not crop at $width x $height")
            val originalDirection=(preset.position-preset.target).normalized()
            val direction=(framed.position-framed.target).normalized()
            assertEquals(originalDirection.z,direction.z,1e-8,"Viewport fitting must preserve authored elevation")
            assertEquals(preset.fov,framed.fov,"Viewport fitting must preserve authored FOV")
            for(p in benches) { val projected=assertNotNull(camera.project(p));assertTrue(area.contains(projected.x.toDouble(),projected.y.toDouble())) }
        }
    }
}
