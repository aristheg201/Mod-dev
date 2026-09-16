package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.render.AnimatedAssetRenderer
import io.github.aristheg201.svhub.client.render.AnimatedTextRenderer
import io.github.aristheg201.svhub.client.render.PixelUi
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
            "heading" -> {
                val text = props.string("text")
                AnimatedTextRenderer.render(gui, text, x, y + 2, state.theme.palette.text, "none", props.float("scale", 1.15f), false, state.tick)
                28
            }
            "animated_text" -> {
                val text = props.string("text")
                val centered = props.string("align", "left").equals("center", true)
                AnimatedTextRenderer.render(
                    gui, text, if (centered) x + availableWidth / 2 else x, y + 3,
                    state.theme.palette.accent, props.string("animation", state.theme.titleAnimation),
                    props.float("scale", 1.25f).coerceIn(0.6f, 2.5f), centered, state.tick
                )
                32
            }
            "text", "markdown" -> renderParagraph(gui, cleanMarkup(props.string("text")), x, y, availableWidth, state, 8)
            "notice" -> renderNotice(gui, props.string("text"), x, y, availableWidth, state)
            "separator" -> {
                gui.fill(x, y + 7, x + availableWidth, y + 9, state.theme.palette.accent2)
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
                gui.drawString(state.font, props.string("placeholder", "Dùng thanh tìm kiếm phía trên"), x + 4, y + 5, state.theme.palette.mutedText, false)
                22
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
                PixelUi.panel(gui, x, y, availableWidth, 30, state.theme.palette.panel, state.theme.palette.danger)
                gui.drawString(state.font, "Component chưa hỗ trợ: ${component.type}", x + 8, y + 10, state.theme.palette.danger, false)
                36
            }
        }
    }

    private fun renderParagraph(gui: GuiGraphics, text: String, x: Int, y: Int, width: Int, state: RenderState, bottom: Int): Int {
        if (text.isBlank()) return 8
        var cy = y
        state.font.split(Component.literal(text), width).take(24).forEach { line ->
            gui.drawString(state.font, line, x, cy, state.theme.palette.text, false)
            cy += 12
        }
        return (cy - y) + bottom
    }

    private fun renderNotice(gui: GuiGraphics, text: String, x: Int, y: Int, width: Int, state: RenderState): Int {
        val lines = state.font.split(Component.literal(text), width - 24).take(8)
        val height = 18 + lines.size * 12
        PixelUi.panel(gui, x, y, width, height, state.theme.palette.panelAlt, state.theme.palette.accent)
        var cy = y + 9
        lines.forEach { line -> gui.drawString(state.font, line, x + 12, cy.also { cy += 12 }, state.theme.palette.text, false) }
        return height + 8
    }

    private fun renderBadge(gui: GuiGraphics, text: String, x: Int, y: Int, state: RenderState): Int {
        val width = (state.font.width(text) + 16).coerceAtMost(220)
        PixelUi.panel(gui, x, y, width, 22, state.theme.palette.panelAlt, state.theme.palette.accent2)
        gui.drawCenteredString(state.font, state.font.plainSubstrByWidth(text, width - 10), x + width / 2, y + 7, state.theme.palette.text)
        return 28
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
        if (!AnimatedAssetRenderer.render(gui, asset, x, y, drawWidth, height, frames, fps, state.tick)) {
            return renderMissingAsset(gui, assetId, x, y, width, state)
        }
        return height + 8
    }

    private fun renderMissingAsset(gui: GuiGraphics, id: String, x: Int, y: Int, width: Int, state: RenderState): Int {
        PixelUi.panel(gui, x, y, width, 34, state.theme.palette.panel, state.theme.palette.danger)
        gui.drawString(state.font, "Asset không tồn tại/không đọc được: $id", x + 8, y + 11, state.theme.palette.danger, false)
        return 42
    }

    private fun renderActionCard(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val props = component.props
        val label = props.string("label", props.string("command", props.string("title", "Mở")))
        val description = props.string("description")
        val height = if (description.isBlank()) 32 else 48
        val hovered = state.mouseX in x until x + width && state.mouseY in y until y + height
        PixelUi.button(gui, x, y, width, height, hovered, state.theme)
        gui.drawString(state.font, state.font.plainSubstrByWidth(label, width - 20), x + 10, y + 9, state.theme.palette.text, true)
        if (description.isNotBlank()) {
            gui.drawString(state.font, state.font.plainSubstrByWidth(description, width - 20), x + 10, y + 27, state.theme.palette.mutedText, false)
        }
        state.hits += HubHitTarget(x, y, x + width, y + height) { state.execute(component) }
        return height + 8
    }

    private fun renderList(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val items = component.props.getAsJsonArray("items")?.mapNotNull { runCatching { it.asString }.getOrNull() }
            ?: component.props.string("text").lines().filter(String::isNotBlank)
        var cy = y
        items.take(32).forEach { item ->
            gui.drawString(state.font, "•", x + 2, cy, state.theme.palette.accent, true)
            state.font.split(Component.literal(item), width - 18).take(3).forEach { line ->
                gui.drawString(state.font, line, x + 16, cy, state.theme.palette.text, false)
                cy += 12
            }
            cy += 3
        }
        return (cy - y).coerceAtLeast(14) + 4
    }

    private fun renderCollapse(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val expanded = state.isExpanded(component.id)
        val title = component.props.string("title", "Chi tiết")
        val headerHeight = 28
        val hovered = state.mouseX in x until x + width && state.mouseY in y until y + headerHeight
        PixelUi.button(gui, x, y, width, headerHeight, hovered, state.theme)
        gui.drawString(state.font, (if (expanded) "▼ " else "▶ ") + title, x + 10, y + 9, state.theme.palette.text, true)
        state.hits += HubHitTarget(x, y, x + width, y + headerHeight) { state.toggleExpanded(component.id) }
        if (!expanded) return headerHeight + 6
        val body = component.props.string("text")
        val bodyHeight = renderParagraph(gui, body, x + 10, y + headerHeight + 6, width - 20, state, 8)
        return headerHeight + bodyHeight + 10
    }

    private fun renderTable(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val rows = component.props.getAsJsonArray("rows") ?: return 20
        var cy = y
        rows.take(20).forEachIndexed { index, element ->
            val cells = if (element.isJsonArray) element.asJsonArray.map { runCatching { it.asString }.getOrDefault("") } else listOf(runCatching { element.asString }.getOrDefault("") )
            val rowHeight = 24
            gui.fill(x, cy, x + width, cy + rowHeight, if (index % 2 == 0) state.theme.palette.panel else state.theme.palette.panelAlt)
            val cols = cells.size.coerceAtLeast(1)
            val colWidth = width / cols
            cells.forEachIndexed { col, value ->
                gui.drawString(state.font, state.font.plainSubstrByWidth(value, colWidth - 10), x + col * colWidth + 5, cy + 8, state.theme.palette.text, index == 0)
            }
            cy += rowHeight
        }
        return (cy - y) + 8
    }

    private fun renderWidget(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val label = component.props.string("label", component.props.string("provider", "Widget"))
        PixelUi.panel(gui, x, y, width, 38, state.theme.palette.panel, state.theme.palette.accent2)
        gui.drawString(state.font, label, x + 10, y + 13, state.theme.palette.text, true)
        return 46
    }

    private fun renderPokemonModel(gui: GuiGraphics, component: HubComponent, x: Int, y: Int, width: Int, state: RenderState): Int {
        val species = component.props.string("species", "cobblemon:pikachu")
        val route = "pokemon/${URLEncoder.encode(species, StandardCharsets.UTF_8)}"
        val view = CobblemonWikiProvider.resolveRoute(route, state.content) ?: return 36
        val size = component.props.int("size", minOf(128, width)).coerceIn(48, minOf(220, width))
        PokemonModelRenderer.render(gui, view, x + width / 2, y + size / 2 + 8, size)
        return size + 20
    }

    private fun cleanMarkup(text: String): String = text
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")

    private fun com.google.gson.JsonObject.string(key: String, fallback: String = ""): String =
        runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private fun com.google.gson.JsonObject.int(key: String, fallback: Int): Int =
        runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private fun com.google.gson.JsonObject.float(key: String, fallback: Float): Float =
        runCatching { get(key)?.asFloat ?: fallback }.getOrDefault(fallback)
}
