package io.github.aristheg201.svhub.client.nativeui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import java.util.concurrent.ConcurrentHashMap

object VanillaCompanionModelRenderer {
    private data class Cached(val level: ClientLevel, val entity: LivingEntity)
    private val cache = ConcurrentHashMap<String, Cached>()

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
        val cached = cache[entityId]
        val entity = if (cached != null && cached.level === level && !cached.entity.isRemoved) {
            cached.entity
        } else {
            val id = ResourceLocation.withDefaultNamespace(entityId)
            val type = BuiltInRegistries.ENTITY_TYPE.get(id)
            val created = type.create(level) as? LivingEntity ?: return false
            cache[entityId] = Cached(level, created)
            created
        }

        val width = x2 - x1
        val height = y2 - y1
        val scale = (minOf(width, height) * 0.72f).toInt().coerceIn(24, 110)
        return runCatching {
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                gui,
                x1,
                y1,
                x2,
                y2,
                scale,
                0f,
                if (facingLeft) 18f else -18f,
                0f,
                entity
            )
            true
        }.getOrDefault(false)
    }

    fun clear() {
        cache.clear()
    }
}
