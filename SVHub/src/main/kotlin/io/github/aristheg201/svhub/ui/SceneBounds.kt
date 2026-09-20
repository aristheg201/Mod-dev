package io.github.aristheg201.svhub.ui

/** Conservative bounds for scenery placement and camera-to-gameplay visibility checks. */
data class SceneBounds(val min:SceneVec3,val max:SceneVec3) {
    init { require(min.isFinite() && max.isFinite() && min.x<=max.x && min.y<=max.y && min.z<=max.z) }
    fun blocksSegment(from:SceneVec3,to:SceneVec3):Boolean {
        val direction=to-from
        var enter=0.0;var leave=1.0
        for((origin,delta,low,high) in listOf(
            listOf(from.x,direction.x,min.x,max.x),listOf(from.y,direction.y,min.y,max.y),listOf(from.z,direction.z,min.z,max.z))) {
            if(kotlin.math.abs(delta)<1e-9) { if(origin<low || origin>high) return false }
            else {
                val a=(low-origin)/delta;val b=(high-origin)/delta
                enter=maxOf(enter,minOf(a,b));leave=minOf(leave,maxOf(a,b))
                if(enter>leave) return false
            }
        }
        return enter<.99999 && leave>.00001
    }
    companion object {
        fun enclosing(points:List<SceneVec3>)=SceneBounds(
            SceneVec3(points.minOf { it.x },points.minOf { it.y },points.minOf { it.z }),
            SceneVec3(points.maxOf { it.x },points.maxOf { it.y },points.maxOf { it.z }))
    }
}
