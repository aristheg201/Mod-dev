package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

object HubSearchOverlay {
    fun render(
        gui: GuiGraphics,
        font: Font,
        theme: HubTheme,
        query: String,
        width: Int,
        top: Int,
        maxRows: Int,
        onRoute: (String) -> Unit,
        hits: MutableList<HubHitTarget>
    ) {
        val results = ClientHubState.search(query, maxRows)
        val panelWidth = minOf(520, width - 32)
        val left = (width - panelWidth) / 2
        var y = top
        if (results.isEmpty()) {
            PixelUi.panel(gui, left, y, panelWidth, 34, theme.palette.panel, theme.palette.accent2)
            gui.drawCenteredString(font, "Không tìm thấy kết quả", width / 2, y + 13, theme.palette.mutedText)
            return
        }
        results.forEach { hit ->
            val rowHeight = 40
            PixelUi.panel(gui, left, y, panelWidth, rowHeight, theme.palette.panel, theme.palette.accent2)
            gui.drawString(font, font.plainSubstrByWidth(hit.title, panelWidth - 150), left + 12, y + 8, theme.palette.text, true)
            val secondary = listOf(hit.category, hit.subtitle).filter(String::isNotBlank).joinToString(" • ")
            gui.drawString(font, font.plainSubstrByWidth(secondary, panelWidth - 24), left + 12, y + 23, theme.palette.mutedText, false)
            if (hit.source != "hub") {
                val badge = hit.source.take(14)
                gui.drawString(font, badge, left + panelWidth - font.width(badge) - 10, y + 8, theme.palette.accent, false)
            }
            hits += HubHitTarget(left, y, left + panelWidth, y + rowHeight) { onRoute(hit.route) }
            y += rowHeight + 6
        }
    }
}
