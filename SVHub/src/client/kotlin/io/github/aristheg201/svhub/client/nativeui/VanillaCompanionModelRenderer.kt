package io.github.aristheg201.svhub.client.nativeui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ConcurrentHashMap
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.LightTexture
import io.github.aristheg201.svhub.client.cobblemon.SceneModelBounds
import io.github.aristheg201.svhub.ui.SceneActorFit
import io.github.aristheg201.svhub.ui.SceneActorSizing

object VanillaCompanionModelRenderer {
    private data class Cached(val level: ClientLevel, val entity: LivingEntity,
                              val startedAt:Long=System.nanoTime(),var tick:Int=0,var fit:SceneActorFit?=null)
    private val cache = ConcurrentHashMap<String, Cached>()

    /** Detached provider object supplies a model; it is never added to or ticked in a Level. */
    fun renderEmbedded(instanceId:String,entityId:String,animation:String,poses:PoseStack,buffers:MultiBufferSource):Boolean {
        val minecraft=Minecraft.getInstance()
        val level=minecraft.level ?: return false
        val id=resolveEntityId(entityId) ?: return false
        val key="scene:$instanceId:$id"
        val existing=cache[key]
        val cached=if(existing?.level === level) existing else {
            val created=BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)?.create(level) as? LivingEntity ?: return false
            Cached(level,created).also { cache[key]=it }
        }
        val entity=cached.entity
        val ticks=(System.nanoTime()-cached.startedAt)/50_000_000.0
        entity.tickCount=ticks.toInt()
        val moving=animation in setOf("WALK","RUN","CAROUSEL_MOVEMENT")
        repeat((entity.tickCount-cached.tick).coerceIn(0,5)) { entity.walkAnimation.update(if(moving) .35f else 0f,.25f) }
        cached.tick=entity.tickCount
        val renderer=minecraft.entityRenderDispatcher.getRenderer(entity)
        val fit=cached.fit ?: run {
            val capture=SceneModelBounds()
            val measure=PoseStack().also { it.mulPose(Axis.XP.rotationDegrees(90f)) }
            renderer.render(entity,0f,0f,measure,MultiBufferSource { capture },LightTexture.FULL_BRIGHT)
            SceneActorSizing(.75,.9,1.5).fit(capture.bounds()).also { cached.fit=it }
        }
        poses.pushPose()
        try {
            poses.translate(fit.offset.x,fit.offset.y,fit.offset.z)
            poses.scale(fit.scale.toFloat(),fit.scale.toFloat(),fit.scale.toFloat())
            poses.mulPose(Axis.XP.rotationDegrees(90f))
            renderer.render(entity,0f,(ticks%1).toFloat(),poses,buffers,LightTexture.FULL_BRIGHT)
        } finally { poses.popPose() }
        return true
    }

    fun render(
        gui: GuiGraphics,
        entityId: String,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        facingLeft: Boolean
    ): Boolean {
        if (x2 <= x1 || y2 <= y1) return false
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false
        val id = resolveEntityId(entityId) ?: return false
        val cacheKey = id.toString()
        val cached = cache[cacheKey]
        val entity = if (cached != null && cached.level === level && !cached.entity.isRemoved) {
            cached.entity
        } else {
            val type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null) ?: return false
            val created = runCatching { type.create(level) as? LivingEntity }.getOrNull() ?: return false
            cache[cacheKey] = Cached(level, created)
            created
        }

        val width = x2 - x1
        val height = y2 - y1
        val scale = (minOf(width, height) * 0.72f).toInt().coerceIn(24, 110)
        return runCatching {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                gui,
                x1,
                y1,
                x2,
                y2,
                scale,
                1.0f,
                if (facingLeft) 18f else -18f,
                0f,
                entity
            )
            true
        }.getOrDefault(false)
    }

    fun resolveEntityId(raw: String): ResourceLocation? {
        val normalized = raw.trim().lowercase()
        if (normalized.isBlank()) return null
        return runCatching {
            if (':' in normalized) ResourceLocation.tryParse(normalized)
            else ResourceLocation.fromNamespaceAndPath("minecraft", normalized)
        }.getOrNull()
    }

    fun clear() {
        cache.clear()
    }
}
