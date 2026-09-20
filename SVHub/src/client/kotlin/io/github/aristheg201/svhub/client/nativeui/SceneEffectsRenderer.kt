package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.JsonParser
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import io.github.aristheg201.svhub.ui.SceneEffectNode
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import kotlin.math.cos
import kotlin.math.sin

/** Small worldless effects. Definition and transform stay in scene coordinates. */
object SceneEffectsRenderer {
    private data class Effect(val color: Int, val radius: Float, val thickness: Float, val duration: Long, val rise: Float)
    private var definitions: Map<String, Effect>? = null
    fun clear() { definitions = null }

    fun render(poses: PoseStack, node: SceneEffectNode, now: Long) {
        if (!node.visible) return
        val effect = definitions().get(node.effectId) ?: return
        val progress = (now - node.startedAt).toFloat() / effect.duration
        if (progress !in 0f..1f) return
        poses.pushPose()
        try {
            EmbeddedSceneRenderer.applyTransform(poses, node.transform)
            val matrix = poses.last().pose()
            val radius = effect.radius * (.3f + progress)
            val inner = (radius - effect.thickness).coerceAtLeast(0f)
            val alpha = ((effect.color ushr 24) * (1f - progress)).toInt().coerceIn(0, 255)
            val color = (effect.color and 0xffffff) or (alpha shl 24)
            val z = .035f + effect.rise * progress
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false)
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            repeat(24) { segment ->
                val a = segment * Math.PI / 12; val b = (segment + 1) * Math.PI / 12
                builder.addVertex(matrix,(cos(a)*inner).toFloat(),(sin(a)*inner).toFloat(),z).setColor(color)
                builder.addVertex(matrix,(cos(a)*radius).toFloat(),(sin(a)*radius).toFloat(),z).setColor(color)
                builder.addVertex(matrix,(cos(b)*radius).toFloat(),(sin(b)*radius).toFloat(),z).setColor(color)
                builder.addVertex(matrix,(cos(b)*inner).toFloat(),(sin(b)*inner).toFloat(),z).setColor(color)
            }
            BufferUploader.drawWithShader(builder.buildOrThrow())
        } finally {
            RenderSystem.depthMask(true); RenderSystem.disableBlend()
            poses.popPose()
        }
    }

    private fun definitions(): Map<String, Effect> {
        definitions?.let { return it }
        val resource = Minecraft.getInstance().resourceManager.getResource(ResourceLocation.fromNamespaceAndPath("svhub", "scene_effects.json")).orElse(null)
        val loaded = resource?.open()?.bufferedReader()?.use { reader ->
            JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (id, raw) ->
                val value=raw.asJsonObject
                id to Effect(value.get("color").asString.removePrefix("#").toLong(16).toInt(),
                    value.get("radius").asFloat.coerceIn(.01f,8f),value.get("thickness").asFloat.coerceIn(.01f,1f),
                    value.get("durationMs").asLong.coerceIn(1,10000),value.get("rise").asFloat.coerceIn(0f,5f))
            }
        }.orEmpty()
        definitions=loaded
        return loaded
    }
}
