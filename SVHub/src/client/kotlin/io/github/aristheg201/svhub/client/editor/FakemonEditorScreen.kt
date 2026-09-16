package io.github.aristheg201.svhub.client.editor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import io.github.aristheg201.svhub.client.gui.SVHubScreen
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.FakemonEntry
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubTheme
import io.github.aristheg201.svhub.content.LocalizedText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** In-game editor for base-species + aspect Fakemon entries. */
class FakemonEditorScreen(
    private val parent: HubEditorScreen,
    initial: HubContent
) : SVHubScreen(Component.literal("SVHub Fakemon Editor")) {
    private data class Hit(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val run: () -> Unit) {
        fun contains(x: Double, y: Double) = x >= x1 && x < x2 && y >= y1 && y < y2
    }

    private var draft = HubEditorScreen.clone(initial)
    private var selected = if (draft.cobblemonWiki.explicitFakemon.isEmpty()) -1 else 0
    private var syncing = false
    private val hits = mutableListOf<Hit>()
    private lateinit var idBox: EditBox
    private lateinit var speciesBox: EditBox
    private lateinit var aspectsBox: EditBox
    private lateinit var nameBox: EditBox
    private lateinit var wikiPageBox: EditBox

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("+ Fakemon")) { addEntry() }.bounds(10, 9, 76, 22).build())
        addRenderableWidget(Button.builder(Component.literal("Xóa")) { deleteEntry() }.bounds(90, 9, 48, 22).build())
        addRenderableWidget(Button.builder(Component.literal("Hủy")) { Minecraft.getInstance().setScreen(parent) }.bounds(width - 142, 9, 58, 22).build())
        addRenderableWidget(Button.builder(Component.literal("Xong")) {
            parent.acceptFakemonDraft(draft)
            Minecraft.getInstance().setScreen(parent)
        }.bounds(width - 76, 9, 60, 22).build())

        idBox = field(0, "ID") { value -> update { entry -> entry.copy(id = sanitizeId(value, entry.id, entry.species)) } }
        speciesBox = field(1, "Species") { value -> update { it.copy(species = value.trim()) } }
        aspectsBox = field(2, "Aspects: comma,separated") { value -> update { it.copy(aspects = value.split(',').map(String::trim).filter(String::isNotBlank).distinct()) } }
        nameBox = field(3, "Tên hiển thị") { value -> update { it.copy(displayName = LocalizedText(it.displayName.values + ("vi_vn" to value))) } }
        wikiPageBox = field(4, "Wiki page ID (optional)") { value -> update { it.copy(wikiPage = value.trim().ifBlank { null }) } }
        syncFields()
    }

    private fun field(index: Int, hint: String, responder: (String) -> Unit): EditBox {
        val left = (width * 0.43).toInt()
        val box = EditBox(font, left, 78 + index * 43, width - left - 18, 20, Component.literal(hint))
        box.setHint(Component.literal(hint))
        box.setMaxLength(512)
        box.setResponder { if (!syncing) responder(it) }
        addRenderableWidget(box)
        return box
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val theme = draft.themes[draft.defaultTheme] ?: HubTheme("fakemon_editor")
        PixelUi.background(gui, width, height, draft, theme, System.currentTimeMillis() / 50)
        gui.fill(0, 0, width, 38, 0xE80B0F18.toInt())
        hits.clear()

        val listW = (width * 0.39).toInt()
        PixelUi.panel(gui, 10, 46, listW, height - 58, theme.palette.panel, theme.palette.accent2)
        gui.drawString(font, "FAKEMON ENTRIES", 20, 56, theme.palette.accent, true)
        var y = 76
        draft.cobblemonWiki.explicitFakemon.forEachIndexed { index, entry ->
            if (y > height - 30) return@forEachIndexed
            val active = index == selected
            val hover = mouseX in 16 until 10 + listW - 6 && mouseY in y until y + 31
            if (active || hover) gui.fill(16, y, 10 + listW - 6, y + 31, if (active) 0xAA314A68.toInt() else 0x66314158)
            val title = entry.displayName.resolve("vi_vn").ifBlank { entry.id }
            gui.drawString(font, font.plainSubstrByWidth(title, listW - 38), 22, y + 6, theme.palette.text, true)
            gui.drawString(font, font.plainSubstrByWidth(entry.species, listW - 38), 22, y + 19, theme.palette.mutedText, false)
            hits += Hit(16, y, 10 + listW - 6, y + 31) { selected = index; syncFields() }
            y += 34
        }

        val x = (width * 0.43).toInt()
        gui.drawString(font, "THÔNG TIN", x, 52, theme.palette.accent, true)
        listOf("ID", "Species", "Aspects", "Tên hiển thị", "Wiki page").forEachIndexed { i, label ->
            gui.drawString(font, label, x, 67 + i * 43, theme.palette.mutedText, false)
        }
        gui.drawString(font, "Species lấy trực tiếp từ PokemonSpecies registry. Aspect có thể nhập tay; form-aspect sẽ được gợi ý khi Cobblemon cung cấp.", x, height - 26, theme.palette.mutedText, false)
        super.render(gui, mouseX, mouseY, partialTick)
        renderSpeciesSuggestions(gui, theme, mouseX, mouseY)
        renderAspectSuggestions(gui, theme, mouseX, mouseY)
    }

    private fun renderSpeciesSuggestions(gui: GuiGraphics, theme: HubTheme, mouseX: Int, mouseY: Int) {
        if (!speciesBox.isFocused || speciesBox.value.length < 1) return
        val q = speciesBox.value.lowercase()
        val suggestions = PokemonSpecies.implemented.asSequence()
            .filter { it.resourceIdentifier.toString().lowercase().contains(q) || it.translatedName.string.lowercase().contains(q) }
            .take(6).toList()
        var y = speciesBox.y + 22
        suggestions.forEach { species ->
            val x = speciesBox.x
            val w = speciesBox.width
            PixelUi.panel(gui, x, y, w, 20, 0xF21A2436.toInt(), theme.palette.accent2)
            val label = "${species.translatedName.string}  •  ${species.resourceIdentifier}"
            gui.drawString(font, font.plainSubstrByWidth(label, w - 10), x + 5, y + 6, theme.palette.text, false)
            hits += Hit(x, y, x + w, y + 20) {
                speciesBox.value = species.resourceIdentifier.toString()
                update { it.copy(species = species.resourceIdentifier.toString()) }
            }
            y += 21
        }
    }

    private fun renderAspectSuggestions(gui: GuiGraphics, theme: HubTheme, mouseX: Int, mouseY: Int) {
        if (!aspectsBox.isFocused) return
        val species = ResourceLocation.tryParse(speciesBox.value)?.let(PokemonSpecies::getByIdentifier) ?: return
        val known = species.forms.flatMap { it.aspects }.distinct().filter(String::isNotBlank).take(8)
        if (known.isEmpty()) return
        var x = aspectsBox.x
        val y = aspectsBox.y + 23
        known.forEach { aspect ->
            val w = (font.width(aspect) + 12).coerceAtMost(110)
            if (x + w > width - 12) return@forEach
            PixelUi.panel(gui, x, y, w, 20, 0xF21A2436.toInt(), theme.palette.accent)
            gui.drawCenteredString(font, font.plainSubstrByWidth(aspect, w - 8), x + w / 2, y + 6, theme.palette.text)
            hits += Hit(x, y, x + w, y + 20) {
                val values = (aspectsBox.value.split(',').map(String::trim).filter(String::isNotBlank) + aspect).distinct()
                aspectsBox.value = values.joinToString(",")
            }
            x += w + 5
        }
    }

    private fun addEntry() {
        val entries = draft.cobblemonWiki.explicitFakemon.toMutableList()
        val n = entries.size + 1
        entries += FakemonEntry("fakemon_$n", "cobblemon:pikachu", displayName = LocalizedText.of("Fakemon mới"))
        draft = draft.copy(cobblemonWiki = draft.cobblemonWiki.copy(explicitFakemon = entries))
        selected = entries.lastIndex
        syncFields()
    }

    private fun deleteEntry() {
        if (selected < 0) return
        val entries = draft.cobblemonWiki.explicitFakemon.toMutableList()
        if (selected in entries.indices) entries.removeAt(selected)
        draft = draft.copy(cobblemonWiki = draft.cobblemonWiki.copy(explicitFakemon = entries))
        selected = if (entries.isEmpty()) -1 else selected.coerceAtMost(entries.lastIndex)
        syncFields()
    }

    private fun update(transform: (FakemonEntry) -> FakemonEntry) {
        if (syncing || selected < 0) return
        val entries = draft.cobblemonWiki.explicitFakemon.toMutableList()
        val current = entries.getOrNull(selected) ?: return
        entries[selected] = transform(current)
        draft = draft.copy(cobblemonWiki = draft.cobblemonWiki.copy(explicitFakemon = entries))
    }

    private fun syncFields() {
        if (!::idBox.isInitialized) return
        syncing = true
        val entry = draft.cobblemonWiki.explicitFakemon.getOrNull(selected)
        idBox.value = entry?.id.orEmpty()
        speciesBox.value = entry?.species.orEmpty()
        aspectsBox.value = entry?.aspects?.joinToString(",").orEmpty()
        nameBox.value = entry?.displayName?.resolve("vi_vn").orEmpty()
        wikiPageBox.value = entry?.wikiPage.orEmpty()
        val enabled = entry != null
        idBox.setEditable(enabled)
        speciesBox.setEditable(enabled)
        aspectsBox.setEditable(enabled)
        nameBox.setEditable(enabled)
        wikiPageBox.setEditable(enabled)
        syncing = false
    }

    private fun sanitizeId(value: String, old: String, species: String): String {
        val cleaned = value.lowercase().replace(Regex("[^a-z0-9_.:/-]+"), "_").trim('_')
        return cleaned.ifBlank { old.ifBlank { species.substringAfter(':', "fakemon") } }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) hits.asReversed().firstOrNull { it.contains(mouseX, mouseY) }?.let { it.run(); return true }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
