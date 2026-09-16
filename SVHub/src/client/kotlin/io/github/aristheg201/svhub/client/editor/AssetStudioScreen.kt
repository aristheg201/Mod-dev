package io.github.aristheg201.svhub.client.editor

import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Visual theme/background editor. It edits a private copy and only mutates the
 * parent draft when the user presses Xong, so Cancel is transaction-safe.
 */
class AssetStudioScreen(
    private val parent: HubEditorScreen,
    initial: HubContent,
    private val pageIndex: Int
) : Screen(Component.literal("SVHub Asset Studio")) {
    private var draft = HubEditorScreen.clone(initial)
    private val presets = listOf("pixel_sky", "pixel_forest", "pixel_grid", "pixel_neon", "pixel_cave", "pixel_volcano")
    private var selected = currentTheme().backgroundPreset
    private var tickCounter = 0L

    override fun init() {
        var x = 16
        var y = 44
        presets.forEach { preset ->
            addRenderableWidget(Button.builder(Component.literal(preset.removePrefix("pixel_").uppercase())) {
                selected = preset
                applyPreset()
            }.bounds(x, y, 86, 22).build())
            x += 90
            if (x > width - 100) { x = 16; y += 26 }
        }
        addRenderableWidget(Button.builder(Component.literal("Hủy")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(width - 142, 10, 58, 22).build())
        addRenderableWidget(Button.builder(Component.literal("Xong")) {
            parent.acceptAssetDraft(draft)
            Minecraft.getInstance().setScreen(parent)
        }.bounds(width - 76, 10, 60, 22).build())
    }

    override fun tick() { tickCounter++ }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val theme = currentTheme()
        PixelUi.background(gui, width, height, draft, theme, tickCounter)
        gui.fill(0, 0, width, 34, 0xDD0B0F18.toInt())
        gui.drawString(font, "ASSET STUDIO — Pixel Background Generator", 14, 13, theme.palette.accent, true)
        val boxX = width / 8
        val boxY = 104
        val boxW = width * 3 / 4
        val boxH = height - 140
        PixelUi.panel(gui, boxX, boxY, boxW, boxH, theme.palette.panel, theme.palette.accent2)
        gui.drawCenteredString(font, "Preset: $selected", width / 2, boxY + 18, theme.palette.text)
        gui.drawCenteredString(font, "Static PNG generated + procedural motion overlay", width / 2, boxY + 40, theme.palette.mutedText)
        gui.drawCenteredString(font, "Không cần Photoshop: chọn preset, preview trực tiếp, rồi Xong.", width / 2, boxY + 58, theme.palette.mutedText)
        super.render(gui, mouseX, mouseY, partialTick)
    }

    private fun applyPreset() {
        val page = draft.pages.getOrNull(pageIndex) ?: return
        val base = currentTheme()
        val customId = "page_${page.id}_theme"
        val assetId = when (selected) {
            "pixel_sky" -> "home_pixel"
            "pixel_forest", "pixel_cave" -> "wiki_pixel"
            "pixel_grid" -> "commands_pixel"
            "pixel_neon", "pixel_volcano" -> "updates_pixel"
            else -> null
        }
        val custom = base.copy(id = customId, backgroundAsset = assetId, backgroundPreset = selected)
        val themes = draft.themes + (customId to custom)
        val pages = draft.pages.toMutableList(); pages[pageIndex] = page.copy(theme = customId)
        draft = draft.copy(themes = themes, pages = pages)
    }

    private fun currentTheme(): HubTheme {
        val page = draft.pages.getOrNull(pageIndex)
        return draft.themes[page?.theme ?: draft.defaultTheme] ?: draft.themes[draft.defaultTheme] ?: HubTheme("asset")
    }
}
