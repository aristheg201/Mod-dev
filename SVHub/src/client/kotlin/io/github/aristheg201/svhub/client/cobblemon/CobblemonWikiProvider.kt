package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import io.github.aristheg201.svhub.content.FakemonEntry
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.search.SearchIndex
import net.minecraft.resources.ResourceLocation
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class PokemonView(
    val key: String,
    val route: String,
    val speciesId: String,
    val aspects: Set<String>,
    val displayName: String,
    val dexNumber: Int,
    val fakemon: Boolean,
    val wikiPage: String? = null,
    val score: Int = 0
)

object CobblemonWikiProvider {
    private val cache = ConcurrentHashMap<String, List<PokemonView>>()

    private val customLabels = setOf(
        "fakemon", "myths", "eldorian", "bloodmoon", "custom", "server"
    )
    private val customAspects = setOf(
        "eldorian", "bloodmoon", "blossom", "draconic", "zodiac", "libra"
    )

    fun pokemon(content: HubContent): List<PokemonView> = build(content).filterNot { it.fakemon }
    fun fakemon(content: HubContent): List<PokemonView> = build(content).filter { it.fakemon }

    fun resolveRoute(route: String, content: HubContent): PokemonView? {
        val normalized = route.removePrefix("pokemon/").removePrefix("fakemon/")
        val key = URLDecoder.decode(normalized, StandardCharsets.UTF_8)
        return build(content).firstOrNull { it.key == key || (it.speciesId == key && it.aspects.isEmpty()) }
    }

    fun search(query: String, content: HubContent, limit: Int): List<PokemonView> {
        val q = SearchIndex.normalize(query)
        if (q.isBlank()) return emptyList()
        return build(content).mapNotNull { view ->
            val name = SearchIndex.normalize(view.displayName)
            val id = SearchIndex.normalize(view.speciesId)
            val aspects = SearchIndex.normalize(view.aspects.joinToString(" "))
            var score = 0
            if (name == q || id == q) score += 240
            if (name.startsWith(q)) score += 150
            if (name.contains(q)) score += 90
            if (id.contains(q)) score += 65
            if (aspects.contains(q)) score += 60
            if (score == 0) null else view.copy(score = score)
        }.sortedByDescending { it.score }.take(limit)
    }

    fun clearCaches() = cache.clear()

    private fun build(content: HubContent): List<PokemonView> {
        val signature = "${content.revision}:${PokemonSpecies.species.size}:${content.cobblemonWiki.hashCode()}"
        return cache.computeIfAbsent(signature) {
            val cfg = content.cobblemonWiki
            val explicitBySpecies = cfg.explicitFakemon.groupBy { it.species }
            val result = mutableListOf<PokemonView>()

            PokemonSpecies.implemented
                .asSequence()
                .filterNot { it.resourceIdentifier.toString() in cfg.hiddenSpecies }
                .sortedWith(compareBy({ it.nationalPokedexNumber }, { it.resourceIdentifier.toString() }))
                .forEach { species ->
                    val id = species.resourceIdentifier.toString()
                    val explicit = explicitBySpecies[id].orEmpty()
                    val speciesIsCustom = isCustomSpecies(species, cfg.fakemonNamespaces, cfg.autoFakemonNamespaces)

                    if (speciesIsCustom) {
                        result += baseView(species, fakemon = true)
                    } else if (cfg.autoPokemon) {
                        result += baseView(species, fakemon = false)
                    }

                    // Official species can still have server/addon-specific forms (for example
                    // Eldorian Dragonite). Keep the official base in Pokédex and add only the
                    // custom form to Fakédex.
                    species.forms
                        .asSequence()
                        .filter { it !== species.standardForm }
                        .filter(::isCustomForm)
                        .forEach { form -> result += formView(species, form) }

                    explicit.forEach { entry ->
                        result += explicitView(entry, species.nationalPokedexNumber, species.translatedName.string)
                    }
                }

            result.distinctBy { it.key }
        }
    }

    private fun baseView(species: Species, fakemon: Boolean): PokemonView {
        val id = species.resourceIdentifier.toString()
        return PokemonView(
            key = id,
            route = "${if (fakemon) "fakemon" else "pokemon"}/${enc(id)}",
            speciesId = id,
            aspects = emptySet(),
            displayName = species.translatedName.string,
            dexNumber = species.nationalPokedexNumber,
            fakemon = fakemon
        )
    }

    private fun formView(species: Species, form: FormData): PokemonView {
        val id = species.resourceIdentifier.toString()
        val aspects = form.aspects.toSet()
        val key = "$id|${aspects.sorted().joinToString(",")}" 
        val suffix = when {
            form.name.isNotBlank() && !form.name.equals("normal", true) -> form.name
            aspects.isNotEmpty() -> aspects.sorted().joinToString(" + ") { pretty(it) }
            else -> "Custom Form"
        }
        return PokemonView(
            key = key,
            route = "fakemon/${enc(key)}",
            speciesId = id,
            aspects = aspects,
            displayName = "${species.translatedName.string} — $suffix",
            dexNumber = species.nationalPokedexNumber,
            fakemon = true
        )
    }

    private fun explicitView(entry: FakemonEntry, dex: Int, fallbackName: String): PokemonView {
        val key = if (entry.aspects.isEmpty()) entry.id else "${entry.species}|${entry.aspects.sorted().joinToString(",")}" 
        return PokemonView(
            key = key,
            route = "fakemon/${enc(key)}",
            speciesId = entry.species,
            aspects = entry.aspects.toSet(),
            displayName = entry.displayName.resolve("vi_vn").ifBlank { fallbackName },
            dexNumber = dex,
            fakemon = true,
            wikiPage = entry.wikiPage
        )
    }

    private fun isCustomSpecies(species: Species, configuredNamespaces: Set<String>, autoNamespaces: Boolean): Boolean {
        val id = species.resourceIdentifier
        if (id.namespace in configuredNamespaces) return true
        if (autoNamespaces && id.namespace != "cobblemon") return true
        if (species.labels.any { it.lowercase() in customLabels }) return true
        // Official National Dex values are far below this range; both supplied Fakemon
        // packs intentionally use high custom dex ids (for example 7777/9020).
        return species.nationalPokedexNumber >= CUSTOM_DEX_FLOOR
    }

    private fun isCustomForm(form: FormData): Boolean =
        form.labels.any { it.lowercase() in customLabels } ||
            form.aspects.any { it.lowercase() in customAspects }

    private fun pretty(value: String): String = value
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private const val CUSTOM_DEX_FLOOR = 2000
}
