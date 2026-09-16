package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
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

    fun pokemon(content: HubContent): List<PokemonView> = build(content).filterNot { it.fakemon }
    fun fakemon(content: HubContent): List<PokemonView> = build(content).filter { it.fakemon }

    fun resolveRoute(route: String, content: HubContent): PokemonView? {
        val normalized = route.removePrefix("pokemon/").removePrefix("fakemon/")
        val key = URLDecoder.decode(normalized, StandardCharsets.UTF_8)
        return build(content).firstOrNull { it.key == key || it.speciesId == key }
    }

    fun search(query: String, content: HubContent, limit: Int): List<PokemonView> {
        val q = SearchIndex.normalize(query)
        if (q.isBlank()) return emptyList()
        return build(content).mapNotNull { view ->
            val name = SearchIndex.normalize(view.displayName)
            val id = SearchIndex.normalize(view.speciesId)
            var score = 0
            if (name == q || id == q) score += 240
            if (name.startsWith(q)) score += 150
            if (name.contains(q)) score += 90
            if (id.contains(q)) score += 65
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
                    val namespaceFakemon = id.substringBefore(':') in cfg.fakemonNamespaces ||
                        (cfg.autoFakemonNamespaces && species.resourceIdentifier.namespace != "cobblemon")

                    if (!namespaceFakemon && cfg.autoPokemon) {
                        result += PokemonView(
                            key = id,
                            route = "pokemon/${enc(id)}",
                            speciesId = id,
                            aspects = emptySet(),
                            displayName = species.translatedName.string,
                            dexNumber = species.nationalPokedexNumber,
                            fakemon = false
                        )
                    } else if (namespaceFakemon) {
                        result += PokemonView(
                            key = id,
                            route = "fakemon/${enc(id)}",
                            speciesId = id,
                            aspects = emptySet(),
                            displayName = species.translatedName.string,
                            dexNumber = species.nationalPokedexNumber,
                            fakemon = true
                        )
                    }

                    explicit.forEach { entry -> result += explicitView(entry, species.nationalPokedexNumber, species.translatedName.string) }
                }
            result.distinctBy { it.key }
        }
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

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
