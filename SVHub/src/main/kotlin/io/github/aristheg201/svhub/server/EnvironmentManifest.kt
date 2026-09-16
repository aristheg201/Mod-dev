package io.github.aristheg201.svhub.server

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.api.SVHubApi
import net.fabricmc.loader.api.FabricLoader

data class ModInfo(val id: String, val name: String, val version: String)

data class EnvironmentManifest(
    val hubVersion: String,
    val mods: List<ModInfo>,
    val capabilities: Set<String>
) {
    fun toJson(): String = JsonObject().apply {
        addProperty("hubVersion", hubVersion)
        add("mods", JsonArray().also { arr -> mods.forEach { mod ->
            arr.add(JsonObject().apply {
                addProperty("id", mod.id)
                addProperty("name", mod.name)
                addProperty("version", mod.version)
            })
        } })
        add("capabilities", JsonArray().also { arr -> capabilities.sorted().forEach(arr::add) })
    }.toString()

    companion object {
        fun local(extraCapabilities: Set<String> = emptySet()): EnvironmentManifest {
            val loader = FabricLoader.getInstance()
            val version = loader.getModContainer("svhub").map { it.metadata.version.friendlyString }.orElse("unknown")
            val mods = loader.allMods.map { ModInfo(it.metadata.id, it.metadata.name, it.metadata.version.friendlyString) }.sortedBy { it.id }
            return EnvironmentManifest(version, mods, extraCapabilities + SVHubApi.advertisedCapabilities())
        }

        fun modIds(json: String): Set<String> = runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonArray("mods")?.mapNotNull { it.asJsonObject.get("id")?.asString }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
    }
}
