package io.github.aristheg201.svarcade.client.cobblemon

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft

object BundledFakemonCatalog {
    data class FormKey(val species: String, val aspects: Set<String>)
    data class Snapshot(
        val speciesSources: Map<String, String>,
        val formSources: Map<FormKey, String>
    ) {
        fun sourceForSpecies(species: String): String? = speciesSources[species]
        fun sourceForForm(species: String, aspects: Set<String>): String? {
            if (aspects.isEmpty()) return null
            return formSources.entries.firstOrNull { (key, _) ->
                key.species == species && (key.aspects == aspects || aspects.containsAll(key.aspects))
            }?.value
        }
    }

    private val gson = Gson()
    @Volatile private var cached: Snapshot? = null

    fun snapshot(): Snapshot = cached ?: synchronized(this) {
        cached ?: load().also { cached = it }
    }

    fun clear() { cached = null }

    private fun load(): Snapshot {
        val resources = Minecraft.getInstance().resourceManager
            .listResources("fakemon_catalog") { it.path.endsWith(".json") }
        val species = linkedMapOf<String, String>()
        val forms = linkedMapOf<FormKey, String>()
        resources.entries.sortedBy { it.key.toString() }.forEach resourceLoop@ { (_, resource) ->
            runCatching {
                resource.open().bufferedReader().use { reader ->
                    val root = gson.fromJson(reader, JsonObject::class.java) ?: return@use
                    val source = root.get("source")?.asString?.takeIf(String::isNotBlank) ?: "Bundled Fakemon"
                    root.getAsJsonArray("species")?.forEach { raw ->
                        runCatching { raw.asString.lowercase() }.getOrNull()?.takeIf(String::isNotBlank)?.let { species[it] = source }
                    }
                    root.getAsJsonArray("forms")?.forEach formLoop@ { raw ->
                        val obj = runCatching { raw.asJsonObject }.getOrNull() ?: return@formLoop
                        val id = runCatching { obj.get("species")?.asString?.lowercase().orEmpty() }.getOrDefault("")
                        val aspects = obj.getAsJsonArray("aspects")?.mapNotNull {
                            runCatching { it.asString.lowercase() }.getOrNull()?.takeIf(String::isNotBlank)
                        }?.toSet().orEmpty()
                        if (id.isNotBlank() && aspects.isNotEmpty()) forms[FormKey(id, aspects)] = source
                    }
                }
            }
        }
        return Snapshot(species, forms)
    }
}
