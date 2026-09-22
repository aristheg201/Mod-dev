package io.github.aristheg201.svhub.native

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.SVHub
import io.github.aristheg201.svhub.SVHubRuntime
import io.github.aristheg201.svhub.native.game.tft.TftSetRegistry
import io.github.aristheg201.svhub.native.network.NativePlatformNetwork
import io.github.aristheg201.svhub.native.store.*
import io.github.aristheg201.svhub.util.AtomicFiles
import net.minecraft.server.level.ServerPlayer
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

object NativeCosmeticService {
    private val economy = BEconomyAdapter()
    private var offers = emptyList<CosmeticOffer>()
    private val profiles = object : CosmeticProfiles {
        override fun read(player: UUID) = NativeProfileStore.get(player)?.cosmetics
        override fun legacyBalance(player: UUID) = NativeProfileStore.get(player)?.arcadeTokens ?: 0L
        override fun save(player: UUID, account: CosmeticAccount, done: () -> Unit) =
            NativeProfileStore.mutateDurable(player, { it.cosmetics = account.copyDeep() }, done)
        override fun retireLegacy(player: UUID, done: () -> Unit) =
            NativeProfileStore.mutateDurable(player, { it.arcadeTokens = 0L }, done)
    }
    val purchases = CosmeticPurchases(economy, profiles, { offers }) { id, error ->
        SVHub.LOGGER.error("Cosmetic economy operation requires recovery for {}", id, error)
    }
    fun start(path: Path) {
        if (!Files.exists(path)) {
            val defaults = requireNotNull(javaClass.getResourceAsStream("/data/svhub/cosmetic_store.json")).bufferedReader().use { it.readText() }
            AtomicFiles.writeUtf8(path, defaults)
        }
        val set = TftSetRegistry.active()
        offers = JsonParser.parseString(Files.readString(path)).asJsonArray.map { entry ->
            val value = entry.asJsonObject
            val kind = CosmeticKind.valueOf(value.get("kind").asString)
            val id = value.get("id").asString
            require(if (kind == CosmeticKind.ARENA) id in set.rules.arenas &&
                javaClass.getResource("/assets/svhub/arenas/$id.json") != null else set.tacticians.any { it.id == id }) { "Unknown cosmetic $kind:$id" }
            val currency = value.get("currency")?.asString ?: if (kind == CosmeticKind.ARENA) BEconomyAdapter.BEAST else BEconomyAdapter.HUNTER
            CosmeticOffer(kind, id, value.get("price").asString.toBigDecimal(), currency)
        }
        require(offers.map { it.key }.distinct().size == offers.size) { "Duplicate cosmetic catalog keys" }
        require(offers.any { it.kind == CosmeticKind.ARENA && it.id == set.rules.defaultArena && it.price.signum() == 0 })
        require(offers.any { it.kind == CosmeticKind.TACTICIAN && it.id == set.defaultTactician && it.price.signum() == 0 })
    }

    fun verifyProvider() {
        val status = economy.status()
        when {
            status.ready -> SVHub.LOGGER.info(
                "BEconomy integration ready via {} (BeastCoin={}, HunterCoin={})",
                status.providerClass, status.beastCoin, status.hunterCoin
            )
            status.available -> SVHub.LOGGER.error(
                "BEconomy provider {} resolved but required currencies are not ready: {}",
                status.providerClass, status.detail
            )
            else -> SVHub.LOGGER.warn(
                "BEconomy integration unavailable; paid cosmetics and BeastCoin rewards stay disabled until the provider is ready: {}",
                status.detail
            )
        }
    }

    fun recover(player: UUID, done: () -> Unit = {}) {
        purchases.migrate(player) { result ->
            if (result == StoreResult.GRANTED || result == StoreResult.OWNED) purchases.recover(player, done)
        }
    }
    fun selectedArena(player: UUID) = purchases.selected(player, CosmeticKind.ARENA)
    fun selectedTactician(player: UUID) = purchases.selected(player, CosmeticKind.TACTICIAN)
    fun reward(player: UUID, identity: String, amount: Long, done: () -> Unit) {
        recover(player) {
            purchases.creditOnce(player, identity, BigDecimal.valueOf(amount.coerceAtLeast(0))) {
                if (it == StoreResult.GRANTED || it == StoreResult.OWNED) done()
            }
        }
    }
    fun handle(player: ServerPlayer, action: String, data: JsonObject): String {
        if (action == "refresh") { recover(player.uuid); NativePlatform.refresh(player, "store"); return "" }
        val kind = runCatching { CosmeticKind.valueOf(data.string("kind")) }.getOrNull() ?: return "gui.svhub.store.invalid"
        val id = data.string("id")
        val currency = offers.firstOrNull { it.kind == kind && it.id == id }?.currency
            ?: if (kind == CosmeticKind.ARENA) BEconomyAdapter.BEAST else BEconomyAdapter.HUNTER
        val callback: (StoreResult) -> Unit = { result ->
            SVHubRuntime.server?.playerList?.getPlayer(player.uuid)?.let { live ->
                if (NativePlatformNetwork.currentModule(live.uuid) == "store")
                    NativePlatformNetwork.sendState(live, "store", state(live), message(result, kind, currency))
            }
        }
        when (action) {
            "buy" -> purchases.purchase(player.uuid, kind, id, data.string("requestId"), callback)
            "equip" -> purchases.equip(player.uuid, kind, id, callback)
            else -> return "gui.svhub.error.invalid_action"
        }
        return ""
    }
    private fun message(result: StoreResult, kind: CosmeticKind, currency: String) = "gui.svhub.store." + when (result) {
        StoreResult.PURCHASED -> if (kind == CosmeticKind.ARENA) "arena_unlocked" else "tactician_unlocked"
        StoreResult.OWNED -> "already_owned"
        StoreResult.EQUIPPED -> "equipped"
        StoreResult.INSUFFICIENT -> if (currency == BEconomyAdapter.HUNTER) "not_enough_hunter" else "not_enough_beast"
        StoreResult.UNAVAILABLE -> "unavailable"
        StoreResult.PENDING -> "pending"
        StoreResult.GRANTED -> "complete"
        StoreResult.INVALID -> "invalid"
    }
    fun balances(player: UUID) = JsonObject().apply {
        listOf(BEconomyAdapter.BEAST, BEconomyAdapter.HUNTER).forEach { currency ->
            val value = runCatching { if (economy.currencyExists(currency)) economy.balance(player, currency).toPlainString() else null }.getOrNull()
            if (value != null) addProperty(currency, value)
        }
    }
    fun state(player: ServerPlayer) = JsonObject().apply {
        addProperty("module", "store")
        add("balances", balances(player.uuid))
        addProperty("arena", selectedArena(player.uuid) ?: TftSetRegistry.active().rules.defaultArena)
        addProperty("tactician", selectedTactician(player.uuid) ?: TftSetRegistry.active().defaultTactician)
        add("offers", JsonArray().also { array -> offers.forEach { offer ->
            array.add(JsonObject().apply {
                addProperty("id", offer.id); addProperty("kind", offer.kind.name)
                addProperty("price", offer.price.toPlainString()); addProperty("currency", offer.currency)
                addProperty("owned", purchases.owns(player.uuid, offer))
                addProperty("equipped", (if (offer.kind == CosmeticKind.ARENA) selectedArena(player.uuid) ?: TftSetRegistry.active().rules.defaultArena
                    else selectedTactician(player.uuid) ?: TftSetRegistry.active().defaultTactician) == offer.id)
                TftSetRegistry.active().tacticians.find { offer.kind == CosmeticKind.TACTICIAN && it.id == offer.id }?.let { definition ->
                    addProperty("name", definition.name); addProperty("entity", definition.entity)
                    addProperty("species", definition.presentation?.species.orEmpty())
                    addProperty("aspects", definition.presentation?.resolverAspects()?.joinToString(",").orEmpty())
                    addProperty("scale", definition.scale); addProperty("vfx", definition.cosmeticVfx)
                }
            })
        } })
    }
}
