package io.github.aristheg201.svhub.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

object PokemonDetailView {
    fun render(
        gui: GuiGraphics,
        font: Font,
        view: PokemonView,
        theme: HubTheme,
        width: Int,
        height: Int,
        yaw: Float,
        zoom: Float
    ) {
        val left = maxOf(18, width / 12)
        val top = 54
        val modelWidth = minOf(260, width / 2 - 28)
        val detailsX = left + modelWidth + 18
        val detailsWidth = maxOf(180, width - detailsX - left)
        val panelHeight = height - top - 22

        PixelUi.panel(gui, left, top, modelWidth, panelHeight, theme.palette.panel, theme.palette.accent2)
        PixelUi.panel(gui, detailsX, top, detailsWidth, panelHeight, theme.palette.panel, theme.palette.accent2)

        val modelSize = minOf(modelWidth - 28, panelHeight - 90).coerceAtLeast(80)
        PokemonModelRenderer.render(gui, view, left + modelWidth / 2, top + 42 + modelSize / 2, modelSize, yaw, zoom)
        gui.drawCenteredString(font, "Kéo để xoay • Cuộn để zoom", left + modelWidth / 2, top + panelHeight - 28, theme.palette.mutedText)

        gui.drawString(font, view.displayName, detailsX + 18, top + 18, theme.palette.accent, true)
        val dex = if (view.dexNumber > 0) "#${view.dexNumber}" else "Custom"
        gui.drawString(font, "$dex • ${view.speciesId}", detailsX + 18, top + 36, theme.palette.mutedText, false)
        if (view.aspects.isNotEmpty()) {
            gui.drawString(font, "Aspect: ${view.aspects.sorted().joinToString(", ")}", detailsX + 18, top + 52, theme.palette.accent2, false)
        }

        val species = ResourceLocation.tryParse(view.speciesId)?.let(PokemonSpecies::getByIdentifier)
        if (species == null) {
            gui.drawString(font, "Species không còn tồn tại trong registry hiện tại.", detailsX + 18, top + 82, theme.palette.danger, false)
            return
        }

        val form = species.getForm(view.aspects)
        val types = form.types.joinToString(" / ") { it.name.replaceFirstChar(Char::uppercase) }
        var y = top + 82
        detailLine(gui, font, detailsX + 18, y, "Type", types, theme); y += 18
        detailLine(gui, font, detailsX + 18, y, "Height", "${form.height / 10f} m", theme); y += 18
        detailLine(gui, font, detailsX + 18, y, "Weight", "${form.weight / 10f} kg", theme); y += 26

        gui.drawString(font, "BASE STATS", detailsX + 18, y, theme.palette.accent, true); y += 17
        form.baseStats.entries
            .sortedBy { it.key.showdownId }
            .take(8)
            .forEach { (stat, value) ->
                detailLine(gui, font, detailsX + 18, y, stat.displayName.string, value.toString(), theme)
                y += 16
            }

        val description = species.pokedex.firstOrNull().orEmpty()
        if (description.isNotBlank() && y < top + panelHeight - 55) {
            y += 8
            gui.drawString(font, "POKÉDEX", detailsX + 18, y, theme.palette.accent, true); y += 15
            font.split(net.minecraft.network.chat.Component.literal(description), detailsWidth - 36).take(5).forEach { line ->
                gui.drawString(font, line, detailsX + 18, y, theme.palette.text, false)
                y += 12
            }
        }

        view.wikiPage?.let { wiki ->
            gui.drawString(font, "Server wiki: $wiki", detailsX + 18, top + panelHeight - 24, theme.palette.accent2, false)
        }
    }

    private fun detailLine(gui: GuiGraphics, font: Font, x: Int, y: Int, label: String, value: String, theme: HubTheme) {
        gui.drawString(font, "$label:", x, y, theme.palette.mutedText, false)
        gui.drawString(font, value, x + 88, y, theme.palette.text, false)
    }
}
