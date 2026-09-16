package io.github.aristheg201.svhub.client.editor

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.render.GeneratedBackgroundRenderer
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubAsset
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Visual theme/background editor. It edits a private copy and only mutates the
 * parent draft when the user presses Xong, so Cancel is transaction-safe.
 *
 * Generated backgrounds are persisted as HubAsset(source="generated") recipes;
 * the client renderer deterministically materializes and caches their static
 * geometry instead of regenerating random pixels every frame.
 */
class AssetStudioScreen(
    private val parent: HubEditorScreen,
    initial: HubContent,
    private val pageIndex: Int
) : Screen(Component.literal("SVHub Asset Studio")) {
    private var draft = HubEditorScreen.clone(initial)
    private val presets = listOf("pixel_sky", "pixel_forest", "pixel_grid", "pixel_neon", "pixel_cave", "pixel_volcano")
    private var selected = currentTheme().backgroundPreset
    private var density = currentGeneratorInt("density", 2).coerceIn(1, 4)
    private var seed = currentGeneratorLong("seed", defaultSeed())
    private var tickCounter = 0L
    private lateinit var seedBox: EditBox
    private lateinit var densityButton: Button
    private var validationMessage: String? = null

    override fun init() {
        var x = 16
        var y = 44
        presets.forEach { preset ->
            addRenderableWidget(Button.builder(Component.literal(preset.removePrefix("pixel_").uppercase())) {
                selected = preset
                applyPreset()
            }.bounds(x, y, 86, 22).build())
            x += 90
            if (x > width - 100) {
                x = 16
                y += 26
            }
        }

        seedBox = EditBox(font, 16, y + 38, 178, 20, Component.literal("Seed"))
        seedBox.setHint(Component.literal("Seed deterministic"))
        seedBox.setMaxLength(20)
        seedBox.value = seed.toString()
        seedBox.setResponder { validationMessage = null }
        addRenderableWidget(seedBox)

        densityButton = addRenderableWidget(
            Button.builder(Component.literal(densityLabel())) {
                density = if (density >= 4) 1 else density + 1
                it.message = Component.literal(densityLabel())
                applyPreset()
            }.bounds(202, y + 38, 112, 20).build()
        )

        addRenderableWidget(Button.builder(Component.literal("Áp dụng seed")) {
            val parsed = seedBox.value.trim().toLongOrNull()
            if (parsed == null) {
                validationMessage = "Seed phải là số nguyên 64-bit hợp lệ."
            } else {
                seed = parsed
                validationMessage = null
                applyPreset()
            }
        }.bounds(322, y + 38, 104, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Random seed")) {
            seed = System.nanoTime()
            seedBox.value = seed.toString()
            validationMessage = null
            applyPreset()
        }.bounds(434, y + 38, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Hủy")) {
            Minecraft.getInstance().setScreen(parent)
        }.bounds(width - 142, 10, 58, 22).build())

        addRenderableWidget(Button.builder(Component.literal("Xong")) {
            val parsed = seedBox.value.trim().toLongOrNull()
            if (parsed == null) {
                validationMessage = "Seed phải là số nguyên 64-bit hợp lệ."
                return@builder
            }
            seed = parsed
            applyPreset()
            parent.acceptAssetDraft(draft)
            Minecraft.getInstance().setScreen(parent)
        }.bounds(width - 76, 10, 60, 22).build())

        // Materialize the current editor selection as a generated recipe immediately,
        // so preview and saved content use the same rendering path.
        applyPreset()
    }

    override fun tick() {
        tickCounter++
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val theme = currentTheme()
        PixelUi.background(gui, width, height, draft, theme, tickCounter)
        gui.fill(0, 0, width, 34, 0xDD0B0F18.toInt())
        gui.drawString(font, "ASSET STUDIO — Pixel Background Generator", 14, 13, theme.palette.accent, true)

        val boxX = width / 8
        val boxY = 132
        val boxW = width * 3 / 4
        val boxH = height - 168
        PixelUi.panel(gui, boxX, boxY, boxW, boxH, theme.palette.panel, theme.palette.accent2)
        gui.drawCenteredString(font, "Preset: $selected", width / 2, boxY + 18, theme.palette.text)
        gui.drawCenteredString(font, "Generated recipe • seed=$seed • density=$density", width / 2, boxY + 38, theme.palette.mutedText)
        gui.drawCenteredString(font, "Static pixel geometry được generate một lần rồi giữ trong bounded LRU cache.", width / 2, boxY + 56, theme.palette.mutedText)
        gui.drawCenteredString(font, "Recipe được persist trong HubAsset và sync cùng revision — không cần Photoshop/resource PNG.", width / 2, boxY + 74, theme.palette.mutedText)
        gui.drawCenteredString(font, "Cache surfaces: ${GeneratedBackgroundRenderer.cachedSurfaceCount()}", width / 2, boxY + 94, theme.palette.accent2)

        validationMessage?.let {
            gui.drawCenteredString(font, it, width / 2, height - 24, theme.palette.danger)
        }
        super.render(gui, mouseX, mouseY, partialTick)
    }

    private fun applyPreset() {
        val page = draft.pages.getOrNull(pageIndex) ?: return
        val base = currentTheme()
        val customId = "page_${page.id}_theme"
        val assetId = "generated_bg_${page.id}"

        val recipe = JsonObject().apply {
            addProperty("version", 1)
            addProperty("preset", selected)
            addProperty("seed", seed)
            addProperty("density", density)
        }
        val generated = HubAsset(
            id = assetId,
            type = "background",
            source = "generated",
            resource = null,
            width = 320,
            height = 180,
            generator = recipe,
            tags = listOf("generated", "pixel", "background", page.category)
        )
        val custom = base.copy(id = customId, backgroundAsset = assetId, backgroundPreset = selected)
        val themes = draft.themes + (customId to custom)
        val assets = draft.assets + (assetId to generated)
        val pages = draft.pages.toMutableList()
        pages[pageIndex] = page.copy(theme = customId)
        draft = draft.copy(themes = themes, assets = assets, pages = pages)
        GeneratedBackgroundRenderer.clear()
    }

    private fun currentTheme(): HubTheme {
        val page = draft.pages.getOrNull(pageIndex)
        return draft.themes[page?.theme ?: draft.defaultTheme] ?: draft.themes[draft.defaultTheme] ?: HubTheme("asset")
    }

    private fun currentGeneratorInt(key: String, fallback: Int): Int {
        val asset = currentTheme().backgroundAsset?.let(draft.assets::get) ?: return fallback
        return runCatching { asset.generator.get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    }

    private fun currentGeneratorLong(key: String, fallback: Long): Long {
        val asset = currentTheme().backgroundAsset?.let(draft.assets::get) ?: return fallback
        return runCatching { asset.generator.get(key)?.asLong ?: fallback }.getOrDefault(fallback)
    }

    private fun defaultSeed(): Long = draft.pages.getOrNull(pageIndex)?.id?.fold(1125899906842597L) { acc, c -> acc * 31L + c.code } ?: 1L

    private fun densityLabel(): String = "Density: $density"
}
