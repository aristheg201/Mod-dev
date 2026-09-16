package io.github.aristheg201.svhub.client.render

import net.kyori.adventure.text.Component as AdventureComponent
import net.kyori.adventure.text.TextComponent as AdventureTextComponent
import net.kyori.adventure.text.format.Style as AdventureStyle
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component as MinecraftComponent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style as MinecraftStyle
import net.minecraft.network.chat.TextColor as MinecraftTextColor
import java.util.LinkedHashMap

/**
 * Safe MiniMessage -> vanilla Component bridge for client GUI text.
 *
 * Only presentation tags are enabled. Click, hover, insertion, NBT, selector,
 * score, keybind and font tags are intentionally excluded so rich text can never
 * become a second command/action channel around SVHub's authoritative action IDs.
 */
object MiniMessageText {
    private val safeTags: TagResolver = TagResolver.builder()
        .resolvers(
            StandardTags.color(),
            StandardTags.decorations(),
            StandardTags.gradient(),
            StandardTags.rainbow(),
            StandardTags.reset(),
            StandardTags.newline()
        )
        .build()

    private val miniMessage: MiniMessage = MiniMessage.builder()
        .tags(safeTags)
        .strict(false)
        .build()

    private val cache = object : LinkedHashMap<String, MinecraftComponent>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MinecraftComponent>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    @Synchronized
    fun component(markup: String): MinecraftComponent {
        cache[markup]?.let { return it }
        val converted = runCatching { convert(miniMessage.deserialize(markup)) }
            .getOrElse { MinecraftComponent.literal(markup) }
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

    private fun convert(component: AdventureComponent): MutableComponent {
        // With the restricted tag resolver MiniMessage emits TextComponents. Keep a
        // defensive empty fallback for any future safe component type.
        val root = if (component is AdventureTextComponent) {
            MinecraftComponent.literal(component.content())
        } else {
            MinecraftComponent.empty()
        }
        root.style = convertStyle(component.style())
        component.children().forEach { child -> root.append(convert(child)) }
        return root
    }

    private fun convertStyle(style: AdventureStyle): MinecraftStyle {
        var result = MinecraftStyle.EMPTY
        style.color()?.let { result = result.withColor(MinecraftTextColor.fromRgb(it.value())) }
        result = applyDecoration(result, style, TextDecoration.BOLD) { base, value -> base.withBold(value) }
        result = applyDecoration(result, style, TextDecoration.ITALIC) { base, value -> base.withItalic(value) }
        result = applyDecoration(result, style, TextDecoration.UNDERLINED) { base, value -> base.withUnderlined(value) }
        result = applyDecoration(result, style, TextDecoration.STRIKETHROUGH) { base, value -> base.withStrikethrough(value) }
        result = applyDecoration(result, style, TextDecoration.OBFUSCATED) { base, value -> base.withObfuscated(value) }
        return result
    }

    private inline fun applyDecoration(
        base: MinecraftStyle,
        style: AdventureStyle,
        decoration: TextDecoration,
        apply: (MinecraftStyle, Boolean) -> MinecraftStyle
    ): MinecraftStyle = when (style.decoration(decoration)) {
        TextDecoration.State.TRUE -> apply(base, true)
        TextDecoration.State.FALSE -> apply(base, false)
        TextDecoration.State.NOT_SET -> base
    }

    private const val MAX_CACHE_ENTRIES = 2048
}
