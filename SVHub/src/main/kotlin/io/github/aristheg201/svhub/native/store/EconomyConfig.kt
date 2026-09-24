package io.github.aristheg201.svhub.native.store

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.util.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path

/** Currency identifiers and aliases are server data, never compiled assumptions. */
object EconomyConfig {
    private var config = JsonObject()
    fun start(path: Path) {
        if (!Files.exists(path)) AtomicFiles.writeUtf8(path, requireNotNull(javaClass.getResourceAsStream("/data/svhub/economy.json")).bufferedReader().use { it.readText() })
        config = JsonParser.parseString(Files.readString(path)).asJsonObject
    }
    fun defaultCurrency(kind: String): String = config.getAsJsonObject("defaults")?.let { defaults ->
        if (kind in setOf("ARENA", "TACTICIAN")) defaults.getAsJsonObject("store")?.get(kind)?.asString
        else defaults.get(kind)?.asString
    } ?: ""
    fun resolve(key: String, available: List<String>): String? {
        val definition = config.getAsJsonObject("currencies")?.getAsJsonObject(key)
        val id = definition?.get("currency")?.asString ?: key
        if (id.startsWith("@beconomy:")) return id.substringAfter(':').toIntOrNull()?.let(available::getOrNull)
        return id.takeIf { it.isNotBlank() }
    }
    fun wallet(available: List<String>): List<String> {
        val keys = config.getAsJsonArray("wallet")?.map { it.asString } ?: available
        return keys.flatMap { if (it == "@beconomy:*") available else listOfNotNull(resolve(it, available)) }.distinct()
    }
}
