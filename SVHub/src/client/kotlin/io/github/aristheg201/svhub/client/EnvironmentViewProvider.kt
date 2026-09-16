package io.github.aristheg201.svhub.client

import com.google.gson.JsonParser
import io.github.aristheg201.svhub.search.SearchHit
import io.github.aristheg201.svhub.search.SearchIndex
import net.fabricmc.loader.api.FabricLoader

data class ModView(
    val id: String,
    val name: String,
    val server: Boolean,
    val client: Boolean,
    val serverVersion: String?,
    val clientVersion: String?
) {
    val route: String get() = "mod/$id"
    val sideLabel: String get() = when {
        server && client -> "Server + Client"
        server -> "Server"
        else -> "Client"
    }
}

object EnvironmentViewProvider {
    fun all(): List<ModView> {
        val server = parseServer()
        val client = FabricLoader.getInstance().allMods.associate { mod ->
            mod.metadata.id to Pair(mod.metadata.name, mod.metadata.version.friendlyString)
        }
        return (server.keys + client.keys).sorted().map { id ->
            val s = server[id]
            val c = client[id]
            ModView(id, s?.first ?: c?.first ?: id, s != null, c != null, s?.second, c?.second)
        }
    }

    fun byId(id: String): ModView? = all().firstOrNull { it.id == id }

    fun search(query: String, limit: Int = 20): List<SearchHit> {
        val q = SearchIndex.normalize(query)
        if (q.isBlank()) return emptyList()
        return all().mapNotNull { mod ->
            val id = SearchIndex.normalize(mod.id)
            val name = SearchIndex.normalize(mod.name)
            var score = 0
            if (id == q || name == q) score += 180
            if (id.startsWith(q) || name.startsWith(q)) score += 100
            if (id.contains(q) || name.contains(q)) score += 55
            if (score == 0) null else SearchHit("mod:${mod.id}", mod.route, mod.name, "${mod.id} • ${mod.sideLabel}", "mod", score, "environment")
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun parseServer(): Map<String, Pair<String, String>> = runCatching {
        val root = JsonParser.parseString(ClientHubState.serverManifest).asJsonObject
        root.getAsJsonArray("mods")?.associate { element ->
            val obj = element.asJsonObject
            val id = obj.get("id")?.asString ?: "unknown"
            id to Pair(obj.get("name")?.asString ?: id, obj.get("version")?.asString ?: "unknown")
        } ?: emptyMap()
    }.getOrDefault(emptyMap())
}
