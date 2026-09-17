package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.render.AnimatedAssetRenderer
import io.github.aristheg201.svhub.client.render.AnimatedTextRenderer
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.client.nativeui.NativePixelArt
import io.github.aristheg201.svhub.content.HubComponent
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HubComponentRenderer {
    data class RenderState(
        val content: HubContent,
        val theme: HubTheme,
        val font: Font,
        val width: Int,
        val mouseX: Int,
        val mouseY: Int,
        val tick: Long,
        val hits: MutableList<HubHitTarget>,
        val gridPage: (String) -> Int,
        val setGridPage: (String, Int) -> Unit,
        val isExpanded: (String) -> Boolean,
        val toggleExpanded: (String) -> Unit,
        val navigate: (String) -> Unit,
        val execute: (HubComponent) -> Unit
    )

    fun render(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, availableWidth: Int, state: RenderState): Int {
        val props = component.props
        return when (component.type) {
            "heading" -> renderHeading(gui, props.string("text"), x, y, availableWidth, state)
            "animated_text" -> {
                val text = props.string("text")
                val centered = props.string("align", "left").equals("center", true)
                AnimatedTextRenderer.render(
                    gui = gui,
                    text = text,
                    x = if (centered) x + availableWidth / 2 else x,
                    y = y + 3,
                    color = state.theme.palette.accent,
                    animation = props.string("animation", state.theme.titleAnimation),
                    scale = props.float("scale", 1.25f).coerceIn(0.6f, 2.5f),
                    centered = centered,
                    tick = state.tick
                )
                32
            }
            "text", "markdown" -> renderParagraph(gui, props.string("text"), x, y, availableWidth, state, 8)
            "notice" -> renderNotice(gui, props.string("text"), x, y, availableWidth, state)
            "separator" -> {
                gui.fill(x, y + 7, x + availableWidth, y + 8, PixelUi.withAlpha(state.theme.palette.accent, 90))
                gui.fill(x, y + 8, x + minOf(64, availableWidth), y + 10, state.theme.palette.accent2)
                18
            }
            "spacer" -> props.int("height", 16).coerceIn(4, 160)
            "badge" -> renderBadge(gui, props.string("text", props.string("label", "Badge")), x, y, state)
            "tooltip" -> renderParagraph(gui, props.string("text"), x, y, availableWidth, state, 4)
            "image", "pixel_image" -> renderImage(gui, component, x, y, availableWidth, state)
            "animated_image" -> renderAnimatedImage(gui, component, x, y, availableWidth, state)
            "button", "link_card", "command_card" -> renderActionCard(gui, component, x, y, availableWidth, state)
            "list" -> renderList(gui, component, x, y, availableWidth, state)
            "collapse" -> renderCollapse(gui, component, x, y, availableWidth, state)
            "table" -> renderTable(gui, component, x, y, availableWidth, state)
            "widget" -> renderWidget(gui, component, x, y, availableWidth, state)
            "search_box" -> {
                val hint = props.string("placeholder", "Dùng thanh tìm kiếm phía trên")
                PixelUi.sectionBand(gui, x, y, availableWidth, 24, state.theme)
                MiniMessageText.draw(gui, state.font, hint, x + 10, y + 8, state.theme.palette.mutedText)
                30
            }
            "grid" -> {
                val result = HubDynamicGridRenderer.render(
                    gui, state.font, state.content, component, state.theme, x, y, availableWidth,
                    state.gridPage(component.id), state.mouseX, state.mouseY, state.navigate,
                    { page -> state.setGridPage(component.id, page) }, state.hits
                )
                result.height + 6
            }
            "pokemon_model" -> renderPokemonModel(gui, component, x, y, availableWidth, state)
            else -> {
                PixelUi.panel(gui, x, y, availableWidth, 34, state.theme.palette.panel, state.theme.palette.danger)
                gui.drawString(state.font, "Component chưa hỗ trợ: ${component.type}", x + 10, y + 12, state.theme.palette.danger, false)
                40
            }
        }
    }

    private fun renderHeading(gui: GuiGraphics, text: String, x: Int, y: Int, width: Int, state: RenderState): Int {
        val height = 27
        PixelUi.sectionBand(gui, x, y, width, height, state.theme)
        val markup = if (text.contains("<bold>", ignoreCase = true) || text.contains("<b>", ignoreCase = true)) text else "<bold>$text</bold>"
        MiniMessageText.draw(gui, state.font, markup, x + 12, y + 9, state.theme.palette.text)
        return height + 8
    }

    private fun renderParagraph(gui: GuiGraphics, text: String, x: Int, y: Int, width: Int, state: RenderState, bottom: Int): Int {
        if (text.isBlank()) return 8
        var cy = y
        MiniMessageText.split(state.font, text, width).take(MAX_PARAGRAPH_LINES).forEach { line ->
            gui.drawString(state.font, line, x, cy, state.theme.palette.text, false); cy += 12
        }
        return (cy - y) + bottom
    }

    private fun renderNotice(gui: GuiGraphics, text: String, x: Int, y: Int, width: Int, state: RenderState): Int {
        val lines = MiniMessageText.split(state.font, text, width - 30).take(10)
        val height = 20 + lines.size * 12
        PixelUi.panel(gui, x, y, width, height, state.theme.palette.panelAlt, state.theme.palette.accent)
        gui.fill(x, y, x + 5, y + height, state.theme.palette.accent2)
        var cy = y + 10
        lines.forEach { line -> gui.drawString(state.font, line, x + 16, cy, state.theme.palette.text, false); cy += 12 }
        return height + 9
    }

    private fun renderBadge(gui: GuiGraphics, text: String, x: Int, y: Int, state: RenderState): Int {
        val component = MiniMessageText.component(text)
        val width = (state.font.width(component) + 18).coerceIn(44, 230)
        PixelUi.panel(gui, x, y, width, 22, state.theme.palette.panelAlt, state.theme.palette.accent2)
        gui.drawString(state.font, component, x + 9, y + 7, state.theme.palette.text, false)
        return 29
    }

    private fun renderImage(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val assetId = component.props.string("asset")
        val asset = state.content.assets[assetId] ?: return renderMissingAsset(gui, assetId, x, y, width, state)
        val height = component.props.int("height", 120).coerceIn(24, 360)
        val drawWidth = component.props.int("width", width).coerceIn(24, width)
        if (!PixelUi.image(gui, asset, x, y, drawWidth, height)) return renderMissingAsset(gui, assetId, x, y, width, state)
        return height + 8
    }

    private fun renderAnimatedImage(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val assetId = component.props.string("asset")
        val asset = state.content.assets[assetId] ?: return renderMissingAsset(gui, assetId, x, y, width, state)
        val height = component.props.int("height", 64).coerceIn(16, 256)
        val drawWidth = component.props.int("width", width).coerceIn(16, width)
        val frames = component.props.int("frames", 1)
        val fps = component.props.int("fps", 8)
        if (!AnimatedAssetRenderer.render(gui, asset, x, y, drawWidth, height, frames, fps, state.tick)) return renderMissingAsset(gui, assetId, x, y, width, state)
        return height + 8
    }

    private fun renderMissingAsset(gui: GuiGraphics, id: String, x: Int, y: Int, width: Int, state: RenderState): Int {
        PixelUi.panel(gui, x, y, width, 36, state.theme.palette.panel, state.theme.palette.danger)
        gui.drawString(state.font, "Asset không tồn tại/không đọc được: $id", x + 10, y + 13, state.theme.palette.danger, false)
        return 44
    }

    private fun renderActionCard(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val props = component.props
        val isCommand = component.type == "command_card"
        val command = props.string("command")
        val label = props.string("label", if (isCommand) command else props.string("title", "Mở"))
        val description = props.string("description")
        val icon = props.string("icon")
        val textInset = if (icon.isBlank()) 12 else 42
        val descriptionLines = if (description.isBlank()) emptyList() else MiniMessageText.split(state.font, description, width - textInset - 14).take(2)
        val height = if (descriptionLines.isEmpty()) 34 else 38 + descriptionLines.size * 11
        val hovered = state.mouseX in x until x + width && state.mouseY in y until y + height
        PixelUi.button(gui, x, y, width, height, hovered, state.theme)
        if (isCommand) {
            gui.drawString(state.font, Component.literal(command), x + 12, y + 9, state.theme.palette.accent2, true)
            val marker = "COMMAND"; val markerWidth = state.font.width(marker)
            gui.drawString(state.font, marker, x + width - markerWidth - 10, y + 9, state.theme.palette.mutedText, false)
        } else {
            if (icon.isNotBlank()) NativePixelArt.icon(gui, icon, x + 11, y + 10, 22, if (hovered) state.theme.palette.accent2 else state.theme.palette.accent)
            MiniMessageText.draw(gui, state.font, label, x + textInset, y + 9, if (hovered) state.theme.palette.accent else state.theme.palette.text, true)
        }
        var cy = y + 25
        descriptionLines.forEach { line -> gui.drawString(state.font, line, x + textInset, cy, state.theme.palette.mutedText, false); cy += 11 }
        state.hits += HubHitTarget(x, y, x + width, y + height) { state.execute(component) }
        return height + 8
    }

    private fun renderList(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val items = component.props.getAsJsonArray("items")?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: component.props.string("text").lines().filter(String::isNotBlank)
        var cy = y
        items.take(32).forEach { item ->
            gui.fill(x + 1, cy + 3, x + 5, cy + 7, state.theme.palette.accent2)
            MiniMessageText.split(state.font, item, width - 20).take(3).forEach { line -> gui.drawString(state.font, line, x + 15, cy, state.theme.palette.text, false); cy += 12 }
            cy += 4
        }
        return (cy - y).coerceAtLeast(14) + 4
    }

    private fun renderCollapse(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val expanded = state.isExpanded(component.id); val title = component.props.string("title", "Chi tiết"); val headerHeight = 30
        val hovered = state.mouseX in x until x + width && state.mouseY in y until y + headerHeight
        PixelUi.button(gui, x, y, width, headerHeight, hovered, state.theme)
        gui.drawString(state.font, if (expanded) "−" else "+", x + 11, y + 10, state.theme.palette.accent2, true)
        MiniMessageText.draw(gui, state.font, title, x + 27, y + 10, state.theme.palette.text, true)
        state.hits += HubHitTarget(x, y, x + width, y + headerHeight) { state.toggleExpanded(component.id) }
        if (!expanded) return headerHeight + 7
        val bodyHeight = renderParagraph(gui, component.props.string("text"), x + 12, y + headerHeight + 8, width - 24, state, 8)
        return headerHeight + bodyHeight + 12
    }

    private fun renderTable(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val rows = component.props.getAsJsonArray("rows") ?: return 20; var cy = y
        rows.take(20).forEachIndexed { index, element ->
            val cells = if (element.isJsonArray) element.asJsonArray.map { runCatching { it.asString }.getOrDefault("") } else listOf(runCatching { element.asString }.getOrDefault(""))
            val rowHeight = if (index == 0) 25 else 23
            val fill = if (index == 0) state.theme.palette.panelAlt else if (index % 2 == 0) state.theme.palette.panel else PixelUi.withAlpha(state.theme.palette.panelAlt, 205)
            gui.fill(x, cy, x + width, cy + rowHeight, fill)
            if (index == 0) gui.fill(x, cy, x + 4, cy + rowHeight, state.theme.palette.accent)
            val cols = cells.size.coerceAtLeast(1); val colWidth = width / cols
            cells.forEachIndexed { col, value ->
                val parsed = MiniMessageText.component(value)
                val line = state.font.split(parsed, (colWidth - 12).coerceAtLeast(16)).firstOrNull() ?: return@forEachIndexed
                gui.drawString(state.font, line, x + col * colWidth + 7, cy + 8, if (index == 0) state.theme.palette.accent else state.theme.palette.text, index == 0)
            }
            cy += rowHeight
        }
        return (cy - y) + 8
    }

    private fun renderWidget(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val label = component.props.string("label", component.props.string("provider", "Widget"))
        PixelUi.panel(gui, x, y, width, 40, state.theme.palette.panel, state.theme.palette.accent2)
        gui.fill(x, y, x + 5, y + 40, state.theme.palette.accent)
        MiniMessageText.draw(gui, state.font, label, x + 14, y + 14, state.theme.palette.text, true)
        return 48
    }

    private fun renderPokemonModel(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val species = component.props.string("species", "cobblemon:pikachu")
        val route = "pokemon/${URLEncoder.encode(species, StandardCharsets.UTF_8)}"
        val view = CobblemonWikiProvider.resolveRoute(route, state.content) ?: return 36
        val size = component.props.int("size", minOf(128, width)).coerceIn(48, minOf(220, width))
        PokemonModelRenderer.render(gui, view, x + width / 2, y + size / 2 + 8, size)
        return size + 20
    }

    private fun com.google.gson.JsonObject.string(key: String, fallback: String = ""): String = runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)
    private fun com.google.gson.JsonObject.int(key: String, fallback: Int): Int = runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun com.google.gson.JsonObject.float(key: String, fallback: Float): Float = runCatching { get(key)?.asFloat ?: fallback }.getOrDefault(fallback)
    private const val MAX_PARAGRAPH_LINES = 36
}
