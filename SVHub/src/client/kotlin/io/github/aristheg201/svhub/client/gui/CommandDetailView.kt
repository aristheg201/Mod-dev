package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.CommandViewProvider
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

object CommandDetailView {
    fun render(gui: GuiGraphics, font: Font, route: String, theme: HubTheme, width: Int, height: Int): Boolean {
        val command = CommandViewProvider.resolveRoute(route) ?: return false
        val panelWidth = minOf(660, width - 48)
        val left = (width - panelWidth) / 2
        val top = 58
        val panelHeight = height - top - 24
        val p = theme.palette

        PixelUi.panel(gui, left, top, panelWidth, panelHeight, p.panel, p.accent)
        gui.fill(left, top, left + 6, top + panelHeight, p.accent)
        MiniMessageText.draw(gui, font, "<bold><color:#2E7168>LỆNH NGƯỜI CHƠI</color></bold>", left + 18, top + 16, p.accent)
        gui.drawString(font, Component.literal("/${command.name}"), left + 18, top + 37, p.accent2, true)
        gui.drawString(font, "Cú pháp khả dụng trên client hiện tại", left + 18, top + 54, p.mutedText, false)

        var y = top + 78
        command.syntaxes.take(18).forEachIndexed { index, syntax ->
            val rowHeight = 31
            val fill = if (index % 2 == 0) p.panelAlt else p.panel
            PixelUi.panel(gui, left + 18, y, panelWidth - 36, rowHeight, fill, PixelUi.withAlpha(p.accent, 75))
            gui.fill(left + 18, y, left + 22, y + rowHeight, p.accent2)
            gui.drawString(font, Component.literal(syntax), left + 31, y + 11, p.text, false)
            y += rowHeight + 6
        }
        return true
    }
}
