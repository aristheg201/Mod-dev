package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.CommandViewProvider
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

object CommandDetailView {
    fun render(gui: GuiGraphics, font: Font, route: String, theme: HubTheme, width: Int, height: Int): Boolean {
        val command = CommandViewProvider.resolveRoute(route) ?: return false
        val panelWidth = minOf(620, width - 48)
        val left = (width - panelWidth) / 2
        val top = 58
        PixelUi.panel(gui, left, top, panelWidth, height - top - 24, theme.palette.panel, theme.palette.accent2)
        gui.drawString(font, "/${command.name}", left + 18, top + 18, theme.palette.accent, true)
        gui.drawString(font, "Lệnh được server sync cho client hiện tại", left + 18, top + 38, theme.palette.mutedText, false)
        var y = top + 68
        command.syntaxes.take(18).forEach { syntax ->
            PixelUi.panel(gui, left + 18, y, panelWidth - 36, 28, theme.palette.panelAlt, theme.palette.accent2)
            gui.drawString(font, font.plainSubstrByWidth(syntax, panelWidth - 52), left + 28, y + 10, theme.palette.text, false)
            y += 34
        }
        return true
    }
}
