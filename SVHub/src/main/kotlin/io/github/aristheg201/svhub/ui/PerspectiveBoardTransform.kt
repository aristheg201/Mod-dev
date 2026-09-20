package io.github.aristheg201.svhub.ui

import kotlin.math.sqrt
import kotlin.math.tan

data class SceneVec3(val x:Double,val y:Double,val z:Double){
    operator fun plus(o:SceneVec3)=SceneVec3(x+o.x,y+o.y,z+o.z)
    operator fun minus(o:SceneVec3)=SceneVec3(x-o.x,y-o.y,z-o.z)
    operator fun times(v:Double)=SceneVec3(x*v,y*v,z*v)
    fun dot(o:SceneVec3)=x*o.x+y*o.y+z*o.z
    fun cross(o:SceneVec3)=SceneVec3(y*o.z-z*o.y,z*o.x-x*o.z,x*o.y-y*o.x)
    fun normalized():SceneVec3{val length=sqrt(dot(this)).coerceAtLeast(1e-9);return this*(1.0/length)}
}
data class SceneRay(val origin:SceneVec3,val direction:SceneVec3)

/** Shared perspective projection and inverse ray used by both drawing and hit testing. */
class PerspectiveBoardTransform(val viewport:UiRect,val position:SceneVec3,val target:SceneVec3,val fovDegrees:Double,val near:Double=.1,val far:Double=100.0){
    private val forward=(target-position).normalized()
    private val right=forward.cross(SceneVec3(0.0,0.0,1.0)).normalized()
    private val up=right.cross(forward).normalized()
    private val aspect=viewport.width.toDouble()/viewport.height.coerceAtLeast(1)
    private val tangent=tan(Math.toRadians(fovDegrees.coerceIn(20.0,100.0))/2.0)
    fun project(world:SceneVec3):SceneProjectedPoint?{
        val relative=world-position;val depth=relative.dot(forward)
        if(depth !in near..far)return null
        val nx=relative.dot(right)/(depth*tangent*aspect);val ny=relative.dot(up)/(depth*tangent)
        return SceneProjectedPoint((viewport.x+(nx+1.0)*viewport.width/2.0).toFloat(),(viewport.y+(1.0-ny)*viewport.height/2.0).toFloat())
    }
    fun ray(screenX:Double,screenY:Double):SceneRay{
        val nx=((screenX-viewport.x)/viewport.width.coerceAtLeast(1))*2.0-1.0
        val ny=1.0-((screenY-viewport.y)/viewport.height.coerceAtLeast(1))*2.0
        return SceneRay(position,(forward+right*(nx*tangent*aspect)+up*(ny*tangent)).normalized())
    }
    fun boardIntersection(screenX:Double,screenY:Double,elevation:Double=0.0):SceneVec3?{
        val ray=ray(screenX,screenY);if(kotlin.math.abs(ray.direction.z)<1e-9)return null
        val distance=(elevation-ray.origin.z)/ray.direction.z
        return if(distance>=0.0)ray.origin+ray.direction*distance else null
    }
}
