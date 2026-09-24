package io.github.aristheg201.svarcade.client.nativeui

import io.github.aristheg201.svarcade.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/** Shared control paint for interactive screens and their visual acceptance captures. */
object NativeControlRenderer {
    fun draw(gui: GuiGraphics, font: Font, rect: UiRect, label: String, mouseX: Int, mouseY: Int,
             icon: String? = null, active: Boolean = false, enabled: Boolean = true) {
        if (rect.width <= 0 || rect.height <= 0) return
        val hovered = enabled && rect.contains(mouseX.toDouble(), mouseY.toDouble())
        val fill = when {
            !enabled -> 0xFF141C1E.toInt()
            active -> 0xFF21443E.toInt()
            hovered -> 0xFF213338.toInt()
            else -> 0xFF18272B.toInt()
        }
        val border = if (active) 0xFF4CC7B2.toInt() else if (hovered) 0xFFE2BE62.toInt() else 0xFF2A3B3F.toInt()
        gui.enableScissor(rect.x, rect.y, rect.right, rect.bottom)
        try {
            gui.fill(rect.x, rect.y, rect.right, rect.bottom, fill)
            gui.fill(rect.x, rect.y, rect.x + 3, rect.bottom, border)
            icon?.let { NativePixelArt.icon(gui, it, rect.x + 6, rect.y + (rect.height - 16) / 2, 16,
                if (active) 0xFF4CC7B2.toInt() else 0xFF92A5A1.toInt()) }
            val textX = rect.x + if (icon == null) 7 else 27
            val fitted = font.plainSubstrByWidth(label, (rect.right - textX - 5).coerceAtLeast(0))
            gui.drawString(font, fitted, textX, rect.y + (rect.height - 8) / 2,
                if (enabled) 0xFFF1F5F3.toInt() else 0xFF92A5A1.toInt(), false)
        } finally { gui.disableScissor() }
    }
}
