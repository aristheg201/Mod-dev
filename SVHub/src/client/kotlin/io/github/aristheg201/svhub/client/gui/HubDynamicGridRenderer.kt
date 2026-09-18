package io.github.aristheg201.svhub.client.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.CommandViewProvider
import io.github.aristheg201.svhub.client.api.SVHubClientApi
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubComponent
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

object HubDynamicGridRenderer {
    data class Result(val height: Int, val pageCount: Int, val normalizedPage: Int)
    private data class Entry(val title: String, val subtitle: String, val route: String, val pokemon: PokemonView? = null)

    fun render(
        gui: GuiGraphics,
        font: Font,
        content: HubContent,
        component: HubComponent,
        theme: HubTheme,
        x: Int,
        y: Int,
        width: Int,
        page: Int,
        mouseX: Int,
        mouseY: Int,
        onRoute: (String) -> Unit,
        onPage: (Int) -> Unit,
        hits: MutableList<HubHitTarget>
    ): Result {
        val provider = component.props.get("provider")?.asString.orEmpty()
        val pokemonGrid = provider == "cobblemon:pokemon" || provider == "cobblemon:fakemon"
        val requestedPageSize = component.props.get("pageSize")?.let { runCatching { it.asInt }.getOrNull() } ?: 24
        val pageSize = requestedPageSize.coerceIn(6, if (pokemonGrid) 20 else 30)
        val entries = entries(provider, content)
        val pageCount = maxOf(1, (entries.size + pageSize - 1) / pageSize)
        val normalizedPage = page.coerceIn(0, pageCount - 1)
        val visible = entries.drop(normalizedPage * pageSize).take(pageSize)

        val requestedColumns = component.props.get("columns")?.let { runCatching { it.asInt }.getOrNull() } ?: 3
        val columns = requestedColumns.coerceIn(1, 6).coerceAtMost(maxOf(1, width / if (pokemonGrid) 164 else 120))
        val gap = 8
        val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(if (pokemonGrid) 144 else 96)
        val cellHeight = if (pokemonGrid) 66 else 46
        val viewportBottom = gui.guiHeight()

        visible.forEachIndexed { index, entry ->
            val col = index % columns
            val row = index / columns
            val cx = x + col * (cellWidth + gap)
            val cy = y + row * (cellHeight + gap)
            val hover = mouseX in cx until cx + cellWidth && mouseY in cy until cy + cellHeight
            PixelUi.panel(
                gui, cx, cy, cellWidth, cellHeight,
                if (hover) theme.palette.panelAlt else theme.palette.panel,
                if (hover) theme.palette.accent else PixelUi.withAlpha(theme.palette.accent, 85)
            )
            gui.fill(cx, cy, cx + 4, cy + cellHeight, if (hover) theme.palette.accent2 else theme.palette.accent)

            val textX = if (entry.pokemon != null) {
                if (cy + cellHeight >= 42 && cy <= viewportBottom) {
                    renderPokemonModel(gui, entry.pokemon, cx + 7, cy + 6, 54, theme)
                } else {
                    drawPortraitPlaceholder(gui, cx + 7, cy + 6, 54, theme)
                }
                cx + 68
            } else cx + 12
            val textWidth = (cellWidth - (textX - cx) - 8).coerceAtLeast(32)
            val titleY = if (pokemonGrid) cy + 16 else cy + 10
            val subtitleY = if (pokemonGrid) cy + 36 else cy + 27

            val titleLine = font.split(MiniMessageText.component(entry.title), textWidth).firstOrNull()
            if (titleLine != null) gui.drawString(font, titleLine, textX, titleY, theme.palette.text, true)
            if (entry.subtitle.isNotBlank()) {
                val subtitleLine = font.split(MiniMessageText.component(entry.subtitle), textWidth).firstOrNull()
                if (subtitleLine != null) gui.drawString(font, subtitleLine, textX, subtitleY, theme.palette.mutedText, false)
            }
            hits += HubHitTarget(cx, cy, cx + cellWidth, cy + cellHeight) { onRoute(entry.route) }
        }

        val rows = maxOf(1, (visible.size + columns - 1) / columns)
        var totalHeight = rows * (cellHeight + gap)
        if (pageCount > 1) {
            val navY = y + totalHeight + 4
            val prevX = x
            val nextX = x + width - 78
            val prevEnabled = normalizedPage > 0
            val nextEnabled = normalizedPage + 1 < pageCount
            PixelUi.button(gui, prevX, navY, 78, 25, prevEnabled && mouseX in prevX until prevX + 78 && mouseY in navY until navY + 25, theme)
            PixelUi.button(gui, nextX, navY, 78, 25, nextEnabled && mouseX in nextX until nextX + 78 && mouseY in navY until navY + 25, theme)
            gui.drawCenteredString(font, "← Trước", prevX + 39, navY + 9, if (prevEnabled) theme.palette.text else theme.palette.mutedText)
            gui.drawCenteredString(font, "Sau →", nextX + 39, navY + 9, if (nextEnabled) theme.palette.text else theme.palette.mutedText)
            gui.drawCenteredString(font, "${normalizedPage + 1} / $pageCount", x + width / 2, navY + 9, theme.palette.accent)
            if (prevEnabled) hits += HubHitTarget(prevX, navY, prevX + 78, navY + 25) { onPage(normalizedPage - 1) }
            if (nextEnabled) hits += HubHitTarget(nextX, navY, nextX + 78, navY + 25) { onPage(normalizedPage + 1) }
            totalHeight += 33
        }

        return Result(totalHeight, pageCount, normalizedPage)
    }

    private fun renderPokemonModel(gui: GuiGraphics, view: PokemonView, x: Int, y: Int, size: Int, theme: HubTheme) {
        gui.fill(x, y, x + size, y + size, theme.palette.panelAlt)
        gui.fill(x, y + size - 2, x + size, y + size, PixelUi.withAlpha(theme.palette.accent2, 105))
        val rendered = PokemonModelRenderer.render(
            gui = gui,
            view = view,
            centerX = x + size / 2,
            centerY = y + size / 2,
            size = size,
            yaw = 0f,
            zoom = 0.92f
        )
        if (!rendered) drawPortraitPlaceholder(gui, x, y, size, theme)
    }

    private fun drawPortraitPlaceholder(gui: GuiGraphics, x: Int, y: Int, size: Int, theme: HubTheme) {
        gui.fill(x, y, x + size, y + size, theme.palette.panelAlt)
        val cx = x + size / 2
        val cy = y + size / 2
        gui.fill(cx - 1, y + 9, cx + 1, y + size - 9, PixelUi.withAlpha(theme.palette.accent, 120))
        gui.fill(x + 9, cy - 1, x + size - 9, cy + 1, PixelUi.withAlpha(theme.palette.accent, 120))
        gui.fill(cx - 5, cy - 5, cx + 5, cy + 5, theme.palette.panel)
        gui.fill(cx - 2, cy - 2, cx + 2, cy + 2, theme.palette.accent2)
    }

    private fun entries(provider: String, content: HubContent): List<Entry> = when (provider) {
        "cobblemon:pokemon" -> CobblemonWikiProvider.pokemon(content).map { Entry(it.displayName, pokemonSubtitle(it), it.route, it) }
        "cobblemon:fakemon" -> CobblemonWikiProvider.fakemon(content).map { Entry(it.displayName, pokemonSubtitle(it), it.route, it) }
        "svhub:commands", "environment:commands" -> CommandViewProvider.all().map { Entry("/${it.name}", it.syntaxes.firstOrNull().orEmpty(), it.route) }
        else -> SVHubClientApi.grid(provider, content)?.map { Entry(it.title, it.subtitle, it.route) }.orEmpty()
    }

    private fun pokemonSubtitle(view: PokemonView): String {
        val species = ResourceLocation.tryParse(view.speciesId)?.let(PokemonSpecies::getByIdentifier)
        val form = species?.getForm(view.aspects)
        val type = form?.types?.joinToString("/") { it.name.replaceFirstChar(Char::uppercase) }.orEmpty()
        val dex = if (view.dexNumber > 0) "#${view.dexNumber}" else "Custom"
        val parts = buildList {
            view.sourcePack?.takeIf(String::isNotBlank)?.let(::add)
            add(dex)
            if (type.isNotBlank()) add(type)
        }
        return parts.joinToString(" · ")
    }
}
