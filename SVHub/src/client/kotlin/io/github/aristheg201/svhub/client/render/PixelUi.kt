package io.github.aristheg201.svhub.client.render

import io.github.aristheg201.svhub.content.HubAsset
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.abs

object PixelUi {
    fun background(gui: GuiGraphics, width: Int, height: Int, content: HubContent, theme: HubTheme?, tick: Long) {
        val palette = theme?.palette ?: return gui.fill(0, 0, width, height, 0xFF10131A.toInt())
        gui.fill(0, 0, width, height, palette.background)

        val asset = theme.backgroundAsset?.let { content.assets[it] }
        val resource = asset?.resource?.let(ResourceLocation::tryParse)
        val rendered = when {
            resource != null && asset != null -> runCatching {
                val tw = asset.width.takeIf { it > 0 } ?: 512
                val th = asset.height.takeIf { it > 0 } ?: 288
                gui.blit(resource, 0, 0, width, height, 0f, 0f, tw, th, tw, th)
                true
            }.getOrDefault(false)
            asset != null && asset.source == "generated" -> GeneratedBackgroundRenderer.render(
                gui = gui,
                asset = asset,
                x = 0,
                y = 0,
                width = width,
                height = height,
                baseColor = palette.panelAlt,
                accentColor = palette.accent2
            )
            else -> false
        }

        if (!rendered) procedural(gui, width, height, theme.backgroundPreset, palette.panelAlt, tick, theme.motionStrength)
        proceduralOverlay(gui, width, height, theme.backgroundPreset, palette.accent2, tick, theme.motionStrength)
        gui.fill(0, 0, width, height, 0x25000000)
    }

    fun image(gui: GuiGraphics, asset: HubAsset, x: Int, y: Int, width: Int, height: Int): Boolean {
        val resource = asset.resource?.let(ResourceLocation::tryParse) ?: return false
        val tw = asset.width.takeIf { it > 0 } ?: width
        val th = asset.height.takeIf { it > 0 } ?: height
        return runCatching {
            gui.blit(resource, x, y, width, height, 0f, 0f, tw, th, tw, th)
            true
        }.getOrDefault(false)
    }

    fun panel(gui: GuiGraphics, x: Int, y: Int, width: Int, height: Int, color: Int, border: Int) {
        gui.fill(x, y, x + width, y + height, color)
        gui.fill(x, y, x + width, y + 2, border)
        gui.fill(x, y + height - 2, x + width, y + height, darken(border, .55f))
        gui.fill(x, y, x + 2, y + height, border)
        gui.fill(x + width - 2, y, x + width, y + height, darken(border, .55f))
        gui.fill(x + 3, y + 3, x + width - 3, y + 4, withAlpha(border, 45))
    }

    fun button(gui: GuiGraphics, x: Int, y: Int, width: Int, height: Int, hovered: Boolean, theme: HubTheme) {
        val p = theme.palette
        panel(gui, x, y, width, height, if (hovered) p.panelAlt else p.panel, if (hovered) p.accent else p.accent2)
        if (hovered) gui.fill(x + 3, y + height - 4, x + width - 3, y + height - 2, p.accent)
    }

    private fun procedural(gui: GuiGraphics, w: Int, h: Int, preset: String, color: Int, tick: Long, motion: Float) {
        when (preset) {
            "pixel_grid", "pixel_neon" -> drawGrid(gui, w, h, color, tick, motion, preset == "pixel_neon")
            "pixel_forest" -> drawForest(gui, w, h, color)
            "pixel_cave" -> drawCave(gui, w, h, color)
            "pixel_volcano" -> drawVolcano(gui, w, h, color, tick)
            else -> drawSky(gui, w, h, color, tick, motion)
        }
    }

    private fun proceduralOverlay(gui: GuiGraphics, w: Int, h: Int, preset: String, color: Int, tick: Long, motion: Float) {
        if (preset == "pixel_neon") drawGrid(gui, w, h, color, tick, motion * .5f, true)
        for (i in 0 until 18) {
            val x = abs((i * 113 + (tick * motion).toInt() * (i % 3 + 1)) % (w + 16)) - 8
            val y = abs((i * 67 + (tick / 4).toInt()) % (h + 16)) - 8
            gui.fill(x, y, x + 2, y + 2, withAlpha(color, 24 + (i % 3) * 12))
        }
    }

    private fun drawGrid(gui: GuiGraphics, w: Int, h: Int, color: Int, tick: Long, motion: Float, neon: Boolean) {
        val step = 24
        val drift = ((tick * motion).toInt() % step)
        var x = -step + drift
        while (x < w) {
            gui.fill(x, 0, x + 1, h, withAlpha(color, if (neon) 72 else 45))
            x += step
        }
        var y = -step + drift / 2
        while (y < h) {
            gui.fill(0, y, w, y + 1, withAlpha(color, if (neon) 72 else 45))
            y += step
        }
    }

    private fun drawSky(gui: GuiGraphics, w: Int, h: Int, color: Int, tick: Long, motion: Float) {
        val drift = (tick * motion).toInt()
        for (i in 0 until 32) {
            val x = abs((i * 97 + drift * (i % 3 + 1)) % (w + 30)) - 15
            val y = abs((i * 53 + (tick / 3).toInt()) % (h + 20)) - 10
            val s = if (i % 5 == 0) 3 else 2
            gui.fill(x, y, x + s, y + s, withAlpha(color, 38 + (i % 4) * 12))
        }
    }

    private fun drawForest(gui: GuiGraphics, w: Int, h: Int, color: Int) {
        val ground = h * 3 / 4
        gui.fill(0, ground, w, h, withAlpha(color, 62))
        var x = 0
        while (x < w) {
            val ht = 18 + (x / 13 % 4) * 10
            gui.fill(x, ground - ht, x + 7, ground, withAlpha(color, 56))
            gui.fill(x - 5, ground - ht + 6, x + 12, ground - ht + 12, withAlpha(color, 48))
            x += 34
        }
    }

    private fun drawCave(gui: GuiGraphics, w: Int, h: Int, color: Int) {
        var x = 0
        while (x < w) {
            val ht = 12 + (x / 17 % 5) * 8
            gui.fill(x, 0, x + 18, ht, withAlpha(color, 48))
            gui.fill(x, h - ht, x + 18, h, withAlpha(color, 48))
            x += 18
        }
    }

    private fun drawVolcano(gui: GuiGraphics, w: Int, h: Int, color: Int, tick: Long) {
        val baseY = h - 18
        for (i in 0 until 10) {
            val cx = w * (i + 1) / 11
            val ht = 22 + (i % 4) * 17
            gui.fill(cx - 18, baseY - ht / 2, cx + 18, baseY, withAlpha(color, 44))
            gui.fill(cx - 10, baseY - ht, cx + 10, baseY, withAlpha(color, 52))
        }
        for (i in 0 until 14) {
            val x = abs((i * 79 + tick.toInt()) % maxOf(1, w))
            val y = h - abs((i * 31 + tick.toInt() * 2) % maxOf(1, h / 2))
            gui.fill(x, y, x + 2, y + 4, 0x55FF8A35)
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun darken(color: Int, factor: Float): Int {
        val a = color ushr 24 and 0xFF
        val r = ((color ushr 16 and 0xFF) * factor).toInt()
        val g = ((color ushr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
