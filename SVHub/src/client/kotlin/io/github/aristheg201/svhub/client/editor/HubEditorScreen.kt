package io.github.aristheg201.svhub.client.editor

import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.gui.HubScreen
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.*
import io.github.aristheg201.svhub.network.HubEditorChunkC2S
import io.github.aristheg201.svhub.util.Compression
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.concurrent.ThreadLocalRandom

class HubEditorScreen : Screen(Component.literal("SVHub Editor")) {
    private val initial = ClientHubState.editorContent ?: ClientHubState.playerContent ?: DefaultContent.create()
    private val history = HubDraftHistory(initial)
    private var draft = history.current()
    private var pageIndex = 0
    private var componentIndex = -1
    private var syncing = false
    private var publishing = false

    private lateinit var titleBox: EditBox
    private lateinit var routeBox: EditBox
    private lateinit var categoryBox: EditBox
    private lateinit var primaryBox: EditBox
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("+ Page")) { addPage() }.bounds(8, 8, 54, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+ Block")) { Minecraft.getInstance().setScreen(ComponentPickerScreen(this)) }.bounds(66, 8, 58, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Xóa")) { deleteSelected() }.bounds(128, 8, 42, 20).build())
        addRenderableWidget(Button.builder(Component.literal("↑")) { moveSelected(-1) }.bounds(174, 8, 24, 20).build())
        addRenderableWidget(Button.builder(Component.literal("↓")) { moveSelected(1) }.bounds(202, 8, 24, 20).build())
        undoButton = addRenderableWidget(Button.builder(Component.literal("Undo")) { undo() }.bounds(230, 8, 44, 20).build())
        redoButton = addRenderableWidget(Button.builder(Component.literal("Redo")) { redo() }.bounds(278, 8, 44, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Assets")) { Minecraft.getInstance().setScreen(AssetStudioScreen(this, draft, pageIndex)) }.bounds(326, 8, 50, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Fakemon")) { Minecraft.getInstance().setScreen(FakemonEditorScreen(this, draft)) }.bounds(380, 8, 58, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Preview")) {
            Minecraft.getInstance().setScreen(HubScreen(draft.pages.getOrNull(pageIndex)?.route ?: "home", clone(draft), this))
        }.bounds(442, 8, 58, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Publish")) { publish() }.bounds(504, 8, 62, 20).build())

        titleBox = field(44, "Tiêu đề") { value -> mutatePage { it.copy(title = LocalizedText(it.title.values + ("vi_vn" to value))) } }
        routeBox = field(70, "Route") { value -> mutatePage { it.copy(route = value.trim()) } }
        categoryBox = field(96, "Category") { value -> mutatePage { it.copy(category = value.trim()) } }
        primaryBox = field(150, "Nội dung component") { value -> mutateComponentProps { props -> props.addProperty("text", value) } }
        syncFields()
    }

    private fun field(y: Int, hint: String, onChange: (String) -> Unit): EditBox {
        val x = (width * .68).toInt()
        val box = EditBox(font, x, y, width - x - 12, 20, Component.literal(hint))
        box.setHint(Component.literal(hint))
        box.setMaxLength(4096)
        box.setResponder { if (!syncing) onChange(it) }
        addRenderableWidget(box)
        return box
    }

    override fun tick() {
        undoButton.active = history.canUndo() && !publishing
        redoButton.active = history.canRedo() && !publishing
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val theme = draft.themes[draft.defaultTheme] ?: HubTheme("editor")
        PixelUi.background(gui, width, height, draft, theme, System.currentTimeMillis() / 50)
        gui.fill(0, 0, width, 34, 0xE80B0F18.toInt())
        val left = (width * .25).toInt()
        val mid = (width * .65).toInt()
        gui.drawString(font, "PAGES", 12, 42, theme.palette.accent, true)
        gui.drawString(font, "COMPONENTS", left + 12, 42, theme.palette.accent, true)
        gui.drawString(font, "INSPECTOR", mid + 12, 42, theme.palette.accent, true)

        var y = 58
        draft.pages.forEachIndexed { index, page ->
            if (y < height - 20) {
                val active = index == pageIndex
                if (active) gui.fill(8, y, left - 6, y + 22, 0x88314A68.toInt())
                gui.drawString(font, page.title.resolve(draft.defaultLocale).take(22), 14, y + 7, if (active) theme.palette.accent else theme.palette.text, false)
                y += 24
            }
        }

        y = 58
        draft.pages.getOrNull(pageIndex)?.components?.forEachIndexed { index, component ->
            if (y < height - 20) {
                val active = index == componentIndex
                if (active) gui.fill(left + 8, y, mid - 6, y + 24, 0x88314A68.toInt())
                gui.drawString(font, "${index + 1}. ${component.type}", left + 14, y + 8, if (active) theme.palette.accent else theme.palette.text, false)
                y += 26
            }
        }

        if (publishing) gui.drawString(font, "ĐANG PUBLISH...", mid + 12, height - 38, theme.palette.accent, true)
        ClientHubState.lastEditorMessage?.let { gui.drawString(font, it.take(70), mid + 12, height - 24, theme.palette.accent, false) }
        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && mouseY >= 58) {
            val left = (width * .25).toInt()
            val mid = (width * .65).toInt()
            if (mouseX < left) {
                val index = ((mouseY - 58) / 24).toInt()
                if (index in draft.pages.indices) {
                    pageIndex = index
                    componentIndex = -1
                    syncFields()
                    return true
                }
            } else if (mouseX < mid) {
                val indices = draft.pages.getOrNull(pageIndex)?.components?.indices ?: IntRange.EMPTY
                val index = ((mouseY - 58) / 26).toInt()
                if (index in indices) {
                    componentIndex = index
                    syncFields()
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val control = modifiers and 2 != 0
        if (control && keyCode == 90) {
            if (modifiers and 1 != 0) redo() else undo()
            return true
        }
        if (control && keyCode == 89) {
            redo()
            return true
        }
        if (keyCode == 261) {
            deleteSelected()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    internal fun draftForChild(): HubContent = clone(draft)

    internal fun acceptAssetDraft(value: HubContent) {
        replaceDraft(value)
    }

    internal fun acceptFakemonDraft(value: HubContent) {
        replaceDraft(value)
    }

    internal fun addPickedComponent(type: String) {
        val props = JsonObject()
        when (type) {
            "heading", "text", "markdown", "animated_text", "notice" -> props.addProperty("text", "Nội dung mới")
            "button" -> props.addProperty("label", "Nút mới")
            "grid" -> props.addProperty("provider", "svhub:commands")
            "pokemon_model" -> props.addProperty("species", "cobblemon:pikachu")
            "image", "pixel_image", "animated_image" -> props.addProperty("asset", "svhub:banner_home")
        }
        val component = HubComponent(
            "component_${System.nanoTime()}",
            type,
            props,
            action = if (type == "button") HubActionSpec("action_${System.nanoTime()}", "open_page", "home") else null
        )
        val page = draft.pages.getOrNull(pageIndex) ?: return
        replaceDraft(draft.copy(pages = draft.pages.toMutableList().also { pages ->
            pages[pageIndex] = page.copy(components = page.components + component)
        }))
        componentIndex = draft.pages[pageIndex].components.lastIndex
        syncFields()
    }

    private fun addPage() {
        val sequence = generateSequence(draft.pages.size + 1) { it + 1 }
            .first { n -> draft.pages.none { it.id == "page_$n" || it.route == "page/$n" } }
        val pages = draft.pages + HubPage("page_$sequence", "page/$sequence", "custom", LocalizedText.of("Trang mới"))
        replaceDraft(draft.copy(pages = pages))
        pageIndex = pages.lastIndex
        componentIndex = -1
        syncFields()
    }

    private fun deleteSelected() {
        if (publishing) return
        val page = draft.pages.getOrNull(pageIndex) ?: return
        if (componentIndex in page.components.indices) {
            val components = page.components.toMutableList().also { it.removeAt(componentIndex) }
            val pages = draft.pages.toMutableList().also { it[pageIndex] = page.copy(components = components) }
            replaceDraft(draft.copy(pages = pages))
            componentIndex = if (components.isEmpty()) -1 else componentIndex.coerceAtMost(components.lastIndex)
        } else {
            if (draft.pages.size <= 1) {
                ClientHubState.lastEditorMessage = "Hub phải còn ít nhất một page."
                return
            }
            val pages = draft.pages.toMutableList().also { it.removeAt(pageIndex) }
            replaceDraft(draft.copy(pages = pages))
            pageIndex = pageIndex.coerceAtMost(pages.lastIndex)
            componentIndex = -1
        }
        syncFields()
    }

    private fun moveSelected(delta: Int) {
        if (publishing || delta == 0) return
        val page = draft.pages.getOrNull(pageIndex) ?: return
        if (componentIndex in page.components.indices) {
            val target = componentIndex + delta
            if (target !in page.components.indices) return
            val components = page.components.toMutableList()
            val value = components.removeAt(componentIndex)
            components.add(target, value)
            val pages = draft.pages.toMutableList().also { it[pageIndex] = page.copy(components = components) }
            replaceDraft(draft.copy(pages = pages))
            componentIndex = target
        } else {
            val target = pageIndex + delta
            if (target !in draft.pages.indices) return
            val pages = draft.pages.toMutableList()
            val value = pages.removeAt(pageIndex)
            pages.add(target, value)
            replaceDraft(draft.copy(pages = pages))
            pageIndex = target
        }
        syncFields()
    }

    private fun mutatePage(transform: (HubPage) -> HubPage) {
        if (syncing || publishing) return
        val page = draft.pages.getOrNull(pageIndex) ?: return
        val pages = draft.pages.toMutableList().also { it[pageIndex] = transform(page) }
        replaceDraft(draft.copy(pages = pages), sync = false)
    }

    private fun mutateComponentProps(transform: (JsonObject) -> Unit) {
        if (syncing || publishing) return
        val page = draft.pages.getOrNull(pageIndex) ?: return
        val components = page.components.toMutableList()
        val component = components.getOrNull(componentIndex) ?: return
        val props = component.props.deepCopy()
        transform(props)
        components[componentIndex] = component.copy(props = props)
        val pages = draft.pages.toMutableList().also { it[pageIndex] = page.copy(components = components) }
        replaceDraft(draft.copy(pages = pages), sync = false)
    }

    private fun replaceDraft(value: HubContent, sync: Boolean = true) {
        draft = history.replace(value)
        if (sync) syncFields()
    }

    private fun undo() {
        if (publishing || !history.canUndo()) return
        draft = history.undo()
        clampSelection()
        syncFields()
    }

    private fun redo() {
        if (publishing || !history.canRedo()) return
        draft = history.redo()
        clampSelection()
        syncFields()
    }

    private fun clampSelection() {
        pageIndex = pageIndex.coerceIn(0, draft.pages.lastIndex.coerceAtLeast(0))
        val components = draft.pages.getOrNull(pageIndex)?.components.orEmpty()
        componentIndex = if (components.isEmpty()) -1 else componentIndex.coerceIn(-1, components.lastIndex)
    }

    private fun syncFields() {
        if (!::titleBox.isInitialized) return
        syncing = true
        val page = draft.pages.getOrNull(pageIndex)
        titleBox.value = page?.title?.resolve("vi_vn").orEmpty()
        routeBox.value = page?.route.orEmpty()
        categoryBox.value = page?.category.orEmpty()
        primaryBox.value = page?.components?.getOrNull(componentIndex)?.props?.get("text")?.let { runCatching { it.asString }.getOrDefault("") }.orEmpty()
        val hasComponent = page?.components?.getOrNull(componentIndex) != null
        primaryBox.setEditable(hasComponent)
        syncing = false
    }

    private fun publish() {
        if (publishing) return
        val validation = HubValidator.validate(draft)
        if (!validation.ok) {
            ClientHubState.lastEditorMessage = validation.errors.take(3).joinToString(" | ")
            return
        }
        val encoded = Compression.encodeUtf8(HubContentCodec.encode(draft.copy(revision = ClientHubState.serverRevision)))
        val chunks = Compression.chunks(encoded)
        val transfer = ThreadLocalRandom.current().nextLong()
        publishing = true
        ClientHubState.lastEditorMessage = "Đang gửi revision ${ClientHubState.serverRevision}..."
        chunks.forEachIndexed { index, chunk ->
            ClientPlayNetworking.send(HubEditorChunkC2S(transfer, ClientHubState.serverRevision, index, chunks.size, chunk))
        }
        // Result packet refreshes the editor snapshot. This flag prevents accidental duplicate publish clicks in the meantime.
    }

    companion object {
        fun clone(content: HubContent): HubContent = HubDraftHistory.clone(content)
    }
}
