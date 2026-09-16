package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.CommandViewProvider
import io.github.aristheg201.svhub.client.EnvironmentViewProvider
import io.github.aristheg201.svhub.client.api.SVHubClientApi
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonPortraitCache
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubComponent
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

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
        val requestedPageSize = component.props.get("pageSize")?.let { runCatching { it.asInt }.getOrNull() } ?: 24
        val pageSize = requestedPageSize.coerceIn(6, 30)
        val entries = entries(provider, content)
        val pageCount = maxOf(1, (entries.size + pageSize - 1) / pageSize)
        val normalizedPage = page.coerceIn(0, pageCount - 1)
        val visible = entries.drop(normalizedPage * pageSize).take(pageSize)
        val pokemonGrid = provider == "cobblemon:pokemon" || provider == "cobblemon:fakemon"

        val requestedColumns = component.props.get("columns")?.let { runCatching { it.asInt }.getOrNull() } ?: 3
        val columns = requestedColumns.coerceIn(1, 6).coerceAtMost(maxOf(1, width / if (pokemonGrid) 164 else 120))
        val gap = 8
        val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(if (pokemonGrid) 144 else 96)
        val cellHeight = if (pokemonGrid) 64 else 44

        visible.forEachIndexed { index, entry ->
            val col = index % columns
            val row = index / columns
            val cx = x + col * (cellWidth + gap)
            val cy = y + row * (cellHeight + gap)
            val hover = mouseX in cx until cx + cellWidth && mouseY in cy until cy + cellHeight
            PixelUi.panel(gui, cx, cy, cellWidth, cellHeight, if (hover) theme.palette.panelAlt else theme.palette.panel, if (hover) theme.palette.accent else theme.palette.accent2)

            val textX = if (entry.pokemon != null) {
                renderPokemonPortrait(gui, entry.pokemon, cx + 5, cy + 5, 54, theme)
                cx + 64
            } else cx + 8
            val textWidth = (cellWidth - (textX - cx) - 8).coerceAtLeast(32)
            val titleY = if (pokemonGrid) cy + 16 else cy + 9
            val subtitleY = if (pokemonGrid) cy + 35 else cy + 25
            gui.drawString(font, font.plainSubstrByWidth(entry.title, textWidth), textX, titleY, theme.palette.text, true)
            if (entry.subtitle.isNotBlank()) {
                gui.drawString(font, font.plainSubstrByWidth(entry.subtitle, textWidth), textX, subtitleY, theme.palette.mutedText, false)
            }
            hits += HubHitTarget(cx, cy, cx + cellWidth, cy + cellHeight) { onRoute(entry.route) }
        }

        val rows = maxOf(1, (visible.size + columns - 1) / columns)
        var totalHeight = rows * (cellHeight + gap)
        if (pageCount > 1) {
            val navY = y + totalHeight + 4
            val prevX = x
            val nextX = x + width - 74
            val prevEnabled = normalizedPage > 0
            val nextEnabled = normalizedPage + 1 < pageCount
            PixelUi.button(gui, prevX, navY, 74, 24, prevEnabled && mouseX in prevX until prevX + 74 && mouseY in navY until navY + 24, theme)
            PixelUi.button(gui, nextX, navY, 74, 24, nextEnabled && mouseX in nextX until nextX + 74 && mouseY in navY until navY + 24, theme)
            gui.drawCenteredString(font, "← Trước", prevX + 37, navY + 8, if (prevEnabled) theme.palette.text else theme.palette.mutedText)
            gui.drawCenteredString(font, "Sau →", nextX + 37, navY + 8, if (nextEnabled) theme.palette.text else theme.palette.mutedText)
            gui.drawCenteredString(font, "${normalizedPage + 1} / $pageCount", x + width / 2, navY + 8, theme.palette.mutedText)
            if (prevEnabled) hits += HubHitTarget(prevX, navY, prevX + 74, navY + 24) { onPage(normalizedPage - 1) }
            if (nextEnabled) hits += HubHitTarget(nextX, navY, nextX + 74, navY + 24) { onPage(normalizedPage + 1) }
            totalHeight += 32
        }

        if (pokemonGrid && visible.any { it.pokemon != null }) {
            // One portrait at most per 50 ms bucket, even if several grids render.
            PokemonPortraitCache.pump(System.currentTimeMillis() / 50L)
        }
        return Result(totalHeight, pageCount, normalizedPage)
    }

    private fun renderPokemonPortrait(gui: GuiGraphics, view: PokemonView, x: Int, y: Int, size: Int, theme: HubTheme) {
        val location = PokemonPortraitCache.request(view)
        if (location != null) {
            runCatching {
                gui.blit(location, x, y, size, size, 0f, 0f, 96, 96, 96, 96)
            }.onFailure {
                drawPortraitPlaceholder(gui, x, y, size, theme)
            }
        } else {
            drawPortraitPlaceholder(gui, x, y, size, theme)
        }
    }

    private fun drawPortraitPlaceholder(gui: GuiGraphics, x: Int, y: Int, size: Int, theme: HubTheme) {
        gui.fill(x, y, x + size, y + size, theme.palette.panelAlt)
        val cx = x + size / 2
        val cy = y + size / 2
        gui.fill(cx - 1, y + 9, cx + 1, y + size - 9, theme.palette.accent2)
        gui.fill(x + 9, cy - 1, x + size - 9, cy + 1, theme.palette.accent2)
        gui.fill(cx - 5, cy - 5, cx + 5, cy + 5, theme.palette.panel)
    }

    private fun entries(provider: String, content: HubContent): List<Entry> = when (provider) {
        "cobblemon:pokemon" -> CobblemonWikiProvider.pokemon(content).map { Entry(it.displayName, "#${it.dexNumber} • ${it.speciesId}", it.route, it) }
        "cobblemon:fakemon" -> CobblemonWikiProvider.fakemon(content).map { Entry(it.displayName, it.speciesId, it.route, it) }
        "svhub:commands", "environment:commands" -> CommandViewProvider.all().map { Entry("/${it.name}", it.syntaxes.firstOrNull().orEmpty(), it.route) }
        "environment:mods" -> EnvironmentViewProvider.all().map { Entry(it.name, "${it.id} • ${it.sideLabel}", it.route) }
        else -> SVHubClientApi.grid(provider, content)?.map { Entry(it.title, it.subtitle, it.route) }.orEmpty()
    }
}
