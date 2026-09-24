package io.github.aristheg201.svarcade.client.cobblemon

import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.aristheg201.svarcade.ui.SceneActorBounds
import io.github.aristheg201.svarcade.ui.SceneActorSizing
import io.github.aristheg201.svarcade.ui.SceneVec3
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/** CPU vertex sink used once per resolved species/form/aspects to measure real geometry. */
internal class SceneModelBounds : VertexConsumer {
    private var x0=Double.POSITIVE_INFINITY; private var y0=x0; private var z0=x0
    private var x1=Double.NEGATIVE_INFINITY; private var y1=x1; private var z1=x1
    override fun addVertex(x:Float,y:Float,z:Float):VertexConsumer {
        if(x.isFinite() && y.isFinite() && z.isFinite()) {
            x0=minOf(x0,x.toDouble());y0=minOf(y0,y.toDouble());z0=minOf(z0,z.toDouble())
            x1=maxOf(x1,x.toDouble());y1=maxOf(y1,y.toDouble());z1=maxOf(z1,z.toDouble())
        }
        return this
    }
    override fun setColor(r:Int,g:Int,b:Int,a:Int):VertexConsumer=this
    override fun setUv(u:Float,v:Float):VertexConsumer=this
    override fun setUv1(u:Int,v:Int):VertexConsumer=this
    override fun setUv2(u:Int,v:Int):VertexConsumer=this
    override fun setNormal(x:Float,y:Float,z:Float):VertexConsumer=this
    fun bounds()=SceneActorBounds(SceneVec3(x0,y0,z0),SceneVec3(x1,y1,z1))
}

internal object ScenePresentationSizing {
    private var cached:SceneActorSizing?=null
    fun clear() { cached=null }
    fun profile():SceneActorSizing {
        cached?.let { return it }
        val resource=Minecraft.getInstance().resourceManager.getResource(ResourceLocation.fromNamespaceAndPath("svarcade","scene_presentation.json")).orElse(null)
        val profile=resource?.open()?.bufferedReader()?.use {
            val obj=JsonParser.parseReader(it).asJsonObject.getAsJsonObject("boardUnit")
            SceneActorSizing(obj.get("maxHeightCells").asDouble,obj.get("maxFootprintCells").asDouble,obj.get("maxUpscale").asDouble)
        } ?: SceneActorSizing()
        cached=profile
        return profile
    }
}
