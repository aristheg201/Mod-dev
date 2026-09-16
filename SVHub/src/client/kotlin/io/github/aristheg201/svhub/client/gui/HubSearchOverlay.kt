package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.render.MiniMessageText
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
        val panelWidth = minOf(560, width - 32)
        val left = (width - panelWidth) / 2
        var y = top
        if (results.isEmpty()) {
            PixelUi.panel(gui, left, y, panelWidth, 38, theme.palette.panel, theme.palette.accent2)
            MiniMessageText.drawCentered(gui, font, "<color:#66716D>Không tìm thấy kết quả</color>", width / 2, y + 14, theme.palette.mutedText)
            return
        }

        results.forEachIndexed { index, hit ->
            val rowHeight = 44
            val fill = if (index % 2 == 0) theme.palette.panel else PixelUi.withAlpha(theme.palette.panelAlt, 235)
            PixelUi.panel(gui, left, y, panelWidth, rowHeight, fill, PixelUi.withAlpha(theme.palette.accent, 95))
            gui.fill(left, y, left + 4, y + rowHeight, if (hit.source == "hub") theme.palette.accent else theme.palette.accent2)

            val titleLine = font.split(MiniMessageText.component(hit.title), panelWidth - 160).firstOrNull()
            if (titleLine != null) gui.drawString(font, titleLine, left + 12, y + 9, theme.palette.text, true)

            val secondary = listOf(hit.category, hit.subtitle).filter(String::isNotBlank).joinToString(" · ")
            val secondaryLine = font.split(MiniMessageText.component(secondary), panelWidth - 28).firstOrNull()
            if (secondaryLine != null) gui.drawString(font, secondaryLine, left + 12, y + 26, theme.palette.mutedText, false)

            if (hit.source != "hub") {
                val badge = hit.source.take(14).uppercase()
                val badgeWidth = font.width(badge) + 12
                val badgeX = left + panelWidth - badgeWidth - 9
                gui.fill(badgeX, y + 7, badgeX + badgeWidth, y + 21, PixelUi.withAlpha(theme.palette.panelAlt, 245))
                gui.fill(badgeX, y + 7, badgeX + 3, y + 21, theme.palette.accent2)
                gui.drawString(font, badge, badgeX + 7, y + 10, theme.palette.accent, false)
            }

            hits += HubHitTarget(left, y, left + panelWidth, y + rowHeight) { onRoute(hit.route) }
            y += rowHeight + 5
        }
    }
}
