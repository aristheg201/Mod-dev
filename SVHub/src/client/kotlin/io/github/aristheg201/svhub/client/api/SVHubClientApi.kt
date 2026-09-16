package io.github.aristheg201.svhub.client.api

import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.search.SearchHit
import java.util.concurrent.ConcurrentHashMap

data class DynamicHubCard(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val route: String
)

fun interface DynamicGridProvider {
    fun entries(content: HubContent): List<DynamicHubCard>
}

fun interface DynamicSearchProvider {
    fun search(query: String, content: HubContent, limit: Int): List<SearchHit>
}

/** Client-only extension surface: no renderer/server classes leak across sides. */
object SVHubClientApi {
    private val idPattern = Regex("^[a-z0-9_.:-]{1,128}$")
    private val grids = ConcurrentHashMap<String, DynamicGridProvider>()
    private val searches = ConcurrentHashMap<String, DynamicSearchProvider>()

    @JvmStatic
    fun registerGridProvider(id: String, provider: DynamicGridProvider) {
        require(idPattern.matches(id)) { "Invalid SVHub grid provider '$id'" }
        require(grids.putIfAbsent(id, provider) == null) { "Grid provider '$id' already registered" }
    }

    @JvmStatic
    fun registerSearchProvider(id: String, provider: DynamicSearchProvider) {
        require(idPattern.matches(id)) { "Invalid SVHub search provider '$id'" }
        require(searches.putIfAbsent(id, provider) == null) { "Search provider '$id' already registered" }
    }

    fun grid(id: String, content: HubContent): List<DynamicHubCard>? = grids[id]?.entries(content)

    fun search(query: String, content: HubContent, limit: Int): List<SearchHit> =
        searches.values.flatMap { runCatching { it.search(query, content, limit) }.getOrDefault(emptyList()) }.take(limit)
}
