package io.github.aristheg201.svhub.content

import com.google.gson.JsonObject

const val HUB_SCHEMA_VERSION = 1
const val HUB_PROTOCOL_VERSION = 1

data class LocalizedText(val values: Map<String, String>) {
    fun resolve(locale: String, fallbackLocale: String = "vi_vn"): String = values[locale] ?: values[fallbackLocale] ?: values["en_us"] ?: values.values.firstOrNull() ?: ""
    companion object { fun of(text: String, locale: String = "vi_vn") = LocalizedText(mapOf(locale to text)) }
}

data class VisibilitySpec(val permission: String? = null, val serverMod: String? = null, val clientMod: String? = null, val editorOnly: Boolean = false)
data class HubActionSpec(val id: String, val type: String, val value: String = "", val permission: String? = null, val cooldownMs: Long = 500L)
data class HubComponent(val id: String, val type: String, val props: JsonObject = JsonObject(), val visibility: VisibilitySpec = VisibilitySpec(), val action: HubActionSpec? = null)
data class HubPage(val id: String, val route: String, val category: String, val title: LocalizedText, val subtitle: LocalizedText = LocalizedText(emptyMap()), val tags: List<String> = emptyList(), val theme: String? = null, val icon: String? = null, val showInNavigation: Boolean = true, val visibility: VisibilitySpec = VisibilitySpec(), val components: List<HubComponent> = emptyList())

data class ThemePalette(val background: Int = 0xFF10131A.toInt(), val panel: Int = 0xE61B2230.toInt(), val panelAlt: Int = 0xE6263145.toInt(), val text: Int = 0xFFF4F6FB.toInt(), val mutedText: Int = 0xFFB6C1D4.toInt(), val accent: Int = 0xFFFFD45A.toInt(), val accent2: Int = 0xFF68D5FF.toInt(), val danger: Int = 0xFFFF6B6B.toInt())
data class HubTheme(val id: String, val backgroundAsset: String? = null, val backgroundPreset: String = "pixel_sky", val panelStyle: String = "pixel", val buttonStyle: String = "pixel", val titleAnimation: String = "pixel_pop", val palette: ThemePalette = ThemePalette(), val motionStrength: Float = 0.25f)
data class HubAsset(val id: String, val type: String, val source: String, val resource: String? = null, val width: Int = 0, val height: Int = 0, val generator: JsonObject = JsonObject(), val tags: List<String> = emptyList())
data class FakemonEntry(val id: String, val species: String, val aspects: List<String> = emptyList(), val displayName: LocalizedText = LocalizedText(emptyMap()), val tags: List<String> = emptyList(), val wikiPage: String? = null)
data class CobblemonWikiConfig(val autoPokemon: Boolean = true, val autoFakemonNamespaces: Boolean = true, val fakemonNamespaces: Set<String> = emptySet(), val explicitFakemon: List<FakemonEntry> = emptyList(), val hiddenSpecies: Set<String> = emptySet())
data class HubContent(val schema: Int = HUB_SCHEMA_VERSION, val revision: Long = 0L, val defaultLocale: String = "vi_vn", val defaultTheme: String = "pixel_classic", val themes: Map<String, HubTheme> = emptyMap(), val assets: Map<String, HubAsset> = emptyMap(), val pages: List<HubPage> = emptyList(), val cobblemonWiki: CobblemonWikiConfig = CobblemonWikiConfig()) {
    fun page(idOrRoute: String): HubPage? = pages.firstOrNull { it.id == idOrRoute || it.route == idOrRoute }
    fun themeFor(page: HubPage?): HubTheme? = themes[page?.theme ?: defaultTheme] ?: themes[defaultTheme]
}
data class HubSnapshot(val content: HubContent, val generatedAtEpochMs: Long = System.currentTimeMillis()) { val revision: Long get() = content.revision }
