package io.github.aristheg201.svarcade.client.editor

import io.github.aristheg201.svarcade.client.gui.SVArcadeScreen
import io.github.aristheg201.svarcade.client.render.PixelUi
import io.github.aristheg201.svarcade.content.HubTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

class ComponentPickerScreen(private val parent: HubEditorScreen) : SVArcadeScreen(Component.literal("SVArcade Components")) {
    private val entries = listOf(
        "text" to "Text", "markdown" to "Markdown", "heading" to "Heading",
        "animated_text" to "Animated Text", "notice" to "Notice", "button" to "Button",
        "link_card" to "Link Card", "command_card" to "Command", "search_box" to "Search Box",
        "image" to "Image", "pixel_image" to "Pixel Image", "animated_image" to "Animated Image",
        "pokemon_model" to "Pokémon Model", "grid" to "Dynamic Grid", "list" to "List",
        "collapse" to "Collapse", "badge" to "Badge", "tooltip" to "Tooltip",
        "table" to "Table", "widget" to "Widget", "separator" to "Separator", "spacer" to "Spacer"
    )

    override fun init() {
        val cols = when {
            width >= 720 -> 4
            width >= 390 -> 3
            else -> 2
        }
        val gap = 6
        val cardW = ((width - 28 - (cols - 1) * gap) / cols).coerceAtMost(170)
        val totalW = cols * cardW + (cols - 1) * gap
        val startX = (width - totalW) / 2
        var y = 50
        entries.forEachIndexed { index, (type, label) ->
            val x = startX + (index % cols) * (cardW + gap)
            if (index > 0 && index % cols == 0) y += 28
            addRenderableWidget(Button.builder(Component.literal(label)) {
                parent.addPickedComponent(type)
                Minecraft.getInstance().setScreen(parent)
            }.bounds(x, y, cardW, 22).build())
        }
        addRenderableWidget(Button.builder(Component.literal("Hủy")) { Minecraft.getInstance().setScreen(parent) }
            .bounds(width / 2 - 36, height - 34, 72, 22).build())
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val content = parent.draftForChild()
        val theme = content.themes[content.defaultTheme] ?: HubTheme("picker")
        PixelUi.background(gui, width, height, content, theme, System.currentTimeMillis() / 50)
        gui.fill(0, 0, width, 42, 0xE80B0F18.toInt())
        gui.drawCenteredString(font, "CHỌN COMPONENT", width / 2, 17, theme.palette.accent)
        super.render(gui, mouseX, mouseY, partialTick)
    }
}
