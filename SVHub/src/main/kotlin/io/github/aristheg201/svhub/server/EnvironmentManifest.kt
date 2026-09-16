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
        add("mods", JsonArray().also { array ->
            mods.forEach { mod ->
                array.add(JsonObject().apply {
                    addProperty("id", mod.id)
                    addProperty("name", mod.name)
                    addProperty("version", mod.version)
                })
            }
        })
        add("capabilities", JsonArray().also { array -> capabilities.sorted().forEach(array::add) })
    }.toString()

    companion object {
        /** Full local environment. Never send this manifest to ordinary players. */
        fun local(extraCapabilities: Set<String> = emptySet()): EnvironmentManifest = build(null, extraCapabilities)

        /**
         * Public server advertisement: SVHub version/capabilities only. The server's
         * installed mod inventory is deliberately omitted from player-facing packets.
         */
        fun publicAdvertisement(extraCapabilities: Set<String> = emptySet()): EnvironmentManifest =
            EnvironmentManifest(hubVersion(), emptyList(), extraCapabilities + SVHubApi.advertisedCapabilities())

        /**
         * Privacy-preserving client advertisement. Only mods explicitly required by
         * registered SVHub integrations are disclosed; unrelated client mods never
         * leave the client.
         */
        fun clientAdvertisement(extraCapabilities: Set<String> = emptySet()): EnvironmentManifest {
            val allowed = buildSet {
                add("svhub")
                SVHubApi.integrations().flatMapTo(this) { it.requiredMods }
            }
            return build(allowed, extraCapabilities)
        }

        private fun hubVersion(): String = FabricLoader.getInstance()
            .getModContainer("svhub")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        private fun build(modFilter: Set<String>?, extraCapabilities: Set<String>): EnvironmentManifest {
            val loader = FabricLoader.getInstance()
            val mods = loader.allMods.asSequence()
                .filter { modFilter == null || it.metadata.id in modFilter }
                .map { ModInfo(it.metadata.id, it.metadata.name, it.metadata.version.friendlyString) }
                .sortedBy { it.id }
                .toList()
            return EnvironmentManifest(hubVersion(), mods, extraCapabilities + SVHubApi.advertisedCapabilities())
        }

        fun modIds(json: String): Set<String> = runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonArray("mods")?.mapNotNull { it.asJsonObject.get("id")?.asString }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
    }
}
