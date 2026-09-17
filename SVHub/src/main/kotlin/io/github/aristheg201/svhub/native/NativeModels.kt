package io.github.aristheg201.svhub.native

import com.google.gson.JsonObject

data class NativeSkin(
    val id: String,
    val name: String,
    val species: String,
    val aspect: String,
    val source: String,
    val currency: String,
    val price: Long = 0L,
    val perfectIvs: Int = 0,
    val rarity: String = "common",
    val untradable: Boolean = true
)

object NativeSkinCatalog {
    val all: List<NativeSkin> get() = SkiesSkinsBridge.skins()
    fun get(id: String): NativeSkin? = SkiesSkinsBridge.skin(id)
    fun page(page: Int, pageSize: Int, source: String? = null): Pair<List<NativeSkin>, Int> {
        val filtered = when (source) {
            null, "", "all" -> all
            "hunter" -> all.filter { it.currency == "huntercoin" }
            else -> all.filter { it.source == source }
        }
        val size = pageSize.coerceIn(8, 32)
        val pages = ((filtered.size + size - 1) / size).coerceAtLeast(1)
        val safe = page.coerceIn(0, pages - 1)
        return filtered.drop(safe * size).take(size) to pages
    }
}

data class NativeGameStats(var played: Int = 0, var wins: Int = 0, var losses: Int = 0, var draws: Int = 0)

data class NativeProfile(
    var schema: Int = 2,
    var arcadeTokens: Long = 0L,
    var gachaTickets: Int = 0,
    var pity: MutableMap<String, Int> = linkedMapOf(),
    var stats: MutableMap<String, NativeGameStats> = linkedMapOf(),
    var lastUpdatedEpochMs: Long = System.currentTimeMillis()
) {
    fun balance(currency: String): Long = when (currency.lowercase()) {
        "arcade" -> arcadeTokens
        "ticket" -> gachaTickets.toLong()
        else -> 0L
    }
    fun debit(currency: String, amount: Long): Boolean {
        if (amount < 0 || balance(currency) < amount) return false
        when (currency.lowercase()) {
            "arcade" -> arcadeTokens -= amount
            "ticket" -> gachaTickets = (gachaTickets - amount.toInt()).coerceAtLeast(0)
            else -> return false
        }
        touch(); return true
    }
    fun credit(currency: String, amount: Long) {
        if (amount <= 0) return
        when (currency.lowercase()) {
            "arcade" -> arcadeTokens = (arcadeTokens + amount).coerceAtMost(MAX_BALANCE)
            "ticket" -> gachaTickets = (gachaTickets + amount.toInt()).coerceAtMost(1_000_000)
            else -> return
        }
        touch()
    }
    fun touch() { lastUpdatedEpochMs = System.currentTimeMillis() }
    companion object { const val MAX_BALANCE = 9_000_000_000_000L }
}

internal fun JsonObject.string(name: String, fallback: String = ""): String = runCatching { get(name)?.asString ?: fallback }.getOrDefault(fallback)
internal fun JsonObject.int(name: String, fallback: Int = 0): Int = runCatching { get(name)?.asInt ?: fallback }.getOrDefault(fallback)
internal fun JsonObject.long(name: String, fallback: Long = 0L): Long = runCatching { get(name)?.asLong ?: fallback }.getOrDefault(fallback)
