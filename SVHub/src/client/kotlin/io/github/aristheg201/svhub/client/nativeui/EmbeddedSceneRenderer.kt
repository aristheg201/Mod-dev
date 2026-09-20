package io.github.aristheg201.svhub.client.nativeui

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Axis
import io.github.aristheg201.svhub.ui.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import kotlin.math.roundToInt

/** Worldless 3D viewport. Geometry, model transforms, camera and depth belong to SVHub. */
object EmbeddedSceneRenderer {
    private data class Mesh(val buffer: VertexBuffer, val bounds: FloatArray,val blockAtlas:Boolean=false)
    private data class BlockAsset(val state: BlockState, val model: BakedModel, val tint: Int)
    private data class ItemAsset(val stack: ItemStack, val model: BakedModel)
    private val meshes = linkedMapOf<SVHubScene, List<Mesh>>()
    private val blocks = hashMapOf<String, BlockAsset?>()
    private val items = hashMapOf<String, ItemAsset?>()
    private var target: TextureTarget? = null
    @Volatile private var invalidated=false
    private val buffers by lazy { MultiBufferSource.immediate(ByteBufferBuilder(1024 * 1024)) }
    fun invalidate() { invalidated=true }

    fun clear() {
        RenderSystem.assertOnRenderThread()
        meshes.values.forEach { list -> list.forEach { it.buffer.close() } }
        meshes.clear(); blocks.clear(); items.clear()
        SceneEffectsRenderer.clear()
        target?.destroyBuffers(); target = null
    }

    fun render(gui: GuiGraphics, layout: PokemonSceneLayout, theme: MinecraftArenaDefinition,
               actors: (PoseStack, MultiBufferSource.BufferSource) -> Unit = { _, _ -> }) {
        val camera = layout.perspective ?: return
        val scene = MinecraftArenaRenderer.compiledScene(layout, theme).scene
        val client = Minecraft.getInstance()
        gui.flush()
        val saved = HostState()
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        try {
            if(invalidated) { clear();invalidated=false }
            val width = (layout.area.width * client.window.guiScale).roundToInt().coerceAtLeast(1)
            val height = (layout.area.height * client.window.guiScale).roundToInt().coerceAtLeast(1)
            val surface = target?.also { if (it.width != width || it.height != height) it.resize(width,height,Minecraft.ON_OSX) }
                ?: TextureTarget(width,height,true,Minecraft.ON_OSX).also { target=it }
            RenderSystem.disableScissor()
            surface.setClearColor((theme.backgroundColor ushr 16 and 255)/255f,(theme.backgroundColor ushr 8 and 255)/255f,(theme.backgroundColor and 255)/255f,1f)
            surface.clear(Minecraft.ON_OSX)
            surface.bindWrite(true)
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.disableBlend(); RenderSystem.disableCull()
            RenderSystem.setShaderColor(1f,1f,1f,1f)
            RenderSystem.setShaderFogStart(camera.far.toFloat()); RenderSystem.setShaderFogEnd(camera.far.toFloat()+1f)
            val projection=Matrix4f().perspective(Math.toRadians(camera.fovDegrees).toFloat(),width.toFloat()/height,camera.near.toFloat(),camera.far.toFloat())
            val p=camera.position; val t=camera.target; val up=camera.up
            val view=Matrix4f().lookAt(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),t.x.toFloat(),t.y.toFloat(),t.z.toFloat(),up.x.toFloat(),up.y.toFloat(),up.z.toFloat())
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN)
            modelView.identity(); RenderSystem.applyModelViewMatrix()
            val frustum=FrustumIntersection(Matrix4f(projection).mul(view))
            val staticMeshes=meshes.getOrPut(scene) { compileMeshes(scene,theme) }
            val meshShader=GameRenderer.getPositionColorShader() ?: return
            staticMeshes.forEach { mesh ->
                val b=mesh.bounds
                if (frustum.testAab(b[0],b[1],b[2],b[3],b[4],b[5])) {
                    val renderType=if(mesh.blockAtlas) RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS) else null
                    renderType?.setupRenderState()
                    mesh.buffer.bind()
                    mesh.buffer.drawWithShader(view,projection,if(mesh.blockAtlas) checkNotNull(RenderSystem.getShader()) else meshShader)
                    renderType?.clearRenderState()
                }
            }
            VertexBuffer.unbind()
            val poses=PoseStack().also { it.mulPose(view) }
            for (node in scene.nodes) {
                if (!node.visible || node is SceneMeshNode || node is SceneBlockModelNode) continue
                val position=node.transform.position
                val radius=(maxOf(kotlin.math.abs(node.transform.scale.x),kotlin.math.abs(node.transform.scale.y),kotlin.math.abs(node.transform.scale.z))*2).toFloat()
                if (!frustum.testSphere(position.x.toFloat(),position.y.toFloat(),position.z.toFloat(),radius)) continue
                poses.pushPose()
                try {
                    applyTransform(poses,node.transform)
                    when(node) {
                        is SceneBlockModelNode -> renderBlock(poses,node.blockId)
                        is SceneItemModelNode -> renderItem(poses,node.itemId)
                        else -> Unit
                    }
                } finally { poses.popPose() }
            }
            actors(poses,buffers)
            buffers.endBatch()
        } finally {
            VertexBuffer.unbind()
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix()
            saved.restore()
        }
        // Composite only the color texture; scene depth never enters the host GUI depth buffer.
        val surface=target ?: return
        try {
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false)
            RenderSystem.disableBlend()
            RenderSystem.setShader(GameRenderer::getPositionTexShader)
            RenderSystem.setShaderTexture(0,surface.colorTextureId)
            RenderSystem.setShaderColor(1f,1f,1f,1f)
            val rect=layout.area; val pose=gui.pose().last().pose()
            val builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX)
            builder.addVertex(pose,rect.x.toFloat(),rect.bottom.toFloat(),0f).setUv(0f,0f)
            builder.addVertex(pose,rect.right.toFloat(),rect.bottom.toFloat(),0f).setUv(1f,0f)
            builder.addVertex(pose,rect.right.toFloat(),rect.y.toFloat(),0f).setUv(1f,1f)
            builder.addVertex(pose,rect.x.toFloat(),rect.y.toFloat(),0f).setUv(0f,1f)
            BufferUploader.drawWithShader(builder.buildOrThrow())
        } finally { saved.restore() }
    }

    /** T * Rz * Ry * Rx * S, identical to SceneTransform.apply. */
    fun applyTransform(poses: PoseStack, transform: SceneTransform) {
        val p=transform.position;val r=transform.rotationDegrees;val s=transform.scale
        poses.translate(p.x,p.y,p.z)
        poses.mulPose(Axis.ZP.rotationDegrees(r.z.toFloat()))
        poses.mulPose(Axis.YP.rotationDegrees(r.y.toFloat()))
        poses.mulPose(Axis.XP.rotationDegrees(r.x.toFloat()))
        poses.scale(s.x.toFloat(),s.y.toFloat(),s.z.toFloat())
    }

    private fun renderBlock(poses: PoseStack, id: String) {
        val asset=blockAsset(id) ?: return
        renderBlock(poses,asset,buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)))
    }

    private fun blockAsset(id:String):BlockAsset? = blocks.getOrPut(id) {
            val client=Minecraft.getInstance()
            val key=ResourceLocation.tryParse(id) ?: return@getOrPut null
            val block=BuiltInRegistries.BLOCK.getOptional(key).orElse(null) ?: return@getOrPut null
            val state=block.defaultBlockState()
            BlockAsset(state,client.blockRenderer.getBlockModel(state),client.blockColors.getColor(state,null,null,0))
        }

    private fun renderBlock(poses:PoseStack,asset:BlockAsset,vertices:VertexConsumer) {
        poses.mulPose(Axis.XP.rotationDegrees(90f))
        poses.translate(-.5,0.0,-.5)
        val tint=asset.tint
        Minecraft.getInstance().blockRenderer.modelRenderer.renderModel(poses.last(),vertices,asset.state,asset.model,(tint ushr 16 and 255)/255f,(tint ushr 8 and 255)/255f,(tint and 255)/255f,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY)
    }

    fun renderItem(poses: PoseStack, id: String) {
        val client=Minecraft.getInstance()
        val asset=items.getOrPut(id) {
            val key=ResourceLocation.tryParse(id) ?: return@getOrPut null
            val item=BuiltInRegistries.ITEM.getOptional(key).orElse(null) ?: return@getOrPut null
            val stack=ItemStack(item)
            ItemAsset(stack,client.itemRenderer.getModel(stack,null,null,0))
        } ?: return
        poses.mulPose(Axis.XP.rotationDegrees(90f))
        client.itemRenderer.render(asset.stack,ItemDisplayContext.GROUND,false,poses,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,asset.model)
    }

    /** Board highlights share scene depth, so they cannot paint over an actor. */
    fun renderCells(poses:PoseStack,theme:MinecraftArenaDefinition,cells:Set<Int>,color:Int) {
        if(cells.isEmpty()) return
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc()
        RenderSystem.depthMask(false)
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR)
            val matrix=poses.last().pose()
            for(index in cells) {
                val p=theme.boardAnchor(index)
                val dx=theme.cellSize.x*.47f;val dy=theme.cellSize.y*.47f
                builder.addVertex(matrix,p.x-dx,p.y-dy,p.z+.018f).setColor(color)
                builder.addVertex(matrix,p.x+dx,p.y-dy,p.z+.018f).setColor(color)
                builder.addVertex(matrix,p.x+dx,p.y+dy,p.z+.018f).setColor(color)
                builder.addVertex(matrix,p.x-dx,p.y+dy,p.z+.018f).setColor(color)
            }
            BufferUploader.drawWithShader(builder.buildOrThrow())
        } finally { RenderSystem.depthMask(true);RenderSystem.disableBlend() }
    }

    private fun compileMeshes(scene: SVHubScene, theme: MinecraftArenaDefinition): List<Mesh> {
        while(meshes.size >= 8) meshes.remove(meshes.keys.first())?.forEach { it.buffer.close() }
        val colored=scene.nodes.filterIsInstance<SceneMeshNode>().filter { it.visible }.groupBy { it.material }.map { (material,nodes) ->
            val allCorners=nodes.map { it.corners() }
            val base=material.removePrefix("#").toLongOrNull(16)?.let { (it or 0xff000000L).toInt() }
                ?: if(material=="bench") theme.borderColor else theme.floorColor
            val builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR)
            val faces=arrayOf(intArrayOf(0,3,2,1),intArrayOf(0,1,5,4),intArrayOf(1,2,6,5),intArrayOf(2,3,7,6),intArrayOf(3,0,4,7),intArrayOf(4,5,6,7))
            allCorners.forEach { corners -> faces.forEachIndexed { index,face ->
                val shade=if(index==5) 1f else if(index==0) .48f else .65f+index*.04f
                val color=(base and -0x1000000) or (((base ushr 16 and 255)*shade).toInt() shl 16) or (((base ushr 8 and 255)*shade).toInt() shl 8) or ((base and 255)*shade).toInt()
                for(vertex in face){ val p=corners[vertex];builder.addVertex(p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).setColor(color) }
            } }
            val buffer=VertexBuffer(VertexBuffer.Usage.STATIC)
            buffer.bind();buffer.upload(builder.buildOrThrow());VertexBuffer.unbind()
            val corners=allCorners.flatten()
            Mesh(buffer,floatArrayOf(corners.minOf{it.x}.toFloat(),corners.minOf{it.y}.toFloat(),corners.minOf{it.z}.toFloat(),corners.maxOf{it.x}.toFloat(),corners.maxOf{it.y}.toFloat(),corners.maxOf{it.z}.toFloat()))
        }
        val blockNodes=scene.nodes.filterIsInstance<SceneBlockModelNode>().filter { it.visible }
        if(blockNodes.isEmpty()) return colored
        val builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY)
        val poses=PoseStack()
        val bounds=ArrayList<SceneVec3>(blockNodes.size*8)
        blockNodes.forEach { node ->
            val asset=blockAsset(node.blockId) ?: return@forEach
            poses.pushPose()
            try { applyTransform(poses,node.transform);renderBlock(poses,asset,builder) }
            finally { poses.popPose() }
            for(x in listOf(-.5,.5)) for(y in listOf(-.5,.5)) for(z in listOf(0.0,1.0)) bounds+=node.transform.apply(SceneVec3(x,y,z))
        }
        val data=builder.build() ?: return colored
        val buffer=VertexBuffer(VertexBuffer.Usage.STATIC)
        buffer.bind();buffer.upload(data);VertexBuffer.unbind()
        return colored+Mesh(buffer,floatArrayOf(bounds.minOf { it.x }.toFloat(),bounds.minOf { it.y }.toFloat(),bounds.minOf { it.z }.toFloat(),bounds.maxOf { it.x }.toFloat(),bounds.maxOf { it.y }.toFloat(),bounds.maxOf { it.z }.toFloat()),true)
    }

    private class HostState {
        val projection=Matrix4f(RenderSystem.getProjectionMatrix())
        val sorting=RenderSystem.getVertexSorting()
        val framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val viewport=IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT,it) }
        val scissor=IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,it) }
        val scissored=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val cull=GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend=GL11.glIsEnabled(GL11.GL_BLEND)
        val blendSrc=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val blendDst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val blendSrcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val blendDstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val fogStart=RenderSystem.getShaderFogStart();val fogEnd=RenderSystem.getShaderFogEnd()
        val color=RenderSystem.getShaderColor().copyOf()
        val shader=RenderSystem.getShader()
        val textures=IntArray(3) { RenderSystem.getShaderTexture(it) }
        fun restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer)
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3])
            if(scissored) RenderSystem.enableScissor(scissor[0],scissor[1],scissor[2],scissor[3]) else RenderSystem.disableScissor()
            RenderSystem.setProjectionMatrix(projection,sorting)
            RenderSystem.depthMask(depthMask);RenderSystem.depthFunc(depthFunc)
            if(depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if(cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if(blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            RenderSystem.blendFuncSeparate(blendSrc,blendDst,blendSrcAlpha,blendDstAlpha)
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd)
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3])
            if(shader != null) RenderSystem.setShader { shader }
            textures.forEachIndexed { unit,id -> RenderSystem.setShaderTexture(unit,id) }
        }
    }
}
