package io.github.aristheg201.svarcade.content

/**
 * Migrates only the exact family of SVArcade's old bundled showcase seed.
 * Admin-authored content is never replaced based on a loose page-name match.
 */
object BundledContentMigration {
    private val legacyPageIds = setOf(
        "home",
        "how_to_play",
        "pokemon",
        "fakemon",
        "essential_commands",
        "commands",
        "updates",
        "mods"
    )

    fun migrate(content: HubContent): HubContent? {
        if (!isLegacyShowcaseSeed(content)) return null
        val fresh = DefaultContent.create()
        val oldWiki = content.cobblemonWiki
        val mergedWiki = fresh.cobblemonWiki.copy(
            autoPokemon = oldWiki.autoPokemon,
            autoFakemonNamespaces = oldWiki.autoFakemonNamespaces,
            fakemonNamespaces = fresh.cobblemonWiki.fakemonNamespaces + oldWiki.fakemonNamespaces,
            explicitFakemon = oldWiki.explicitFakemon,
            hiddenSpecies = oldWiki.hiddenSpecies
        )
        return fresh.copy(
            revision = content.revision + 1,
            cobblemonWiki = mergedWiki
        )
    }

    fun isLegacyShowcaseSeed(content: HubContent): Boolean {
        if (content.revision != 0L) return false
        if (content.pages.map { it.id }.toSet() != legacyPageIds) return false
        val home = content.page("home") ?: return false
        val mods = content.page("mods") ?: return false
        val hasOldHomeButton = home.components.any { it.id == "mods_btn" && it.action?.value == "mods" }
        val hasOldModsGrid = mods.components.any {
            it.id == "mods_grid" && runCatching { it.props.get("provider")?.asString }.getOrNull() == "environment:mods"
        }
        val oldThemes = setOf("pixel_classic", "pixel_wiki", "pixel_commands", "pixel_updates")
        return hasOldHomeButton && hasOldModsGrid && oldThemes.all { it in content.themes }
    }
}
