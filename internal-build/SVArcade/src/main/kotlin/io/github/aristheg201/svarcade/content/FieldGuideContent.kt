package io.github.aristheg201.svarcade.content

import com.google.gson.JsonObject

/**
 * Current bundled player-facing content presentation.
 *
 * DefaultContent remains the legacy handbook seed so older on-disk revisions can
 * be fingerprinted exactly. Fresh installs and runtime fallbacks use this object.
 */
object FieldGuideContent {
    const val DEFAULT_THEME = "field_guide"
    const val WIKI_THEME = "field_guide_wiki"

    fun create(): HubContent {
        val legacy = DefaultContent.create()
        val completed = BundledHandbookPatch.apply(legacy)?.copy(revision = legacy.revision) ?: legacy
        return restyle(completed).copy(revision = 0L)
    }

    internal fun restyle(content: HubContent): HubContent {
        val themes = linkedMapOf(
            DEFAULT_THEME to HubTheme(
                id = DEFAULT_THEME,
                backgroundPreset = "field_guide",
                panelStyle = "field_guide",
                buttonStyle = "field_guide",
                titleAnimation = "none",
                palette = ThemePalette(
                    background = 0xFFF1E7D2.toInt(),
                    panel = 0xFFFBF7ED.toInt(),
                    panelAlt = 0xFFE7EFE8.toInt(),
                    text = 0xFF29312F.toInt(),
                    mutedText = 0xFF68736F.toInt(),
                    accent = 0xFF2E7168.toInt(),
                    accent2 = 0xFFC58A35.toInt(),
                    danger = 0xFFB84B4B.toInt()
                ),
                motionStrength = 0f
            ),
            WIKI_THEME to HubTheme(
                id = WIKI_THEME,
                backgroundPreset = "field_guide",
                panelStyle = "field_guide",
                buttonStyle = "field_guide",
                titleAnimation = "none",
                palette = ThemePalette(
                    background = 0xFFEDF2E9.toInt(),
                    panel = 0xFFFAFBF4.toInt(),
                    panelAlt = 0xFFE1ECE5.toInt(),
                    text = 0xFF26332F.toInt(),
                    mutedText = 0xFF64716C.toInt(),
                    accent = 0xFF2D6F63.toInt(),
                    accent2 = 0xFFB77A32.toInt(),
                    danger = 0xFFB84B4B.toInt()
                ),
                motionStrength = 0f
            )
        )

        val pages = content.pages.map { page ->
            val wiki = page.id == "pokemon" || page.id == "fakemon" || page.category == "wiki"
            page.copy(
                title = decorateTitle(page),
                theme = if (wiki) WIKI_THEME else DEFAULT_THEME,
                components = decorateComponents(page)
            )
        }

        return content.copy(
            defaultTheme = DEFAULT_THEME,
            themes = themes,
            assets = content.assets.filterValues { asset -> asset.source != "generated" || asset.type !in setOf("background", "overlay") },
            pages = pages
        )
    }

    private fun decorateTitle(page: HubPage): LocalizedText = LocalizedText(
        page.title.values.mapValues { (_, value) ->
            if (page.id == "home") {
                "<gradient:#2E7168:#C58A35><bold>$value</bold></gradient>"
            } else {
                "<bold><color:#2E7168>$value</color></bold>"
            }
        }
    )

    private fun decorateComponents(page: HubPage): List<HubComponent> {
        val base = page.components.map(::decorateComponent).toMutableList()
        if (page.id == "home" && base.none { it.id == "home_status" }) {
            val insertAfter = base.indexOfFirst { it.id == "home_intro" }.let { if (it < 0) 0 else it + 1 }
            base.add(
                insertAfter,
                HubComponent(
                    id = "home_status",
                    type = "notice",
                    props = JsonObject().apply {
                        addProperty(
                            "text",
                            "<color:#68736F>Online</color> <bold><color:#C58A35>%server:online%/%server:max_players%</color></bold>" +
                                "  <color:#68736F>· Giờ thế giới</color> <color:#2E7168>%world:time%</color>" +
                                "  <color:#68736F>· Ping</color> <color:#2E7168>%player:ping%</color><color:#68736F> ms</color>"
                        )
                    }
                )
            )
        }
        return base
    }

    private fun decorateComponent(component: HubComponent): HubComponent {
        val props = component.props.deepCopy()
        when (component.id) {
            "home_start" -> props.addProperty(
                "text",
                "<bold><color:#2E7168>Chào %player:name_visual%</color></bold>"
            )
            "home_intro" -> props.addProperty(
                "text",
                "<color:#29312F>Đây là cẩm nang nhanh của server.</color> " +
                    "<color:#68736F>Mới chơi thì mở</color> <bold><color:#2E7168>Hướng dẫn chơi</color></bold><color:#68736F>; " +
                    "cần thao tác nhanh thì mở</color> <bold><color:#C58A35>Lệnh người chơi</color></bold><color:#68736F>.</color>"
            )
            else -> if (component.type == "heading") {
                val text = runCatching { props.get("text")?.asString }.getOrNull()
                if (!text.isNullOrBlank() && !text.contains("<color:", ignoreCase = true)) {
                    props.addProperty("text", "<bold><color:#2E7168>$text</color></bold>")
                }
            }
        }
        return component.copy(props = props)
    }
}

/**
 * Strict one-time visual refresh for SVArcade-owned clean-dark handbook revisions.
 * Matching compares the whole bundled document body (except revision/wiki data),
 * so administrator-authored content is not replaced by a loose page-name check.
 */
object BundledVisualRefreshPatch {
    fun apply(content: HubContent): HubContent? {
        if (content.defaultTheme == FieldGuideContent.DEFAULT_THEME) return null
        val legacy = DefaultContent.create()
        val completed = BundledHandbookPatch.apply(legacy) ?: legacy
        if (!sameBundledBody(content, legacy) && !sameBundledBody(content, completed)) return null

        val fresh = FieldGuideContent.create()
        val oldWiki = content.cobblemonWiki
        return fresh.copy(
            revision = content.revision + 1,
            cobblemonWiki = fresh.cobblemonWiki.copy(
                autoPokemon = oldWiki.autoPokemon,
                autoFakemonNamespaces = oldWiki.autoFakemonNamespaces,
                fakemonNamespaces = fresh.cobblemonWiki.fakemonNamespaces + oldWiki.fakemonNamespaces,
                explicitFakemon = oldWiki.explicitFakemon,
                hiddenSpecies = oldWiki.hiddenSpecies
            )
        )
    }

    private fun sameBundledBody(actual: HubContent, expected: HubContent): Boolean =
        actual.copy(revision = 0L, cobblemonWiki = expected.cobblemonWiki) ==
            expected.copy(revision = 0L)
}
