package io.github.aristheg201.svhub.native.game.tft

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.aristheg201.svhub.SVHub
import java.nio.file.Files
import java.nio.file.Path

object TftSetRegistry {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    @Volatile private var current: TftSetDefinition? = null

    fun start(configRoot: Path) {
        Files.createDirectories(configRoot)
        val override = configRoot.resolve("active-set.json")
        val loaded = runCatching {
            if (Files.isRegularFile(override)) Files.newBufferedReader(override).use { gson.fromJson(it, TftSetDefinition::class.java) }
            else bundled("kanto_rising")
        }.recoverCatching { error ->
            SVHub.LOGGER.error("Unable to load TFT config {}, falling back to bundled set", override, error)
            bundled("kanto_rising")
        }.getOrThrow()
        current = TftDefinitionValidator.validate(loaded)
        SVHub.LOGGER.info("Loaded TFT set {} with {} units and {} traits", loaded.id, loaded.units.size, loaded.traits.size)
    }

    fun active(): TftSetDefinition = current ?: TftDefinitionValidator.validate(bundled("kanto_rising")).also { current = it }

    private fun bundled(id: String): TftSetDefinition {
        val root = "/data/svhub/tft/sets/$id"
        val base = resource("$root/set.json", TftSetDefinition::class.java)
        return base.copy(
            units = resourceList("$root/units.json", object : TypeToken<List<TftUnitDefinition>>() {}),
            traits = resourceList("$root/traits.json", object : TypeToken<List<TftTraitDefinition>>() {}),
            components = resourceList("$root/components.json", object : TypeToken<List<TftItemComponentDefinition>>() {}),
            fullItems = resourceList("$root/full_items.json", object : TypeToken<List<TftFullItemDefinition>>() {}),
            augments = resourceList("$root/augments.json", object : TypeToken<List<TftAugmentDefinition>>() {}),
            pveRounds = resourceList("$root/pve.json", object : TypeToken<List<TftPveRoundDefinition>>() {})
        )
    }

    private fun <T> resource(path: String, type: Class<T>): T {
        val stream = TftSetRegistry::class.java.getResourceAsStream(path) ?: error("Missing bundled TFT resource $path")
        return stream.reader(Charsets.UTF_8).use { gson.fromJson(it, type) }
    }

    private fun <T> resourceList(path: String, token: TypeToken<List<T>>): List<T> {
        val stream = TftSetRegistry::class.java.getResourceAsStream(path) ?: error("Missing bundled TFT resource $path")
        return stream.reader(Charsets.UTF_8).use { gson.fromJson(it, token.type) }
    }
}
