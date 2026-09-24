package io.github.aristheg201.svarcade.server

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.Placeholders
import io.github.aristheg201.svarcade.content.FakemonEntry
import io.github.aristheg201.svarcade.content.HubComponent
import io.github.aristheg201.svarcade.content.HubContent
import io.github.aristheg201.svarcade.content.HubPage
import io.github.aristheg201.svarcade.content.LocalizedText
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Resolves Fabric Text Placeholder API values on the server before a player
 * snapshot is encoded. MiniMessage formatting is intentionally left intact and
 * is rendered client-side.
 *
 * Editor snapshots are never passed through this class: editors must receive the
 * canonical tokens, not a player-specific materialized copy that could later be
 * published back into config.
 */
object HubPlaceholderResolver {
    private val placeholderToken = Regex("%[a-zA-Z0-9_.-]+:[^%\\r\\n]+%")

    /** True means a disk cache keyed only by content revision would become stale. */
    fun hasDynamicPlaceholders(content: HubContent): Boolean = sequence {
        content.pages.forEach { page ->
            yieldAll(page.title.values.values)
            yieldAll(page.subtitle.values.values)
            page.components.forEach { component -> yieldAll(strings(component.props)) }
        }
        content.cobblemonWiki.explicitFakemon.forEach { yieldAll(it.displayName.values.values) }
    }.any(placeholderToken::containsMatchIn)

    fun forPlayer(content: HubContent, player: ServerPlayer): HubContent {
        if (!hasDynamicPlaceholders(content)) return content
        val context = PlaceholderContext.of(player)
        return content.copy(
            pages = content.pages.map { resolvePage(it, context) },
            cobblemonWiki = content.cobblemonWiki.copy(
                explicitFakemon = content.cobblemonWiki.explicitFakemon.map { resolveFakemon(it, context) }
            )
        )
    }

    private fun resolvePage(page: HubPage, context: PlaceholderContext): HubPage = page.copy(
        title = resolveLocalized(page.title, context),
        subtitle = resolveLocalized(page.subtitle, context),
        components = page.components.map { resolveComponent(it, context) }
    )

    private fun resolveComponent(component: HubComponent, context: PlaceholderContext): HubComponent = component.copy(
        props = resolveJson(component.props, context).asJsonObject
        // Deliberately do not resolve action.value. Client packets only carry the
        // action ID and command/URL payloads remain canonical server-owned data.
    )

    private fun resolveFakemon(entry: FakemonEntry, context: PlaceholderContext): FakemonEntry =
        entry.copy(displayName = resolveLocalized(entry.displayName, context))

    private fun resolveLocalized(text: LocalizedText, context: PlaceholderContext): LocalizedText =
        text.copy(values = text.values.mapValues { (_, value) -> resolveMarkup(value, context) })

    private fun resolveJson(element: JsonElement, context: PlaceholderContext): JsonElement = when {
        element.isJsonObject -> JsonObject().also { copy ->
            element.asJsonObject.entrySet().forEach { (key, value) -> copy.add(key, resolveJson(value, context)) }
        }
        element.isJsonArray -> JsonArray().also { copy ->
            element.asJsonArray.forEach { copy.add(resolveJson(it, context)) }
        }
        element.isJsonPrimitive && element.asJsonPrimitive.isString -> JsonPrimitive(resolveMarkup(element.asString, context))
        else -> element.deepCopy()
    }

    private fun strings(element: JsonElement): Sequence<String> = sequence {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { yieldAll(strings(it.value)) }
            element.isJsonArray -> element.asJsonArray.forEach { yieldAll(strings(it)) }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> yield(element.asString)
        }
    }

    private fun resolveMarkup(raw: String, context: PlaceholderContext): String =
        transformMarkup(raw) { plain ->
            if (!placeholderToken.containsMatchIn(plain)) plain
            else runCatching { Placeholders.parseText(Component.literal(plain), context).string }.getOrDefault(plain)
        }

    /**
     * Applies a resolver only to literal text portions, keeping the safe
     * MiniMessage tags unchanged. Literal angle-bracket command syntax such as
     * `/tpa <name>` is escaped so it renders as text rather than becoming a tag.
     */
    internal fun transformMarkup(raw: String, resolvePlain: (String) -> String): String {
        if (raw.isEmpty()) return raw
        val out = StringBuilder(raw.length + 16)
        var plainStart = 0
        var cursor = 0

        while (cursor < raw.length) {
            if (raw[cursor] == '<' && (cursor == 0 || raw[cursor - 1] != '\\')) {
                val end = raw.indexOf('>', cursor + 1)
                if (end > cursor) {
                    val candidate = raw.substring(cursor + 1, end)
                    if (isAllowedMiniMessageTag(candidate)) {
                        appendResolvedLiteral(out, raw.substring(plainStart, cursor), resolvePlain)
                        out.append(raw, cursor, end + 1)
                        cursor = end + 1
                        plainStart = cursor
                        continue
                    }
                }
            }
            cursor++
        }
        appendResolvedLiteral(out, raw.substring(plainStart), resolvePlain)
        return out.toString()
    }

    private fun appendResolvedLiteral(out: StringBuilder, literal: String, resolvePlain: (String) -> String) {
        if (literal.isEmpty()) return
        out.append(escapeMiniMessageLiteral(resolvePlain(literal)))
    }

    internal fun escapeMiniMessageLiteral(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '<' -> append("\\<")
                else -> append(char)
            }
        }
    }

    internal fun isAllowedMiniMessageTag(rawTag: String): Boolean {
        val tag = rawTag.trim().removePrefix("/").removePrefix("!")
        if (tag.isEmpty()) return false
        if (tag.startsWith("#") && tag.drop(1).matches(Regex("[0-9a-fA-F]{3,8}"))) return true
        val name = tag.substringBefore(':').lowercase()
        return name in SAFE_TAG_NAMES
    }

    private val SAFE_TAG_NAMES = setOf(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "grey",
        "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
        "color", "colour", "bold", "b", "italic", "i", "em", "underlined", "underline", "u",
        "strikethrough", "st", "obfuscated", "obf", "gradient", "rainbow", "reset", "newline", "br"
    )
}
