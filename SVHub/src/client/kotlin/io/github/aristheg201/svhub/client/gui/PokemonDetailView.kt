package io.github.aristheg201.svhub.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.cobblemon.PokemonInfoProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt

object PokemonDetailView {
    /** Renders the sheet and returns the maximum scroll offset for the right panel. */
    fun render(
        gui: GuiGraphics,
        font: Font,
        view: PokemonView,
        theme: HubTheme,
        width: Int,
        height: Int,
        yaw: Float,
        zoom: Float,
        scrollOffset: Int
    ): Int {
        val left = modelLeft(width)
        val top = 54
        val modelWidth = modelWidth(width)
        val detailsX = left + modelWidth + 18
        val detailsWidth = maxOf(200, width - detailsX - left)
        val panelHeight = height - top - 22
        val p = theme.palette

        PixelUi.panel(gui, left, top, modelWidth, panelHeight, p.panel, p.accent)
        PixelUi.panel(gui, detailsX, top, detailsWidth, panelHeight, p.panel, p.accent)

        val species = ResourceLocation.tryParse(view.speciesId)?.let(PokemonSpecies::getByIdentifier)
        if (species == null) {
            gui.drawString(font, "Species không còn tồn tại trong registry hiện tại.", detailsX + 16, top + 18, p.danger, false)
            return 0
        }
        val form = species.getForm(view.aspects)
        val info = PokemonInfoProvider.resolve(view)

        // Left: model + identity. Keep this visually stable while right-side data scrolls.
        gui.fill(left, top, left + modelWidth, top + 5, p.accent)
        MiniMessageText.drawCentered(gui, font, "<bold>${view.displayName}</bold>", left + modelWidth / 2, top + 13, p.text)
        val dex = if (view.dexNumber > 0) "#${view.dexNumber}" else "CUSTOM"
        gui.drawCenteredString(font, dex, left + modelWidth / 2, top + 29, p.accent2)

        val modelSize = minOf(modelWidth - 26, panelHeight - 128).coerceAtLeast(80)
        PokemonModelRenderer.render(gui, view, left + modelWidth / 2, top + 48 + modelSize / 2, modelSize, yaw, zoom)
        renderTypeChips(gui, font, form.types.map { it.name.replaceFirstChar(Char::uppercase) }, left + 12, top + 54 + modelSize, modelWidth - 24, theme)
        gui.drawCenteredString(font, "Kéo: xoay  ·  Cuộn: zoom", left + modelWidth / 2, top + panelHeight - 25, p.mutedText)

        val innerX = detailsX + 16
        val innerWidth = detailsWidth - 32
        val viewportTop = top + 8
        val viewportBottom = top + panelHeight - 8
        val clampedScroll = scrollOffset.coerceAtLeast(0)

        gui.enableScissor(detailsX + 2, top + 2, detailsX + detailsWidth - 2, top + panelHeight - 2)
        var y = top + 14 - clampedScroll

        section(gui, font, innerX, y, innerWidth, "THÔNG TIN", theme); y += 31
        y = line(gui, font, innerX, y, innerWidth, "Species ID", view.speciesId, theme)
        if (view.aspects.isNotEmpty()) y = wrappedValue(gui, font, innerX, y, innerWidth, "Aspect", view.aspects.sorted().joinToString(", "), theme)
        y = line(gui, font, innerX, y, innerWidth, "Height", "${trim(form.height / 10f)} m", theme)
        y = line(gui, font, innerX, y, innerWidth, "Weight", "${trim(form.weight / 10f)} kg", theme)
        info?.let {
            y = line(gui, font, innerX, y, innerWidth, "Catch Rate", it.catchRate.toString(), theme)
            y = line(gui, font, innerX, y, innerWidth, "Gender", it.gender, theme)
            y = line(gui, font, innerX, y, innerWidth, "Egg Group", it.eggGroups.ifEmpty { listOf("—") }.joinToString(" / "), theme)
            y = line(gui, font, innerX, y, innerWidth, "EXP Group", it.experienceGroup, theme)
            y = line(gui, font, innerX, y, innerWidth, "Base EXP", it.baseExperience.toString(), theme)
            y = line(gui, font, innerX, y, innerWidth, "Friendship", it.baseFriendship.toString(), theme)
        }
        y += 8

        info?.let {
            section(gui, font, innerX, y, innerWidth, "ABILITY", theme); y += 31
            y = line(gui, font, innerX, y, innerWidth, "Ability", it.abilities.ifEmpty { listOf("—") }.joinToString(" / "), theme)
            y = line(gui, font, innerX, y, innerWidth, "Hidden", it.hiddenAbilities.ifEmpty { listOf("—") }.joinToString(" / "), theme)
            y += 8
        }

        section(gui, font, innerX, y, innerWidth, "BASE STATS", theme); y += 31
        form.baseStats.entries.sortedBy { statOrder(it.key.showdownId) }.forEach { (stat, value) ->
            y = statLine(gui, font, innerX, y, innerWidth, stat.displayName.string, value, theme)
        }
        info?.let { y = line(gui, font, innerX, y + 2, innerWidth, "BST", it.bst.toString(), theme) }
        info?.takeIf { it.evYield.isNotEmpty() }?.let {
            y = wrappedValue(gui, font, innerX, y, innerWidth, "EV Yield", it.evYield.joinToString(" · "), theme)
        }
        y += 8

        info?.let {
            section(gui, font, innerX, y, innerWidth, "TIẾN HÓA & FORM", theme); y += 31
            y = line(gui, font, innerX, y, innerWidth, "Pre-evolution", it.preEvolution ?: "—", theme)
            y = wrappedValue(gui, font, innerX, y, innerWidth, "Evolves to", it.evolutions.ifEmpty { listOf("—") }.joinToString(", "), theme)
            if (it.forms.isNotEmpty()) y = wrappedValue(gui, font, innerX, y, innerWidth, "Forms", it.forms.joinToString(" · "), theme)
            y += 8
        }

        info?.let {
            section(gui, font, innerX, y, innerWidth, "MOVESET", theme); y += 31
            val counts = "Level ${it.levelMoves.size} · TM ${it.tmMoveCount} · Egg ${it.eggMoveCount} · Tutor ${it.tutorMoveCount} · Other ${it.otherMoveCount}"
            gui.drawString(font, counts, innerX, y, p.mutedText, false); y += 16
            it.levelMoves.take(24).forEach { move ->
                gui.fill(innerX, y + 3, innerX + 3, y + 7, p.accent2)
                gui.drawString(font, font.plainSubstrByWidth(move, innerWidth - 14), innerX + 9, y, p.text, false)
                y += 12
            }
            if (it.levelMoves.size > 24) {
                gui.drawString(font, "+ ${it.levelMoves.size - 24} level-up moves khác", innerX + 9, y, p.mutedText, false); y += 12
            }
            y += 8
        }

        info?.takeIf { it.drops.isNotEmpty() }?.let {
            section(gui, font, innerX, y, innerWidth, "DROPS", theme); y += 31
            it.drops.take(10).forEach { drop ->
                gui.fill(innerX, y + 3, innerX + 3, y + 7, p.accent)
                gui.drawString(font, font.plainSubstrByWidth(drop, innerWidth - 14), innerX + 9, y, p.text, false)
                y += 12
            }
            if (it.drops.size > 10) {
                gui.drawString(font, "+ ${it.drops.size - 10} drops khác", innerX + 9, y, p.mutedText, false); y += 12
            }
            y += 8
        }

        info?.takeIf { it.pokedex.isNotEmpty() }?.let {
            section(gui, font, innerX, y, innerWidth, "POKÉDEX", theme); y += 31
            it.pokedex.take(4).forEach { paragraph ->
                MiniMessageText.split(font, paragraph, innerWidth).take(8).forEach { text ->
                    gui.drawString(font, text, innerX, y, p.text, false)
                    y += 12
                }
                y += 5
            }
        }

        view.wikiPage?.let { wiki ->
            y += 4
            MiniMessageText.draw(gui, font, "<color:#2E7168>Server wiki:</color> $wiki", innerX, y, p.accent); y += 14
        }

        gui.disableScissor()

        val contentHeight = (y + clampedScroll) - (top + 14)
        val viewportHeight = viewportBottom - viewportTop - 16
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        if (maxScroll > 0) {
            val trackHeight = panelHeight - 16
            val barHeight = ((viewportHeight.toFloat() / contentHeight.coerceAtLeast(1)) * trackHeight).roundToInt().coerceAtLeast(24)
            val travel = trackHeight - barHeight
            val barY = top + 8 + ((clampedScroll.coerceAtMost(maxScroll).toFloat() / maxScroll) * travel).roundToInt()
            gui.fill(detailsX + detailsWidth - 7, top + 8, detailsX + detailsWidth - 4, top + panelHeight - 8, PixelUi.withAlpha(p.mutedText, 45))
            gui.fill(detailsX + detailsWidth - 7, barY, detailsX + detailsWidth - 4, barY + barHeight, p.accent)
        }
        return maxScroll
    }

    fun isModelArea(width: Int, mouseX: Double, mouseY: Double, height: Int): Boolean {
        val left = modelLeft(width)
        val top = 54
        val w = modelWidth(width)
        return mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < height - 22
    }

    private fun renderTypeChips(gui: GuiGraphics, font: Font, types: List<String>, x: Int, y: Int, width: Int, theme: HubTheme) {
        if (types.isEmpty()) return
        val gap = 5
        val total = types.sumOf { font.width(it) + 18 } + gap * (types.size - 1)
        var cx = x + ((width - total).coerceAtLeast(0) / 2)
        types.forEachIndexed { index, type ->
            val chipWidth = font.width(type) + 18
            gui.fill(cx, y, cx + chipWidth, y + 20, if (index == 0) theme.palette.accent else theme.palette.accent2)
            gui.drawCenteredString(font, type, cx + chipWidth / 2, y + 7, 0xFFFFFFFF.toInt())
            cx += chipWidth + gap
        }
    }

    private fun section(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, title: String, theme: HubTheme) {
        PixelUi.sectionBand(gui, x, y, width, 24, theme)
        gui.drawString(font, title, x + 11, y + 8, theme.palette.accent, true)
    }

    private fun line(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, label: String, value: String, theme: HubTheme): Int {
        val labelWidth = minOf(102, width / 3)
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        gui.drawString(font, font.plainSubstrByWidth(value, width - labelWidth), x + labelWidth, y, theme.palette.text, false)
        return y + 14
    }

    private fun statLine(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, label: String, value: Int, theme: HubTheme): Int {
        val labelWidth = minOf(82, width / 4)
        val valueWidth = 28
        val barX = x + labelWidth + valueWidth
        val barWidth = (width - labelWidth - valueWidth).coerceAtLeast(40)
        gui.drawString(font, label, x, y + 1, theme.palette.mutedText, false)
        gui.drawString(font, value.toString(), x + labelWidth, y + 1, theme.palette.text, true)
        gui.fill(barX, y + 3, barX + barWidth, y + 9, PixelUi.withAlpha(theme.palette.mutedText, 35))
        val fill = ((value.coerceIn(0, 255) / 255f) * barWidth).roundToInt().coerceAtLeast(if (value > 0) 2 else 0)
        gui.fill(barX, y + 3, barX + fill, y + 9, if (value >= 100) theme.palette.accent2 else theme.palette.accent)
        return y + 14
    }

    private fun wrappedValue(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, label: String, value: String, theme: HubTheme): Int {
        val labelWidth = minOf(102, width / 3)
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        var cy = y
        font.split(Component.literal(value), width - labelWidth).take(6).forEach { text ->
            gui.drawString(font, text, x + labelWidth, cy, theme.palette.text, false)
            cy += 12
        }
        return maxOf(y + 14, cy)
    }

    private fun statOrder(id: String): Int = when (id.lowercase()) {
        "hp" -> 0
        "attack" -> 1
        "defense" -> 2
        "special_attack", "specialattack", "spatk" -> 3
        "special_defense", "specialdefense", "spdef" -> 4
        "speed" -> 5
        else -> 10
    }

    private fun modelLeft(width: Int) = maxOf(18, width / 12)
    private fun modelWidth(width: Int) = minOf(260, width / 2 - 28).coerceAtLeast(150)
    private fun trim(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
}
