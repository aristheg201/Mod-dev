package io.github.aristheg201.svarcade.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random

data class NativeBanner(
    val id: String,
    val name: String,
    val source: String,
    val costTickets: Int,
    val pity: Int
)

object NativeGachaService {
    data class RollResult(val ok: Boolean, val message: String, val state: JsonObject? = null)

    val banners = listOf(
        NativeBanner("hunter", "Hunter Capsule", "hunter", 1, 30),
        NativeBanner("beast", "Dragon Capsule", "dbz", 1, 25)
    )

    fun requestRoll(player: ServerPlayer, bannerId: String, requestId: String): RollResult =
        NativeGachaTransactionService.request(player, bannerId, requestId)

    fun recoverPlayer(player: ServerPlayer) = NativeGachaTransactionService.recoverPlayer(player)

    fun state(player: ServerPlayer, selectedBanner: String = "hunter"): JsonObject {
        val profile = NativeProfileStore.get(player.uuid) ?: NativeProfile()
        val selected = banners.firstOrNull { it.id == selectedBanner } ?: banners.first()
        val pool = poolFor(selected)
        val owned = SkiesSkinsBridge.ownedIds(player)
        val activeRequest = NativeGachaTransactionService.activeRequest(player.uuid)
        return JsonObject().apply {
            addProperty("module", "gacha")
            addProperty("backend", "SkiesSkins")
            addProperty("backendReady", SkiesSkinsBridge.available())
            addProperty("selected", selected.id)
            addProperty("rolling", activeRequest != null)
            activeRequest?.takeIf { it != "processing" }?.let { addProperty("requestId", it) }
            add("wallet", NativeSkinService.walletJson(profile))
            add("banners", JsonArray().also { array ->
                banners.forEach { banner ->
                    array.add(JsonObject().apply {
                        addProperty("id", banner.id)
                        addProperty("name", banner.name)
                        addProperty("costTickets", banner.costTickets)
                        addProperty("pity", banner.pity)
                        addProperty("currentPity", profile.pity[banner.id] ?: 0)
                        addProperty("poolSize", poolFor(banner).size)
                    })
                }
            })
            add("preview", JsonArray().also { array ->
                pool.sortedByDescending(::rarityRank).take(18).forEach {
                    array.add(NativeSkinService.skinJson(it, owned))
                }
            })
        }
    }

    internal fun poolFor(banner: NativeBanner): List<NativeSkin> =
        NativeSkinCatalog.all.filter { matchesBanner(it, banner) }

    internal fun pickForRoll(pool: List<NativeSkin>, forcePremium: Boolean, rng: Random): NativeSkin {
        val byRarity = pool.groupBy { normalizeRarity(it.rarity) }
        if (forcePremium) {
            val premium = PREMIUM.flatMap { byRarity[it].orEmpty() }
            if (premium.isNotEmpty()) return premium[rng.nextInt(premium.size)]
        }
        val available = RARITY_WEIGHTS.filterKeys { !byRarity[it].isNullOrEmpty() }
        val total = available.values.sum().coerceAtLeast(1)
        var roll = rng.nextInt(total)
        var rarity = available.keys.firstOrNull() ?: return pool[rng.nextInt(pool.size)]
        for ((candidate, weight) in available) {
            roll -= weight
            if (roll < 0) { rarity = candidate; break }
        }
        val list = byRarity.getValue(rarity)
        return list[rng.nextInt(list.size)]
    }

    internal fun buildStrip(pool: List<NativeSkin>, winner: NativeSkin, seed: Long): JsonArray =
        JsonArray().also { array ->
            val rng = Random(seed xor 0x5A17C0B1L)
            repeat(39) { index ->
                val skin = if (index == 32) winner else pickForRoll(pool, false, rng)
                array.add(JsonObject().apply {
                    addProperty("id", skin.id)
                    addProperty("name", skin.name)
                    addProperty("rarity", skin.rarity)
                    addProperty("species", skin.species)
                    addProperty("aspect", skin.aspect)
                })
            }
        }

    internal fun isPremium(rarity: String): Boolean = rarity.lowercase() in PREMIUM

    private fun matchesBanner(skin: NativeSkin, banner: NativeBanner): Boolean =
        when (banner.source) {
            "hunter" -> skin.source != "dbz"
            else -> skin.source == banner.source
        }

    private fun normalizeRarity(rarity: String): String =
        rarity.lowercase().let { if (it in RARITY_WEIGHTS) it else "common" }

    private fun rarityRank(skin: NativeSkin): Int = when (skin.rarity.lowercase()) {
        "legendary" -> 5
        "mythic" -> 4
        "epic" -> 3
        "rare" -> 2
        else -> 1
    }

    private val PREMIUM = setOf("legendary", "mythic")
    private val RARITY_WEIGHTS = linkedMapOf(
        "legendary" to 100,
        "mythic" to 400,
        "epic" to 1200,
        "rare" to 2800,
        "uncommon" to 3500,
        "common" to 2000
    )
}
