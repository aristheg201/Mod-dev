package io.github.aristheg201.svhub.ui

import kotlin.math.*

/** Frames playable geometry independently of decorative skyline/perimeter bounds. */
object SceneCameraFraming {
    fun board(preset: SceneCameraPreset, viewport: UiRect, origin: SceneVec3, columns: Int, rows: Int,
              bench: List<SceneVec3>, widthFraction: Double = .78): SceneCameraPreset {
        require(widthFraction in .5.. .9)
        val x0=origin.x-.5; val x1=x0+columns
        val y0=origin.y-.5; val y1=y0+rows
        val board=listOf(SceneVec3(x0,y0,origin.z),SceneVec3(x1,y0,origin.z),SceneVec3(x1,y1,origin.z),SceneVec3(x0,y1,origin.z))
        val frame=board+board.map { it+SceneVec3(0.0,0.0,1.5) }+bench.flatMap { anchor ->
            listOf(-.4,.4).flatMap { x -> listOf(-.4,.4).flatMap { y -> listOf(-.15,1.0).map { z -> anchor+SceneVec3(x,y,z) } } }
        }
        val focus=SceneVec3((x0+x1)*.5,(frame.minOf { it.y }+frame.maxOf { it.y })*.5,origin.z+.35)
        val direction=preset.position-preset.target
        val horizontal=SceneVec3(direction.x,direction.y,0.0).normalized()
        var best=preset
        var bestWidth=0.0
        val preferredPitch=Math.toDegrees(atan2(direction.z,hypot(direction.x,direction.y))).roundToInt().coerceIn(24,40)
        for(pitch in preferredPitch downTo 18 step 2) {
            val a=Math.toRadians(pitch.toDouble())
            val axis=horizontal*cos(a)+SceneVec3(0.0,0.0,sin(a))
            fun camera(distance:Double):SceneCameraPreset {
                var candidate=preset.copy(position=focus+axis*distance,target=focus)
                repeat(3) {
                    val projection=PerspectiveBoardTransform(viewport,candidate.position,candidate.target,candidate.fov,candidate.near,candidate.far)
                    val points=frame.mapNotNull(projection::project)
                    if(points.size==frame.size) {
                        val centerY=(points.minOf { it.y }+points.maxOf { it.y })*.5
                        val pixels=viewport.y+viewport.height*.5-centerY
                        val shift=projection.up*(pixels*2*distance*tan(Math.toRadians(candidate.fov)*.5)/viewport.height)
                        candidate=candidate.copy(position=candidate.position+shift,target=candidate.target+shift)
                    }
                }
                return candidate
            }
            fun fits(candidate:SceneCameraPreset):Boolean {
                val transform=PerspectiveBoardTransform(viewport,candidate.position,candidate.target,candidate.fov,candidate.near,candidate.far)
                val points=frame.map { transform.project(it) ?: return false }
                val projectedBoard=board.map { transform.project(it) ?: return false }
                val span=projectedBoard.maxOf { it.x }-projectedBoard.minOf { it.x }
                return span<=viewport.width*widthFraction && points.all {
                    it.x>=viewport.x+viewport.width*.035 && it.x<=viewport.right-viewport.width*.035 &&
                    it.y>=viewport.y+viewport.height*.06 && it.y<=viewport.bottom-viewport.height*.06
                }
            }
            var near=2.0;var far=min(80.0,preset.far*.8)
            repeat(24) { val mid=(near+far)*.5;if(fits(camera(mid)))far=mid else near=mid }
            val candidate=camera(far)
            val transform=PerspectiveBoardTransform(viewport,candidate.position,candidate.target,candidate.fov,candidate.near,candidate.far)
            val points=board.mapNotNull(transform::project)
            val width=if(points.size==4)(points.maxOf { it.x }-points.minOf { it.x })/viewport.width else 0f
            if(width>bestWidth) { best=candidate;bestWidth=width.toDouble() }
            if(width>=widthFraction-.015) return candidate
        }
        // A short viewport exaggerates the near bench with a wide lens. Tighten the
        // lens before sacrificing battlefield width or cropping playable geometry.
        if(bestWidth<min(.65,widthFraction) && preset.fov>24.0) {
            return board(preset.copy(fov=max(24.0,preset.fov*.8)),viewport,origin,columns,rows,bench,widthFraction)
        }
        return best
    }
}
