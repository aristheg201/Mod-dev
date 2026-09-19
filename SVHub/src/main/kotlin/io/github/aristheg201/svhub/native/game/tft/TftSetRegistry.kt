package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

/** Loads and validates complete sets before publishing them to new sessions. */
object TftSetRegistry {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    // Do not initialize the Minecraft/Fabric entrypoint just to load pure game data.
    private val logger = LoggerFactory.getLogger("SVHub/TFT")
    @Volatile private var current: TftSetDefinition? = null
    private const val MAX_JSON_CHARS = 4 * 1024 * 1024
    private val manifestFields = setOf("schema", "id", "name", "poolSizeByCost", "xpToNextByLevel", "shopOdds")

    @Synchronized
    fun start(configRoot: Path) {
        Files.createDirectories(configRoot)
        val override = configRoot.resolve("active-set.json")
        val loaded = if (Files.isRegularFile(override)) {
            try {
                Files.newBufferedReader(override, Charsets.UTF_8).use { reader ->
                    TftDefinitionValidator.validate(decodeSet(reader, override.toString()))
                }
            } catch (error: Exception) {
                logger.error("Invalid TFT override {}; keeping the file unchanged and loading the bundled set", override, error)
                bundled("kanto_rising")
            }
        } else bundled("kanto_rising")
        current = loaded
        logger.info("Loaded TFT set {}: {} units, {} traits, {} components, {} recipes", loaded.id, loaded.units.size, loaded.traits.size, loaded.components.size, loaded.fullItems.size)
    }

    @Synchronized
    fun active(): TftSetDefinition = current ?: bundled("kanto_rising").also { current = it }

    /** Version-one manifests and embedded recovery definitions keep their XP
     * curve. Newly introduced grants come from shipped content, never Java IDs. */
    internal fun migrateDefinition(set: TftSetDefinition): TftSetDefinition {
        if (set.progression != null && set.roundSchedule.isNotEmpty()) return set
        val manifest = open("/data/svhub/tft/sets/kanto_rising/set.json").use { reader ->
            readJson(reader, "bundled migration defaults").asJsonObject
        }
        val progression = set.progression ?: gson.fromJson(manifest.get("progression"), TftProgressionDefinition::class.java)
            .copy(maxLevel = set.maxLevel, xpToNextByLevel = set.xpToNextByLevel.toMap())
        val schedule = if (set.roundSchedule.isNotEmpty()) set.roundSchedule else
            gson.fromJson<List<TftRoundDefinition>>(manifest.get("roundSchedule"), object : TypeToken<List<TftRoundDefinition>>() {}.type)
        return set.copy(progression = progression, roundSchedule = schedule)
    }

    internal fun bundled(id: String): TftSetDefinition {
        require(id.matches(Regex("^[a-z0-9_.-]{1,64}$"))) { "Invalid bundled TFT set id: $id" }
        val root = "/data/svhub/tft/sets/$id"
        val base = open("$root/set.json").use { decodeSet(it, "$root/set.json") }
        require(base.id == id) { "$root/set.json: expected set id $id, got ${base.id}" }
        return TftDefinitionValidator.validate(base.copy(
            units = resourceList("$root/units.json", object : TypeToken<List<TftUnitDefinition>>() {}),
            teams = optionalResourceList("$root/teams.json", object : TypeToken<List<TftTeamDefinition>>() {}),
            traits = resourceList("$root/traits.json", object : TypeToken<List<TftTraitDefinition>>() {}),
            components = resourceList("$root/components.json", object : TypeToken<List<TftItemComponentDefinition>>() {}),
            fullItems = resourceList("$root/full_items.json", object : TypeToken<List<TftFullItemDefinition>>() {}),
            augments = resourceList("$root/augments.json", object : TypeToken<List<TftAugmentDefinition>>() {}),
            pveRounds = resourceList("$root/pve.json", object : TypeToken<List<TftPveRoundDefinition>>() {})
        ))
    }

    internal fun decodeSet(reader: Reader, source: String): TftSetDefinition {
        val json = readJson(reader, source)
        require(json.isJsonObject) { "$source: expected a TFT set object, not an array or scalar" }
        val missing = manifestFields.filterNot(json.asJsonObject::has)
        require(missing.isEmpty()) { "$source: missing TFT manifest fields $missing" }
        return try {
            migrateDefinition(requireNotNull(gson.fromJson(json, TftSetDefinition::class.java)) { "$source: empty TFT set" })
        } catch (error: Exception) {
            throw IllegalArgumentException("$source: invalid TFT set field types", error)
        }
    }

    private fun <T> resourceList(path: String, token: TypeToken<List<T>>): List<T> = open(path).use { reader ->
        val json = readJson(reader, path)
        require(json.isJsonArray) { "$path: expected an array of definitions" }
        require(json.asJsonArray.all { it.isJsonObject }) { "$path: every definition must be a non-null object" }
        try {
            requireNotNull(gson.fromJson<List<T>>(json, token.type)) { "$path: empty definition list" }
        } catch (error: Exception) {
            throw IllegalArgumentException("$path: invalid TFT definition field types", error)
        }
    }

    private fun <T> optionalResourceList(path: String, token: TypeToken<List<T>>): List<T> {
        val stream = TftSetRegistry::class.java.getResourceAsStream(path) ?: return emptyList()
        return stream.reader(Charsets.UTF_8).use { reader ->
            val json = readJson(reader, path)
            require(json.isJsonArray) { "$path: expected an array of definitions" }
            require(json.asJsonArray.all { it.isJsonObject }) { "$path: every definition must be a non-null object" }
            try {
                requireNotNull(gson.fromJson<List<T>>(json, token.type)) { "$path: empty definition list" }
            } catch (error: Exception) {
                throw IllegalArgumentException("$path: invalid TFT definition field types", error)
            }
        }
    }

    private fun open(path: String): Reader =
        (TftSetRegistry::class.java.getResourceAsStream(path) ?: error("Missing bundled TFT resource $path"))
            .reader(Charsets.UTF_8)

    private fun readJson(reader: Reader, source: String): JsonElement {
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(text.length + count <= MAX_JSON_CHARS) { "$source: TFT JSON exceeds $MAX_JSON_CHARS characters" }
            text.append(buffer, 0, count)
        }
        val json = try { JsonParser.parseString(text.toString()) }
        catch (error: Exception) { throw IllegalArgumentException("$source: malformed TFT JSON", error) }
        rejectNulls(json, source, "$")
        return json
    }

    private fun rejectNulls(value: JsonElement, source: String, path: String) {
        require(!value.isJsonNull) { "$source: null is not allowed at $path" }
        when {
            value.isJsonArray -> value.asJsonArray.forEachIndexed { index, child -> rejectNulls(child, source, "$path[$index]") }
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) -> rejectNulls(child, source, "$path.$key") }
        }
    }
}
