package io.github.aristheg201.svhub.client.render

import io.github.aristheg201.svhub.content.HubAsset
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

/** Horizontal sprite-strip renderer used instead of runtime GIF decoding. */
object AnimatedAssetRenderer {
    fun render(
        gui: GuiGraphics,
        asset: HubAsset,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        frames: Int,
        fps: Int,
        tick: Long
    ): Boolean {
        val resource = asset.resource?.let(ResourceLocation::tryParse) ?: return false
        val frameCount = frames.coerceIn(1, 120)
        val textureWidth = asset.width.takeIf { it > 0 } ?: return false
        val textureHeight = asset.height.takeIf { it > 0 } ?: return false
        val frameWidth = textureWidth / frameCount
        if (frameWidth <= 0) return false
        val safeFps = fps.coerceIn(1, 60)
        val frame = ((tick * safeFps / 20L) % frameCount).toInt()
        val u = (frame * frameWidth).toFloat()
        return runCatching {
            gui.blit(resource, x, y, width, height, u, 0f, frameWidth, textureHeight, textureWidth, textureHeight)
            true
        }.getOrDefault(false)
    }
}
