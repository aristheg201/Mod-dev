package io.github.aristheg201.svhub.client.editor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.gui.SVHubScreen
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/** Generic, schema-driven component editor. */
class ComponentInspectorScreen(
    private val parent: HubEditorScreen,
    source: HubComponent
) : SVHubScreen(Component.literal("SVHub Component Inspector")) {
    private data class BoundField(val spec: InspectorFieldSpec, val box: EditBox)

    private val original = source
    private val schema = ComponentInspectorSchemas.forType(source.type)
    private val fields = mutableListOf<BoundField>()
    private var actionType: EditBox? = null
    private var actionValue: EditBox? = null
    private var actionPermission: EditBox? = null
    private var actionCooldown: EditBox? = null
    private var speciesSuggestionHits = mutableListOf<Pair<IntRange, String>>()
    private var validationMessage: String? = null

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("Hủy")) { Minecraft.getInstance().setScreen(parent) }.bounds(width - 142, 10, 58, 22).build())
        addRenderableWidget(Button.builder(Component.literal("Xong")) { saveAndReturn() }.bounds(width - 76, 10, 60, 22).build())

        val startY = 58
        val fieldWidth = (width * 0.55).toInt().coerceAtLeast(180)
        val x = width - fieldWidth - 24
        schema.fields.take(7).forEachIndexed { index, spec ->
            val box = EditBox(font, x, startY + index * 38, fieldWidth, 20, Component.literal(spec.label))
            box.setHint(Component.literal(spec.placeholder.ifBlank { spec.label }))
            box.setMaxLength(spec.maxLength)
            box.value = original.props.get(spec.key)?.let { runCatching { it.asString }.getOrDefault("") }.orEmpty()
            box.setResponder { validationMessage = null }
            addRenderableWidget(box)
            fields += BoundField(spec, box)
        }

        if (schema.supportsAction) {
            val actionY = startY + fields.size * 38 + 18
            actionType = actionField(x, actionY, fieldWidth, "Action type", original.action?.type.orEmpty())
            actionValue = actionField(x, actionY + 38, fieldWidth, "Action value", original.action?.value.orEmpty())
            actionPermission = actionField(x, actionY + 76, fieldWidth, "Permission (optional)", original.action?.permission.orEmpty())
            actionCooldown = actionField(x, actionY + 114, fieldWidth, "Cooldown ms", (original.action?.cooldownMs ?: 500L).toString())
        }
    }

    private fun actionField(x: Int, y: Int, width: Int, hint: String, value: String): EditBox {
        val box = EditBox(font, x, y, width, 20, Component.literal(hint))
        box.setHint(Component.literal(hint))
        box.setMaxLength(1024)
        box.value = value
        box.setResponder { validationMessage = null }
        addRenderableWidget(box)
        return box
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val content = parent.draftForChild()
        val theme = content.themes[content.defaultTheme] ?: HubTheme("editor")
        PixelUi.background(gui, width, height, content, theme, System.currentTimeMillis() / 50)
        gui.fill(0, 0, width, 42, 0xE80B0F18.toInt())
        gui.drawString(font, "COMPONENT: ${original.type}", 18, 17, theme.palette.accent, true)

        val x = width - (width * 0.55).toInt().coerceAtLeast(180) - 24
        fields.forEach { bound ->
            gui.drawString(font, bound.spec.label, 18, bound.box.y + 6, theme.palette.mutedText, false)
            if (bound.spec.type != InspectorValueType.TEXT) {
                gui.drawString(font, bound.spec.type.name.lowercase(), x - 88, bound.box.y + 6, theme.palette.accent2, false)
            }
        }
        if (schema.supportsAction) {
            actionType?.let { gui.drawString(font, "Action type", 18, it.y + 6, theme.palette.mutedText, false) }
            actionValue?.let { gui.drawString(font, "Action value", 18, it.y + 6, theme.palette.mutedText, false) }
            actionPermission?.let { gui.drawString(font, "Permission", 18, it.y + 6, theme.palette.mutedText, false) }
            actionCooldown?.let { gui.drawString(font, "Cooldown", 18, it.y + 6, theme.palette.mutedText, false) }
        }

        validationMessage?.let { message ->
            gui.drawString(
                font,
                font.plainSubstrByWidth(message, width - 36),
                18,
                height - 24,
                theme.palette.danger,
                false
            )
        }

        super.render(gui, mouseX, mouseY, partialTick)
        renderSpeciesSuggestions(gui, theme)
    }

    private fun renderSpeciesSuggestions(gui: GuiGraphics, theme: HubTheme) {
        speciesSuggestionHits.clear()
        val speciesField = fields.firstOrNull { it.spec.key == "species" } ?: return
        val box = speciesField.box
        if (!box.isFocused || box.value.isBlank()) return
        val query = box.value.lowercase()
        val suggestions = PokemonSpecies.implemented.asSequence()
            .filter {
                it.resourceIdentifier.toString().lowercase().contains(query) ||
                    it.translatedName.string.lowercase().contains(query)
            }
            .take(6)
            .toList()
        var y = box.y + 22
        suggestions.forEach { species ->
            val id = species.resourceIdentifier.toString()
            PixelUi.panel(gui, box.x, y, box.width, 19, 0xF21A2436.toInt(), theme.palette.accent2)
            gui.drawString(font, font.plainSubstrByWidth("${species.translatedName.string} • $id", box.width - 10), box.x + 5, y + 6, theme.palette.text, false)
            speciesSuggestionHits += (y until y + 19) to id
            y += 20
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val speciesField = fields.firstOrNull { it.spec.key == "species" }
            if (speciesField != null && mouseX >= speciesField.box.x && mouseX < speciesField.box.x + speciesField.box.width) {
                val hit = speciesSuggestionHits.firstOrNull { mouseY.toInt() in it.first }
                if (hit != null) {
                    speciesField.box.value = hit.second
                    validationMessage = null
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun saveAndReturn() {
        validationMessage = validateInputs()
        if (validationMessage != null) return

        val props = original.props.deepCopy()
        fields.forEach { bound -> writeProperty(props, bound.spec, bound.box.value) }
        val action = if (schema.supportsAction) {
            val type = actionType?.value?.trim().orEmpty()
            if (type.isBlank()) null else HubActionSpec(
                id = original.action?.id ?: "action_${System.nanoTime()}",
                type = type,
                value = actionValue?.value?.trim().orEmpty(),
                permission = actionPermission?.value?.trim()?.ifBlank { null },
                cooldownMs = actionCooldown?.value?.trim()?.toLongOrNull() ?: 500L
            )
        } else original.action

        val replacement = original.copy(props = props, action = action)
        val content = parent.draftForChild()
        val pages = content.pages.map { page ->
            page.copy(components = page.components.map { component -> if (component.id == original.id) replacement else component })
        }
        parent.acceptAssetDraft(content.copy(pages = pages))
        Minecraft.getInstance().setScreen(parent)
    }

    private fun validateInputs(): String? {
        for (bound in fields) {
            val raw = bound.box.value.trim()
            if (raw.isBlank()) continue
            when (bound.spec.type) {
                InspectorValueType.INTEGER -> if (raw.toIntOrNull() == null) {
                    return "${bound.spec.label}: phải là số nguyên hợp lệ."
                }
                InspectorValueType.FLOAT -> if (raw.toFloatOrNull()?.isFinite() != true) {
                    return "${bound.spec.label}: phải là số thực hữu hạn hợp lệ."
                }
                InspectorValueType.BOOLEAN -> if (!raw.equals("true", true) && !raw.equals("false", true)) {
                    return "${bound.spec.label}: chỉ chấp nhận true hoặc false."
                }
                else -> Unit
            }
        }

        if (fields.any { it.spec.key == "species" }) {
            val raw = fields.first { it.spec.key == "species" }.box.value.trim()
            if (raw.isNotBlank()) {
                val found = PokemonSpecies.implemented.any { it.resourceIdentifier.toString() == raw }
                if (!found) return "Species '$raw' không tồn tại trong Cobblemon registry hiện tại."
            }
        }

        if (schema.supportsAction) {
            val cooldownRaw = actionCooldown?.value?.trim().orEmpty()
            val cooldown = cooldownRaw.toLongOrNull()
            if (cooldown == null || cooldown !in 0L..300_000L) {
                return "Cooldown phải nằm trong khoảng 0–300,000 ms (5 phút)."
            }
            val type = actionType?.value?.trim().orEmpty()
            val value = actionValue?.value?.trim().orEmpty()
            if (type.isNotBlank() && value.length > 2048) return "Action value quá dài."
        }
        return null
    }

    private fun writeProperty(props: JsonObject, spec: InspectorFieldSpec, raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) {
            props.remove(spec.key)
            return
        }
        when (spec.type) {
            InspectorValueType.INTEGER -> props.addProperty(spec.key, value.toInt())
            InspectorValueType.FLOAT -> props.addProperty(spec.key, value.toFloat())
            InspectorValueType.BOOLEAN -> props.addProperty(spec.key, value.equals("true", true))
            else -> props.addProperty(spec.key, raw)
        }
    }
}
