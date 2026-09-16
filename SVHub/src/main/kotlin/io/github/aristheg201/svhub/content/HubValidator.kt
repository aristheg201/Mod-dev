package io.github.aristheg201.svhub.content

import io.github.aristheg201.svhub.api.SVHubApi

private val ID_PATTERN = Regex("^[a-z0-9_.:/-]{1,128}$")
private val COMPONENT_TYPES = setOf(
    "heading", "text", "markdown", "animated_text", "image", "pixel_image", "animated_image",
    "button", "separator", "notice", "grid", "search_box", "pokemon_model",
    "command_card", "link_card", "list", "collapse", "badge", "tooltip", "table", "widget", "spacer"
)
private val ACTION_TYPES get() = SVHubApi.CORE_ACTION_TYPES + SVHubApi.actionTypes()

data class ValidationIssue(val path: String, val message: String, val fatal: Boolean = true)

data class ValidationResult(val issues: List<ValidationIssue>) {
    val ok: Boolean get() = issues.none { it.fatal }
    /** Compatibility view for editor/status surfaces that only need fatal messages. */
    val errors: List<String> get() = issues.filter { it.fatal }.map { "${it.path}: ${it.message}" }
    fun requireValid() {
        if (!ok) throw IllegalArgumentException(errors.joinToString("; "))
    }
}

object HubValidator {
    private const val MAX_PAGES = 5000
    private const val MAX_COMPONENTS_PER_PAGE = 1000
    private const val MAX_THEMES = 256
    private const val MAX_ASSETS = 4096
    private const val MAX_ASSET_DIMENSION = 16384
    private const val MAX_EXPLICIT_FAKEMON = 10_000

    fun validate(content: HubContent): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        if (content.schema != HUB_SCHEMA_VERSION) issues += ValidationIssue("schema", "Unsupported schema ${content.schema}; expected $HUB_SCHEMA_VERSION")
        if (content.revision < 0) issues += ValidationIssue("revision", "Revision cannot be negative")
        if (content.defaultLocale.length !in 2..32) issues += ValidationIssue("defaultLocale", "Locale id is invalid")
        if (content.pages.size > MAX_PAGES) issues += ValidationIssue("pages", "Too many pages (${content.pages.size}); hard cap is $MAX_PAGES")
        if (content.themes.size > MAX_THEMES) issues += ValidationIssue("themes", "Too many themes (${content.themes.size}); hard cap is $MAX_THEMES")
        if (content.assets.size > MAX_ASSETS) issues += ValidationIssue("assets", "Too many assets (${content.assets.size}); hard cap is $MAX_ASSETS")
        if (content.defaultTheme !in content.themes && content.themes.isNotEmpty()) issues += ValidationIssue("defaultTheme", "Theme '${content.defaultTheme}' does not exist")

        content.themes.forEach { (key, theme) ->
            val path = "themes.$key"
            if (!ID_PATTERN.matches(key)) issues += ValidationIssue(path, "Invalid theme key '$key'")
            if (theme.id != key) issues += ValidationIssue("$path.id", "Theme id '${theme.id}' must match map key '$key'")
            if (theme.backgroundAsset != null && theme.backgroundAsset !in content.assets) {
                issues += ValidationIssue("$path.backgroundAsset", "Unknown asset '${theme.backgroundAsset}'", fatal = false)
            }
            if (!theme.motionStrength.isFinite() || theme.motionStrength !in 0f..4f) {
                issues += ValidationIssue("$path.motionStrength", "Motion strength must be finite and between 0 and 4")
            }
        }

        content.assets.forEach { (key, asset) ->
            val path = "assets.$key"
            if (!ID_PATTERN.matches(key)) issues += ValidationIssue(path, "Invalid asset key '$key'")
            if (asset.id != key) issues += ValidationIssue("$path.id", "Asset id '${asset.id}' must match map key '$key'")
            if (asset.type.isBlank() || asset.type.length > 64) issues += ValidationIssue("$path.type", "Asset type is blank or too long")
            if (asset.source.isBlank() || asset.source.length > 64) issues += ValidationIssue("$path.source", "Asset source is blank or too long")
            if (asset.width < 0 || asset.height < 0 || asset.width > MAX_ASSET_DIMENSION || asset.height > MAX_ASSET_DIMENSION) {
                issues += ValidationIssue("$path.size", "Asset dimensions must be between 0 and $MAX_ASSET_DIMENSION")
            }
            if (asset.resource != null && asset.resource.length > 512) issues += ValidationIssue("$path.resource", "Resource id is too long")
        }

        val pageIds = hashSetOf<String>()
        val routes = hashSetOf<String>()
        val actionIds = hashSetOf<String>()
        content.pages.forEachIndexed { pageIndex, page ->
            val path = "pages[$pageIndex]"
            if (!ID_PATTERN.matches(page.id)) issues += ValidationIssue("$path.id", "Invalid id '${page.id}'")
            if (!pageIds.add(page.id)) issues += ValidationIssue("$path.id", "Duplicate page id '${page.id}'")
            if (page.route.isBlank() || page.route.length > 192) issues += ValidationIssue("$path.route", "Route is blank or too long")
            if (!routes.add(page.route)) issues += ValidationIssue("$path.route", "Duplicate route '${page.route}'")
            if (page.theme != null && page.theme !in content.themes) issues += ValidationIssue("$path.theme", "Unknown theme '${page.theme}'")
            if (page.tags.size > 128) issues += ValidationIssue("$path.tags", "Too many tags")
            if (page.components.size > MAX_COMPONENTS_PER_PAGE) issues += ValidationIssue("$path.components", "Too many components (${page.components.size}); hard cap is $MAX_COMPONENTS_PER_PAGE")

            val componentIds = hashSetOf<String>()
            page.components.forEachIndexed { componentIndex, component ->
                val cPath = "$path.components[$componentIndex]"
                if (!ID_PATTERN.matches(component.id)) issues += ValidationIssue("$cPath.id", "Invalid component id '${component.id}'")
                if (!componentIds.add(component.id)) issues += ValidationIssue("$cPath.id", "Duplicate component id '${component.id}' inside page '${page.id}'")
                if (component.type !in COMPONENT_TYPES) issues += ValidationIssue("$cPath.type", "Unknown component type '${component.type}'", fatal = false)
                component.action?.let { action ->
                    if (!ID_PATTERN.matches(action.id)) issues += ValidationIssue("$cPath.action.id", "Invalid action id '${action.id}'")
                    if (!actionIds.add(action.id)) issues += ValidationIssue("$cPath.action.id", "Duplicate action id '${action.id}'")
                    if (action.type !in ACTION_TYPES) issues += ValidationIssue("$cPath.action.type", "Unknown action type '${action.type}' (requires an integration)", fatal = false)
                    if (action.value.length > 2048) issues += ValidationIssue("$cPath.action.value", "Action value is too long")
                    if (action.permission != null && action.permission.length > 256) issues += ValidationIssue("$cPath.action.permission", "Permission node is too long")
                    if (action.cooldownMs < 0L || action.cooldownMs > 300_000L) issues += ValidationIssue("$cPath.action.cooldownMs", "Cooldown must be between 0 and 5 minutes")
                }
            }
        }

        if (content.cobblemonWiki.explicitFakemon.size > MAX_EXPLICIT_FAKEMON) {
            issues += ValidationIssue("cobblemonWiki.explicitFakemon", "Too many explicit Fakemon entries; hard cap is $MAX_EXPLICIT_FAKEMON")
        }
        val fakemonIds = hashSetOf<String>()
        content.cobblemonWiki.explicitFakemon.forEachIndexed { index, entry ->
            val path = "cobblemonWiki.explicitFakemon[$index]"
            if (!ID_PATTERN.matches(entry.id)) issues += ValidationIssue("$path.id", "Invalid id '${entry.id}'")
            if (!fakemonIds.add(entry.id)) issues += ValidationIssue("$path.id", "Duplicate Fakemon id '${entry.id}'")
            if (!entry.species.contains(':') || entry.species.length > 256) issues += ValidationIssue("$path.species", "Species must be a valid namespaced id")
            if (entry.aspects.size > 64) issues += ValidationIssue("$path.aspects", "Too many aspects")
            if (entry.wikiPage != null && content.page(entry.wikiPage) == null) issues += ValidationIssue("$path.wikiPage", "Unknown Wiki page '${entry.wikiPage}'", fatal = false)
        }
        return ValidationResult(issues)
    }
}
