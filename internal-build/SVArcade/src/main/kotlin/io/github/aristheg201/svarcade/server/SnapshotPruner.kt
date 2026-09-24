package io.github.aristheg201.svarcade.server

import com.google.gson.JsonElement
import io.github.aristheg201.svarcade.content.HubContent
import io.github.aristheg201.svarcade.content.HubPage

/** Removes assets/themes that are unreachable from the already-authorized page set. */
object SnapshotPruner {
    fun prune(content: HubContent, visiblePages: List<HubPage>): HubContent {
        val knownAssets = content.assets.keys
        val referenced = linkedSetOf<String>()

        fun collect(element: JsonElement?) {
            if (element == null || element.isJsonNull) return
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val value = element.asString
                    if (value in knownAssets) referenced += value
                }
                element.isJsonArray -> element.asJsonArray.forEach(::collect)
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { collect(it.value) }
            }
        }

        visiblePages.forEach { page ->
            page.icon?.takeIf { it in knownAssets }?.let(referenced::add)
            page.components.forEach { collect(it.props) }
        }

        val themeIds = buildSet {
            add(content.defaultTheme)
            visiblePages.forEach { add(it.theme ?: content.defaultTheme) }
        }
        val themes = content.themes.filterKeys { it in themeIds }
        themes.values.forEach { theme -> theme.backgroundAsset?.takeIf { it in knownAssets }?.let(referenced::add) }

        // Generated assets may depend on other assets. Resolve that graph to a fixed point.
        val pending = ArrayDeque(referenced)
        val scanned = hashSetOf<String>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!scanned.add(id)) continue
            val before = referenced.size
            content.assets[id]?.let { collect(it.generator) }
            if (referenced.size != before) {
                referenced.filterNot { it in scanned || it in pending }.forEach(pending::addLast)
            }
        }

        return content.copy(
            pages = visiblePages,
            themes = themes,
            assets = content.assets.filterKeys { it in referenced }
        )
    }
}
