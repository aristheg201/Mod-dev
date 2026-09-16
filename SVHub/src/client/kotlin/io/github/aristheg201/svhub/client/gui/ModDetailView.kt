package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.EnvironmentViewProvider
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ModDetailView {
    fun render(gui: GuiGraphics, font: Font, route: String, theme: HubTheme, width: Int, height: Int): Boolean {
        if (!route.startsWith("mod/")) return false
        val id = URLDecoder.decode(route.removePrefix("mod/"), StandardCharsets.UTF_8)
        val mod = EnvironmentViewProvider.byId(id) ?: return false
        val panelWidth = minOf(560, width - 48)
        val left = (width - panelWidth) / 2
        val top = 62
        PixelUi.panel(gui, left, top, panelWidth, minOf(260, height - top - 28), theme.palette.panel, theme.palette.accent2)
        gui.drawString(font, mod.name, left + 20, top + 20, theme.palette.accent, true)
        gui.drawString(font, mod.id, left + 20, top + 40, theme.palette.mutedText, false)
        line(gui, font, left + 20, top + 78, "Hiện diện", mod.sideLabel, theme)
        line(gui, font, left + 20, top + 100, "Server version", mod.serverVersion ?: "—", theme)
        line(gui, font, left + 20, top + 122, "Client version", mod.clientVersion ?: "—", theme)
        gui.drawString(font, "SVHub tách server/client capability; không giả định mod tồn tại ở cả hai phía.", left + 20, top + 158, theme.palette.text, false)
        return true
    }

    private fun line(gui: GuiGraphics, font: Font, x: Int, y: Int, label: String, value: String, theme: HubTheme) {
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        gui.drawString(font, value, x + 118, y, theme.palette.text, false)
    }
}
