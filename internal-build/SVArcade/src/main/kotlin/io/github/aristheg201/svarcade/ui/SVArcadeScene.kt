package io.github.aristheg201.svarcade.ui

/** Immutable retained scene graph owned by SVArcade; none of these coordinates are Minecraft Level coordinates. */
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(0.0, 0.0, 0.0),
    val rotationDegrees: SceneVec3 = SceneVec3(0.0, 0.0, 0.0),
    val scale: SceneVec3 = SceneVec3(1.0, 1.0, 1.0)
) {
    init {
        require(position.isFinite() && rotationDegrees.isFinite() && scale.isFinite())
        require(scale.x != 0.0 && scale.y != 0.0 && scale.z != 0.0)
    }

    /** Local scale, then X/Y/Z rotations, then translation. Scene Z is up. */
    fun apply(point: SceneVec3): SceneVec3 = rotate(
        SceneVec3(point.x * scale.x, point.y * scale.y, point.z * scale.z), rotationDegrees
    ) + position

    fun inverse(point: SceneVec3): SceneVec3 {
        val translated = point - position
        val z = rotateZ(translated, -rotationDegrees.z)
        val y = rotateY(z, -rotationDegrees.y)
        val x = rotateX(y, -rotationDegrees.x)
        return SceneVec3(x.x / scale.x, x.y / scale.y, x.z / scale.z)
    }

    private fun rotate(p: SceneVec3, r: SceneVec3) = rotateZ(rotateY(rotateX(p, r.x), r.y), r.z)
    private fun rotateX(p: SceneVec3, degrees: Double): SceneVec3 {
        val a = Math.toRadians(degrees); val c = kotlin.math.cos(a); val s = kotlin.math.sin(a)
        return SceneVec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
    }
    private fun rotateY(p: SceneVec3, degrees: Double): SceneVec3 {
        val a = Math.toRadians(degrees); val c = kotlin.math.cos(a); val s = kotlin.math.sin(a)
        return SceneVec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
    }
    private fun rotateZ(p: SceneVec3, degrees: Double): SceneVec3 {
        val a = Math.toRadians(degrees); val c = kotlin.math.cos(a); val s = kotlin.math.sin(a)
        return SceneVec3(p.x * c - p.y * s, p.x * s + p.y * c, p.z)
    }
}

sealed interface SceneNode { val id:String;val transform:SceneTransform;val visible:Boolean }
/** Cuboid centered at its transform origin; size is the full local extent on each axis. */
data class SceneMeshNode(override val id:String,override val transform:SceneTransform,val size:SceneVec3,val material:String,override val visible:Boolean=true):SceneNode {
    init { require(size.isFinite() && size.x > 0.0 && size.y > 0.0 && size.z > 0.0) }
    fun corners(): List<SceneVec3> = listOf(
        SceneVec3(-.5, -.5, -.5), SceneVec3(.5, -.5, -.5),
        SceneVec3(.5, .5, -.5), SceneVec3(-.5, .5, -.5),
        SceneVec3(-.5, -.5, .5), SceneVec3(.5, -.5, .5),
        SceneVec3(.5, .5, .5), SceneVec3(-.5, .5, .5)
    ).map { transform.apply(SceneVec3(it.x * size.x, it.y * size.y, it.z * size.z)) }
}
data class SceneBlockModelNode(override val id:String,override val transform:SceneTransform,val blockId:String,override val visible:Boolean=true):SceneNode
data class SceneItemModelNode(override val id:String,override val transform:SceneTransform,val itemId:String,override val visible:Boolean=true):SceneNode
data class ScenePokemonNode(override val id:String,override val transform:SceneTransform,val identity:String,val animation:String,override val visible:Boolean=true):SceneNode
data class SceneTacticianNode(
    override val id:String,
    override val transform:SceneTransform,
    val entityId:String="",
    val animation:String="IDLE",
    val pokemonSpecies:String="",
    val pokemonAspects:Set<String> = emptySet(),
    override val visible:Boolean=true
):SceneNode
data class SceneEffectNode(override val id:String,override val transform:SceneTransform,val effectId:String,val startedAt:Long,override val visible:Boolean=true):SceneNode

/** Axis-aligned scene plane. Origin is its minimum corner, with half-open outer edges. */
data class SceneInteractionSurface(val id:String,val origin:SceneVec3,val width:Double,val height:Double,val columns:Int,val rows:Int) {
    init {
        require(origin.isFinite() && width.isFinite() && height.isFinite())
        require(width > 0.0 && height > 0.0 && columns > 0 && rows > 0)
    }

    fun pick(camera: PerspectiveBoardTransform, screenX: Double, screenY: Double): Int? {
        val point = camera.boardIntersection(screenX, screenY, origin.z) ?: return null
        val x = (point.x - origin.x) / width
        val y = (point.y - origin.y) / height
        if (x < 0.0 || x >= 1.0 || y < 0.0 || y >= 1.0) return null
        return kotlin.math.floor(y * rows).toInt() * columns + kotlin.math.floor(x * columns).toInt()
    }
}
data class SVArcadeScene(val id:String,val revision:Long,val nodes:List<SceneNode>,val interactions:List<SceneInteractionSurface>) {
    init { require(id.isNotBlank());require(nodes.map(SceneNode::id).distinct().size==nodes.size) }
}

data class SVArcadeSceneCamera(val position:SceneVec3,val target:SceneVec3,val fov:Double,val near:Double,val far:Double){
    fun transform(viewport:UiRect)=PerspectiveBoardTransform(viewport,position,target,fov,near,far)
    fun interpolate(to:SVArcadeSceneCamera,progress:Double):SVArcadeSceneCamera{
        val t=progress.coerceIn(0.0,1.0)
        fun mix(a:Double,b:Double)=a+(b-a)*t
        fun vec(a:SceneVec3,b:SceneVec3)=SceneVec3(mix(a.x,b.x),mix(a.y,b.y),mix(a.z,b.z))
        return SVArcadeSceneCamera(vec(position,to.position),vec(target,to.target),mix(fov,to.fov),mix(near,to.near),mix(far,to.far))
    }
}
