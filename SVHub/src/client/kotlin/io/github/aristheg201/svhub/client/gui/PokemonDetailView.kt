package io.github.aristheg201.svhub.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.cobblemon.PokemonInfoProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
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

        PixelUi.panel(gui, left, top, modelWidth, panelHeight, theme.palette.panel, theme.palette.accent2)
        PixelUi.panel(gui, detailsX, top, detailsWidth, panelHeight, theme.palette.panel, theme.palette.accent2)

        val modelSize = minOf(modelWidth - 24, panelHeight - 88).coerceAtLeast(80)
        PokemonModelRenderer.render(gui, view, left + modelWidth / 2, top + 38 + modelSize / 2, modelSize, yaw, zoom)
        gui.drawCenteredString(font, "Kéo để xoay · cuộn bên trái để zoom", left + modelWidth / 2, top + panelHeight - 25, theme.palette.mutedText)

        val species = ResourceLocation.tryParse(view.speciesId)?.let(PokemonSpecies::getByIdentifier)
        if (species == null) {
            gui.drawString(font, "Species không còn tồn tại trong registry hiện tại.", detailsX + 16, top + 18, theme.palette.danger, false)
            return 0
        }
        val form = species.getForm(view.aspects)
        val info = PokemonInfoProvider.resolve(view)
        val innerX = detailsX + 16
        val innerWidth = detailsWidth - 32
        val viewportTop = top + 8
        val viewportBottom = top + panelHeight - 8
        val clampedScroll = scrollOffset.coerceAtLeast(0)

        gui.enableScissor(detailsX + 2, top + 2, detailsX + detailsWidth - 2, top + panelHeight - 2)
        var y = top + 16 - clampedScroll

        gui.drawString(font, view.displayName, innerX, y, theme.palette.accent, true); y += 16
        val dex = if (view.dexNumber > 0) "#${view.dexNumber}" else "Custom"
        gui.drawString(font, "$dex · ${view.speciesId}", innerX, y, theme.palette.mutedText, false); y += 15
        if (view.aspects.isNotEmpty()) {
            gui.drawString(font, "Aspect: ${view.aspects.sorted().joinToString(", ")}", innerX, y, theme.palette.accent2, false); y += 15
        }

        section(gui, font, innerX, y, "THÔNG TIN CƠ BẢN", theme); y += 16
        val types = form.types.joinToString(" / ") { it.name.replaceFirstChar(Char::uppercase) }
        y = line(gui, font, innerX, y, innerWidth, "Type", types, theme)
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
        y += 6

        info?.let {
            section(gui, font, innerX, y, "ABILITY", theme); y += 16
            y = line(gui, font, innerX, y, innerWidth, "Ability", it.abilities.ifEmpty { listOf("—") }.joinToString(" / "), theme)
            y = line(gui, font, innerX, y, innerWidth, "Hidden", it.hiddenAbilities.ifEmpty { listOf("—") }.joinToString(" / "), theme)
            y += 6
        }

        section(gui, font, innerX, y, "BASE STATS", theme); y += 16
        form.baseStats.entries.sortedBy { it.key.showdownId }.forEach { (stat, value) ->
            y = line(gui, font, innerX, y, innerWidth, stat.displayName.string, value.toString(), theme)
        }
        info?.let { y = line(gui, font, innerX, y, innerWidth, "BST", it.bst.toString(), theme) }
        info?.takeIf { it.evYield.isNotEmpty() }?.let {
            y = line(gui, font, innerX, y, innerWidth, "EV Yield", it.evYield.joinToString(" · "), theme)
        }
        y += 6

        info?.let {
            section(gui, font, innerX, y, "TIẾN HÓA & FORM", theme); y += 16
            y = line(gui, font, innerX, y, innerWidth, "Pre-evolution", it.preEvolution ?: "—", theme)
            y = line(gui, font, innerX, y, innerWidth, "Evolves to", it.evolutions.ifEmpty { listOf("—") }.joinToString(", "), theme)
            if (it.forms.isNotEmpty()) {
                y = wrappedValue(gui, font, innerX, y, innerWidth, "Forms", it.forms.joinToString(" · "), theme)
            }
            y += 6
        }

        info?.let {
            section(gui, font, innerX, y, "MOVESET", theme); y += 16
            val counts = "Level ${it.levelMoves.size} · TM ${it.tmMoveCount} · Egg ${it.eggMoveCount} · Tutor ${it.tutorMoveCount} · Other ${it.otherMoveCount}"
            gui.drawString(font, counts, innerX, y, theme.palette.mutedText, false); y += 15
            it.levelMoves.take(24).forEach { move ->
                gui.drawString(font, font.plainSubstrByWidth(move, innerWidth - 10), innerX + 6, y, theme.palette.text, false)
                y += 12
            }
            if (it.levelMoves.size > 24) {
                gui.drawString(font, "+ ${it.levelMoves.size - 24} level-up moves khác", innerX + 6, y, theme.palette.mutedText, false); y += 12
            }
            y += 6
        }

        info?.takeIf { it.drops.isNotEmpty() }?.let {
            section(gui, font, innerX, y, "DROPS", theme); y += 16
            it.drops.take(10).forEach { drop ->
                gui.drawString(font, font.plainSubstrByWidth(drop, innerWidth - 10), innerX + 6, y, theme.palette.text, false)
                y += 12
            }
            if (it.drops.size > 10) {
                gui.drawString(font, "+ ${it.drops.size - 10} drops khác", innerX + 6, y, theme.palette.mutedText, false); y += 12
            }
            y += 6
        }

        info?.takeIf { it.pokedex.isNotEmpty() }?.let {
            section(gui, font, innerX, y, "POKÉDEX", theme); y += 16
            it.pokedex.take(4).forEach { paragraph ->
                font.split(Component.literal(paragraph), innerWidth).take(8).forEach { text ->
                    gui.drawString(font, text, innerX, y, theme.palette.text, false)
                    y += 12
                }
                y += 4
            }
        }

        view.wikiPage?.let { wiki ->
            y += 4
            gui.drawString(font, "Server wiki: $wiki", innerX, y, theme.palette.accent2, false); y += 14
        }

        gui.disableScissor()

        val contentHeight = (y + clampedScroll) - (top + 16)
        val viewportHeight = viewportBottom - viewportTop - 16
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        if (maxScroll > 0) {
            val trackHeight = panelHeight - 16
            val barHeight = ((viewportHeight.toFloat() / contentHeight.coerceAtLeast(1)) * trackHeight).roundToInt().coerceAtLeast(24)
            val travel = trackHeight - barHeight
            val barY = top + 8 + ((clampedScroll.coerceAtMost(maxScroll).toFloat() / maxScroll) * travel).roundToInt()
            gui.fill(detailsX + detailsWidth - 7, top + 8, detailsX + detailsWidth - 4, top + panelHeight - 8, 0x4438454E)
            gui.fill(detailsX + detailsWidth - 7, barY, detailsX + detailsWidth - 4, barY + barHeight, theme.palette.accent2)
        }
        return maxScroll
    }

    fun isModelArea(width: Int, mouseX: Double, mouseY: Double, height: Int): Boolean {
        val left = modelLeft(width)
        val top = 54
        val w = modelWidth(width)
        return mouseX >= left && mouseX < left + w && mouseY >= top && mouseY < height - 22
    }

    private fun modelLeft(width: Int) = maxOf(18, width / 12)
    private fun modelWidth(width: Int) = minOf(260, width / 2 - 28).coerceAtLeast(150)

    private fun section(gui: GuiGraphics, font: Font, x: Int, y: Int, title: String, theme: HubTheme) {
        gui.drawString(font, title, x, y, theme.palette.accent, true)
    }

    private fun line(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, label: String, value: String, theme: HubTheme): Int {
        val labelWidth = minOf(96, width / 3)
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        gui.drawString(font, font.plainSubstrByWidth(value, width - labelWidth), x + labelWidth, y, theme.palette.text, false)
        return y + 13
    }

    private fun wrappedValue(gui: GuiGraphics, font: Font, x: Int, y: Int, width: Int, label: String, value: String, theme: HubTheme): Int {
        val labelWidth = minOf(96, width / 3)
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        var cy = y
        font.split(Component.literal(value), width - labelWidth).take(6).forEach { text ->
            gui.drawString(font, text, x + labelWidth, cy, theme.palette.text, false)
            cy += 12
        }
        return maxOf(y + 13, cy)
    }

    private fun trim(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
}
