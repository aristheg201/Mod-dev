package io.github.aristheg201.svhub.ui

/** Immutable retained scene graph owned by SVHub; none of these coordinates are Minecraft Level coordinates. */
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(0.0, 0.0, 0.0),
    val rotationDegrees: SceneVec3 = SceneVec3(0.0, 0.0, 0.0),
    val scale: SceneVec3 = SceneVec3(1.0, 1.0, 1.0)
)

sealed interface SceneNode { val id:String;val transform:SceneTransform;val visible:Boolean }
data class SceneMeshNode(override val id:String,override val transform:SceneTransform,val size:SceneVec3,val material:String,override val visible:Boolean=true):SceneNode
data class SceneBlockModelNode(override val id:String,override val transform:SceneTransform,val blockId:String,override val visible:Boolean=true):SceneNode
data class SceneItemModelNode(override val id:String,override val transform:SceneTransform,val itemId:String,override val visible:Boolean=true):SceneNode
data class ScenePokemonNode(override val id:String,override val transform:SceneTransform,val identity:String,val animation:String,override val visible:Boolean=true):SceneNode
data class SceneTacticianNode(override val id:String,override val transform:SceneTransform,val entityId:String,val animation:String,override val visible:Boolean=true):SceneNode
data class SceneEffectNode(override val id:String,override val transform:SceneTransform,val effectId:String,val startedAt:Long,override val visible:Boolean=true):SceneNode

data class SceneInteractionSurface(val id:String,val origin:SceneVec3,val width:Double,val height:Double,val columns:Int,val rows:Int)
data class SVHubScene(val id:String,val revision:Long,val nodes:List<SceneNode>,val interactions:List<SceneInteractionSurface>) {
    init { require(id.isNotBlank());require(nodes.map(SceneNode::id).distinct().size==nodes.size) }
}

data class SVHubSceneCamera(val position:SceneVec3,val target:SceneVec3,val fov:Double,val near:Double,val far:Double){
    fun transform(viewport:UiRect)=PerspectiveBoardTransform(viewport,position,target,fov,near,far)
    fun interpolate(to:SVHubSceneCamera,progress:Double):SVHubSceneCamera{
        val t=progress.coerceIn(0.0,1.0)
        fun mix(a:Double,b:Double)=a+(b-a)*t
        fun vec(a:SceneVec3,b:SceneVec3)=SceneVec3(mix(a.x,b.x),mix(a.y,b.y),mix(a.z,b.z))
        return SVHubSceneCamera(vec(position,to.position),vec(target,to.target),mix(fov,to.fov),mix(near,to.near),mix(far,to.far))
    }
}
