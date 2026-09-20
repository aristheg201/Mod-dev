package io.github.aristheg201.svhub.ui

import kotlin.math.*

/** Fits playable space at the authored rear-elevated angle; scenery never controls the camera. */
object SceneCameraFraming {
    fun board(preset:SceneCameraPreset,viewport:UiRect,origin:SceneVec3,columns:Int,rows:Int,
              bench:List<SceneVec3>,widthFraction:Double=.78,cellSize:SceneVec3=SceneVec3(1.0,1.0,1.0)):SceneCameraPreset {
        require(widthFraction in .5.. .9)
        val x0=origin.x-cellSize.x*.5;val x1=x0+columns*cellSize.x
        val y0=origin.y-cellSize.y*.5;val y1=y0+rows*cellSize.y
        val corners=listOf(SceneVec3(x0,y0,origin.z),SceneVec3(x1,y0,origin.z),SceneVec3(x1,y1,origin.z),SceneVec3(x0,y1,origin.z))
        val frame=corners.flatMap { listOf(it,it+SceneVec3(0.0,0.0,1.55)) }+bench.flatMap { anchor ->
            listOf(-.44,.44).flatMap { x -> listOf(-.44,.44).flatMap { y -> listOf(-.18,1.0).map { z -> anchor+SceneVec3(x,y,z) } } }
        }
        val axis=(preset.position-preset.target).normalized()
        fun camera(distance:Double):SceneCameraPreset {
            var candidate=preset.copy(position=preset.target+axis*distance)
            repeat(5) {
                val transform=PerspectiveBoardTransform(viewport,candidate.position,candidate.target,candidate.fov,candidate.near,candidate.far)
                val projected=frame.mapNotNull(transform::project)
                if(projected.size==frame.size) {
                    val center=(projected.minOf { it.y }+projected.maxOf { it.y })*.5
                    val pixels=viewport.y+viewport.height*.5-center
                    val shift=transform.up*(pixels*2*distance*tan(Math.toRadians(candidate.fov)*.5)/viewport.height)
                    candidate=candidate.copy(position=candidate.position+shift,target=candidate.target+shift)
                }
            }
            return candidate
        }
        fun fits(candidate:SceneCameraPreset):Boolean {
            val transform=PerspectiveBoardTransform(viewport,candidate.position,candidate.target,candidate.fov,candidate.near,candidate.far)
            val board=corners.map { transform.project(it) ?: return false }
            if(board.maxOf { it.x }-board.minOf { it.x }>viewport.width*widthFraction) return false
            return frame.all { point ->
                val p=transform.project(point) ?: return false
                p.x>=viewport.x+viewport.width*.035 && p.x<=viewport.right-viewport.width*.035 &&
                    p.y>=viewport.y+viewport.height*.035 && p.y<=viewport.bottom-viewport.height*.035
            }
        }
        var near=2.0;var far=min(100.0,preset.far*.75)
        repeat(28) { val mid=(near+far)*.5;if(fits(camera(mid)))far=mid else near=mid }
        return camera(far)
    }
}
