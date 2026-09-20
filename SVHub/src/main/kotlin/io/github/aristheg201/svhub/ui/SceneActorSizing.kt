package io.github.aristheg201.svhub.ui

import kotlin.math.hypot
import kotlin.math.min

/** Bounds of the resolved, posed mesh in scene axes, before board normalization. */
data class SceneActorBounds(val min: SceneVec3, val max: SceneVec3) {
    init { require(min.isFinite() && max.isFinite() && max.x > min.x && max.y > min.y && max.z > min.z) }
    val height get() = max.z - min.z
    val footprint get() = hypot(max.x - min.x, max.y - min.y)
}

data class SceneActorFit(val scale: Double, val offset: SceneVec3, val height: Double, val footprint: Double)

data class SceneActorSizing(val maxHeight: Double = 1.35, val maxFootprint: Double = .92, val maxUpscale: Double = 3.0) {
    init { require(maxHeight > 0 && maxFootprint > 0 && maxUpscale > 0 && listOf(maxHeight,maxFootprint,maxUpscale).all(Double::isFinite)) }
    fun fit(bounds: SceneActorBounds): SceneActorFit {
        // A circular footprint also bounds the silhouette when the actor turns.
        val scale = min(maxUpscale, min(maxHeight / bounds.height, maxFootprint / bounds.footprint))
        return SceneActorFit(scale, SceneVec3(-(bounds.min.x+bounds.max.x)*.5*scale,
            -(bounds.min.y+bounds.max.y)*.5*scale,-bounds.min.z*scale),bounds.height*scale,bounds.footprint*scale)
    }
}
