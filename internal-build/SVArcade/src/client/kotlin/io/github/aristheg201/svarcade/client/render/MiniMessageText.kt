package io.github.aristheg201.svarcade.client.render

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.LinkedHashMap
import kotlin.math.floor

/**
 * Safe presentation-markup -> vanilla Component bridge for client GUI text.
 *
 * This intentionally has zero Adventure/MiniMessage runtime dependency. Keeping
 * net.kyori.* out of the SVArcade JAR prevents version skew with server mods that own
 * their own Adventure Fabric platform (boss bars, audiences, etc.). Only passive
 * presentation tags are accepted; command/click/hover/NBT/selector tags are never
 * interpreted as actions.
 */
object MiniMessageText {
    private data class MarkupState(
        val color: Int? = null,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underlined: Boolean? = null,
        val strikethrough: Boolean? = null,
        val obfuscated: Boolean? = null,
        val gradient: List<Int>? = null,
        val rainbow: Boolean = false
    )

    private data class StackEntry(val name: String, val previous: MarkupState)

    private val cache = object : LinkedHashMap<String, Component>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Component>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    @Synchronized
    fun component(markup: String): Component {
        cache[markup]?.let { return it }
        val converted = runCatching { parse(markup) }.getOrElse { Component.literal(markup) }
        cache[markup] = converted
        return converted
    }

    fun split(font: Font, markup: String, width: Int) = font.split(component(markup), width)

    fun width(font: Font, markup: String): Int = font.width(component(markup))

    fun draw(
        gui: GuiGraphics,
        font: Font,
        markup: String,
        x: Int,
        y: Int,
        fallbackColor: Int,
        shadow: Boolean = false
    ): Int = gui.drawString(font, component(markup), x, y, fallbackColor, shadow)

    fun drawCentered(
        gui: GuiGraphics,
        font: Font,
        markup: String,
        centerX: Int,
        y: Int,
        fallbackColor: Int,
        shadow: Boolean = false
    ) {
        val text = component(markup)
        gui.drawString(font, text, centerX - font.width(text) / 2, y, fallbackColor, shadow)
    }

    @Synchronized
    fun clear() = cache.clear()

    private fun parse(markup: String): MutableComponent {
        val root = Component.empty()
        val stack = ArrayList<StackEntry>()
        var state = MarkupState()
        var cursor = 0

        TAG.findAll(markup).forEach { match ->
            if (match.range.first > cursor) appendText(root, markup.substring(cursor, match.range.first), state)
            val raw = match.groupValues[1].trim()
            val lower = raw.lowercase()

            when {
                lower.startsWith("/") -> {
                    val close = canonicalClose(lower.substring(1))
                    if (close == null) appendText(root, match.value, state)
                    else {
                        val index = stack.indexOfLast { it.name == close }
                        if (index >= 0) {
                            state = stack[index].previous
                            while (stack.size > index) stack.removeAt(stack.lastIndex)
                        }
                    }
                }
                lower == "reset" -> {
                    stack.clear()
                    state = MarkupState()
                }
                lower == "newline" || lower == "br" -> appendText(root, "\n", state)
                lower == "bold" || lower == "b" -> state = push(stack, "bold", state, state.copy(bold = true))
                lower == "italic" || lower == "i" || lower == "em" -> state = push(stack, "italic", state, state.copy(italic = true))
                lower == "underlined" || lower == "underline" || lower == "u" -> state = push(stack, "underlined", state, state.copy(underlined = true))
                lower == "strikethrough" || lower == "st" -> state = push(stack, "strikethrough", state, state.copy(strikethrough = true))
                lower == "obfuscated" || lower == "obf" -> state = push(stack, "obfuscated", state, state.copy(obfuscated = true))
                lower.startsWith("gradient:") -> {
                    val colors = raw.substringAfter(':').split(':').mapNotNull(::parseColor)
                    if (colors.size >= 2) state = push(stack, "gradient", state, state.copy(gradient = colors, rainbow = false))
                    else appendText(root, match.value, state)
                }
                lower == "rainbow" || lower.startsWith("rainbow:") ->
                    state = push(stack, "rainbow", state, state.copy(gradient = null, rainbow = true))
                lower.startsWith("color:") -> {
                    val color = parseColor(raw.substringAfter(':'))
                    if (color != null) state = push(stack, "color", state, state.copy(color = color, gradient = null, rainbow = false))
                    else appendText(root, match.value, state)
                }
                else -> {
                    val color = parseColor(raw)
                    if (color != null) state = push(stack, "color", state, state.copy(color = color, gradient = null, rainbow = false))
                    else appendText(root, match.value, state)
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < markup.length) appendText(root, markup.substring(cursor), state)
        return root
    }

    private fun push(stack: MutableList<StackEntry>, name: String, previous: MarkupState, next: MarkupState): MarkupState {
        stack += StackEntry(name, previous)
        return next
    }

    private fun canonicalClose(raw: String): String? {
        val name = raw.substringBefore(':').trim().lowercase()
        return when {
            name == "bold" || name == "b" -> "bold"
            name == "italic" || name == "i" || name == "em" -> "italic"
            name == "underlined" || name == "underline" || name == "u" -> "underlined"
            name == "strikethrough" || name == "st" -> "strikethrough"
            name == "obfuscated" || name == "obf" -> "obfuscated"
            name == "gradient" -> "gradient"
            name == "rainbow" -> "rainbow"
            name == "color" || parseColor(name) != null -> "color"
            else -> null
        }
    }

    private fun appendText(root: MutableComponent, text: String, state: MarkupState) {
        if (text.isEmpty()) return
        val gradient = state.gradient
        when {
            gradient != null && gradient.size >= 2 -> appendGradient(root, text, state, gradient)
            state.rainbow -> appendRainbow(root, text, state)
            else -> root.append(styled(text, state))
        }
    }

    private fun styled(text: String, state: MarkupState, forcedColor: Int? = null): MutableComponent {
        val out = Component.literal(text)
        var style = Style.EMPTY
        (forcedColor ?: state.color)?.let { style = style.withColor(TextColor.fromRgb(it)) }
        state.bold?.let { style = style.withBold(it) }
        state.italic?.let { style = style.withItalic(it) }
        state.underlined?.let { style = style.withUnderlined(it) }
        state.strikethrough?.let { style = style.withStrikethrough(it) }
        state.obfuscated?.let { style = style.withObfuscated(it) }
        out.style = style
        return out
    }

    private fun appendGradient(root: MutableComponent, text: String, state: MarkupState, colors: List<Int>) {
        val points = text.codePoints().toArray()
        if (points.isEmpty()) return
        points.forEachIndexed { index, codePoint ->
            val position = if (points.size <= 1) 0.0 else index.toDouble() / (points.size - 1).toDouble()
            root.append(styled(String(Character.toChars(codePoint)), state, gradientColor(colors, position)))
        }
    }

    private fun appendRainbow(root: MutableComponent, text: String, state: MarkupState) {
        val points = text.codePoints().toArray()
        if (points.isEmpty()) return
        points.forEachIndexed { index, codePoint ->
            val hue = if (points.size <= 1) 0.0 else index.toDouble() / points.size.toDouble()
            root.append(styled(String(Character.toChars(codePoint)), state, hsvToRgb(hue, 0.82, 1.0)))
        }
    }

    private fun gradientColor(colors: List<Int>, position: Double): Int {
        if (colors.size == 1) return colors[0]
        val scaled = position.coerceIn(0.0, 1.0) * (colors.size - 1)
        val segment = floor(scaled).toInt().coerceIn(0, colors.size - 2)
        val t = scaled - segment
        return mix(colors[segment], colors[segment + 1], t)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun channel(shift: Int): Int {
            val av = (a shr shift) and 0xFF
            val bv = (b shr shift) and 0xFF
            return (av + (bv - av) * t).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun hsvToRgb(h: Double, s: Double, v: Double): Int {
        val hue = ((h % 1.0) + 1.0) % 1.0
        val scaled = hue * 6.0
        val sector = floor(scaled).toInt() % 6
        val f = scaled - floor(scaled)
        val p = v * (1.0 - s)
        val q = v * (1.0 - f * s)
        val t = v * (1.0 - (1.0 - f) * s)
        val (r, g, b) = when (sector) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        fun c(value: Double) = (value * 255.0).toInt().coerceIn(0, 255)
        return (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    private fun parseColor(raw: String): Int? {
        val value = raw.trim().lowercase()
        if (value.startsWith("#") && value.length == 7) return value.substring(1).toIntOrNull(16)
        return NAMED_COLORS[value]
    }

    private val TAG = Regex("<([^<>]+)>")
    private val NAMED_COLORS = mapOf(
        "black" to 0x000000,
        "dark_blue" to 0x0000AA,
        "dark_green" to 0x00AA00,
        "dark_aqua" to 0x00AAAA,
        "dark_red" to 0xAA0000,
        "dark_purple" to 0xAA00AA,
        "gold" to 0xFFAA00,
        "gray" to 0xAAAAAA,
        "grey" to 0xAAAAAA,
        "dark_gray" to 0x555555,
        "dark_grey" to 0x555555,
        "blue" to 0x5555FF,
        "green" to 0x55FF55,
        "aqua" to 0x55FFFF,
        "red" to 0xFF5555,
        "light_purple" to 0xFF55FF,
        "yellow" to 0xFFFF55,
        "white" to 0xFFFFFF
    )
    private const val MAX_CACHE_ENTRIES = 2048
}
