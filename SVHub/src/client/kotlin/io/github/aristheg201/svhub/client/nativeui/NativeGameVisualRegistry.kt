package io.github.aristheg201.svhub.client.nativeui

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

data class NativePieceVisual(
    val species: String,
    val aspects: Set<String> = emptySet(),
    val scale: Float = 1f,
    val yaw: Float = 0f
) {
    fun pokemon(label: String): PokemonView = PokemonView(
        key = "svhub-scene:$species:${aspects.sorted().joinToString(",")}",
        route = "",
        speciesId = species,
        aspects = aspects,
        displayName = label,
        dexNumber = 0,
        fakemon = false
    )
}

data class NativeGameVisualDefinition(
    val pieces: Map<String, NativePieceVisual>,
    val teams: Map<Int, NativePieceVisual>
)

object NativeGameVisualRegistry {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, NativeGameVisualDefinition>()

    fun piece(gameId: String, token: String): NativePieceVisual? {
        val def = definition(gameId)
        return def.pieces[token]
            ?: def.pieces[token.lowercase()]
            ?: def.pieces[token.uppercase()]
    }

    fun team(gameId: String, team: Int): NativePieceVisual? = definition(gameId).teams[team]

    fun clear() = cache.clear()

    private fun definition(gameId: String): NativeGameVisualDefinition =
        cache.computeIfAbsent(gameId, ::load)

    private fun load(gameId: String): NativeGameVisualDefinition {
        val id = ResourceLocation.fromNamespaceAndPath("svhub", "game_visuals/$gameId.json")
        val resource = Minecraft.getInstance().resourceManager.getResource(id).orElse(null)
            ?: return EMPTY
        return runCatching {
            resource.open().bufferedReader().use { reader ->
                parse(gson.fromJson(reader, JsonObject::class.java) ?: JsonObject())
            }
        }.getOrDefault(EMPTY)
    }

    private fun parse(root: JsonObject): NativeGameVisualDefinition {
        val pieces = linkedMapOf<String, NativePieceVisual>()
        root.getAsJsonObject("pieces")?.entrySet()?.forEach { (token, value) ->
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: return@forEach
            visual(obj)?.let { pieces[token] = it }
        }

        val teams = linkedMapOf<Int, NativePieceVisual>()
        root.getAsJsonObject("teams")?.entrySet()?.forEach { (team, value) ->
            val index = team.toIntOrNull() ?: return@forEach
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: return@forEach
            visual(obj)?.let { teams[index] = it }
        }

        return NativeGameVisualDefinition(pieces, teams)
    }

    private fun visual(obj: JsonObject): NativePieceVisual? {
        val species = runCatching { obj.get("species")?.asString.orEmpty() }.getOrDefault("")
        if (species.isBlank()) return null
        val aspects = linkedSetOf<String>()
        obj.getAsJsonArray("aspects")?.forEach { raw ->
            runCatching { raw.asString.trim() }.getOrNull()?.takeIf(String::isNotBlank)?.let(aspects::add)
        }
        val scale = runCatching { obj.get("scale")?.asFloat ?: 1f }.getOrDefault(1f).coerceIn(0.45f, 1.65f)
        val yaw = runCatching { obj.get("yaw")?.asFloat ?: 0f }.getOrDefault(0f)
        return NativePieceVisual(species, aspects, scale, yaw)
    }

    private val EMPTY = NativeGameVisualDefinition(emptyMap(), emptyMap())
}
