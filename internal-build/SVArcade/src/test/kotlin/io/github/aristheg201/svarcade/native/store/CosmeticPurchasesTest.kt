package io.github.aristheg201.svarcade.native.store

import com.google.gson.Gson
import io.github.aristheg201.svarcade.native.NativeProfile
import io.github.aristheg201.svarcade.util.AtomicFiles
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class CosmeticPurchasesTest {
    @TempDir lateinit var directory: Path
    @org.junit.jupiter.api.BeforeEach fun configureReward() {
        val config = directory.resolve("economy.json")
        Files.writeString(config, """{"defaults":{"reward":"BeastCoin"}}""")
        EconomyConfig.start(config)
    }
    private val player = UUID.randomUUID()
    private val arena = CosmeticOffer(CosmeticKind.ARENA, "monster_island", BigDecimal("500"), "BeastCoin")
    private val free = CosmeticOffer(CosmeticKind.ARENA, "kanto_stadium", BigDecimal.ZERO)
    private val tactician = CosmeticOffer(CosmeticKind.TACTICIAN, "svarcade:eevee", BigDecimal("100.25"), "HunterCoin")
    private val hunterArena = CosmeticOffer(CosmeticKind.ARENA, "arkham_asylum", BigDecimal("725"), "HunterCoin")
    private val api = Api15()
    private val profiles = Profiles()
    private fun engine() = CosmeticPurchases(BEconomyAdapter { api }, profiles, { listOf(arena, free, tactician, hunterArena) })
    private fun buy(engine: CosmeticPurchases, offer: CosmeticOffer = arena, request: String = UUID.randomUUID().toString()): StoreResult {
        var result: StoreResult? = null
        engine.purchase(player, offer.kind, offer.id, request) { result = it }
        return requireNotNull(result)
    }

    @Test fun `adapter reads exact decimal balance`() {
        api.balances["BeastCoin"] = BigDecimal("123456789.123456789")
        assertEquals(BigDecimal("123456789.123456789"), BEconomyAdapter { api }.balance(player, "BeastCoin"))
    }
    @Test fun `arena debits BeastCoin once and grants ownership`() {
        val engine = engine()
        assertEquals(StoreResult.PURCHASED, buy(engine))
        assertEquals(BigDecimal("1500"), api.balances["BeastCoin"])
        assertTrue(engine.owns(player, arena))
        assertEquals(1, api.debits)
    }
    @Test fun `premium arena can debit HunterCoin without changing legacy arena currency`() {
        assertEquals(StoreResult.PURCHASED, buy(engine(), hunterArena))
        assertEquals(BigDecimal("1275"), api.balances["HunterCoin"])
        assertEquals(BigDecimal("2000"), api.balances["BeastCoin"])
        assertEquals("HunterCoin", hunterArena.currency)
        assertEquals("BeastCoin", arena.currency)
    }
    @Test fun `tactician debits HunterCoin preserving decimals`() {
        assertEquals(StoreResult.PURCHASED, buy(engine(), tactician))
        assertEquals(BigDecimal("1899.75"), api.balances["HunterCoin"])
        assertEquals(BigDecimal("2000"), api.balances["BeastCoin"])
    }
    @Test fun `insufficient balance cannot grant or debit`() {
        api.balances["BeastCoin"] = BigDecimal.ONE
        val engine = engine()
        assertEquals(StoreResult.INSUFFICIENT, buy(engine))
        assertFalse(engine.owns(player, arena)); assertEquals(0, api.debits)
    }
    @Test fun `conditional debit refusal after balance read cannot unlock`() {
        api.decline = true
        val engine = engine()
        assertEquals(StoreResult.INSUFFICIENT, buy(engine))
        assertFalse(engine.owns(player, arena))
        api.decline = false
        assertEquals(StoreResult.PURCHASED, buy(engine))
        assertEquals(1, api.debits)
    }
    @Test fun `duplicate requests and different requests for owned item never debit again`() {
        val engine = engine(); val request = UUID.randomUUID().toString()
        assertEquals(StoreResult.PURCHASED, buy(engine, request = request))
        repeat(5) { assertEquals(StoreResult.OWNED, buy(engine, request = request)) }
        assertEquals(StoreResult.OWNED, buy(engine)); assertEquals(1, api.debits)
    }
    @Test fun `free arena equips without an economy`() {
        api.unavailable = true
        val engine = engine()
        assertEquals(StoreResult.OWNED, buy(engine, free))
        engine.equip(player, free.kind, free.id) { assertEquals(StoreResult.EQUIPPED, it) }
        assertEquals(free.id, engine.selected(player, free.kind)); assertEquals(0, api.debits)
    }
    @Test fun `direct reward grant is durable and prevents a later charge`() {
        val engine = engine()
        engine.grant(player, arena.kind, arena.id) { assertEquals(StoreResult.GRANTED, it) }
        profiles.reload()
        assertEquals(StoreResult.OWNED, buy(engine())); assertEquals(0, api.debits)
    }
    @Test fun `entitlement and equipped selection survive disk recovery`() {
        val engine = engine()
        buy(engine)
        engine.equip(player, arena.kind, arena.id) { assertEquals(StoreResult.EQUIPPED, it) }
        profiles.reload()
        assertTrue(engine().owns(player, arena))
        assertEquals(arena.id, engine().selected(player, arena.kind))
    }
    @Test fun `debit followed by interrupted entitlement save recovers from confirmed record`() {
        profiles.stopAfterSave = 2
        engine().purchase(player, arena.kind, arena.id, UUID.randomUUID().toString()) { fail("Save callback must be interrupted") }
        assertEquals(1, api.debits)
        profiles.reload(); profiles.stopAfterSave = Int.MAX_VALUE
        engine().recover(player) {}
        assertTrue(engine().owns(player, arena)); assertEquals(1, api.debits)
    }
    @Test fun `provider receipt recovers a debit whose local confirmation was lost`() {
        profiles.failOnSave = 2
        assertEquals(StoreResult.UNAVAILABLE, buy(engine()))
        profiles.reload(); profiles.failOnSave = Int.MAX_VALUE
        engine().recover(player) {}
        assertTrue(engine().owns(player, arena)); assertEquals(1, api.debits)
    }
    @Test fun `ambiguous interrupted debit stays pending without a second debit or free unlock`() {
        api.throwAfterDebit = true
        assertEquals(StoreResult.UNAVAILABLE, buy(engine()))
        profiles.reload(); api.throwAfterDebit = false
        repeat(3) { assertEquals(StoreResult.PENDING, buy(engine())) }
        assertFalse(engine().owns(player, arena)); assertEquals(1, api.debits)
    }
    @Test fun `concurrent request during durable save cannot double debit`() {
        val engine = engine(); profiles.stopAfterSave = 1
        engine.purchase(player, arena.kind, arena.id, UUID.randomUUID().toString()) {}
        assertEquals(StoreResult.PENDING, buy(engine)); assertEquals(0, api.debits)
    }
    @Test fun `migration runs once and reconnect never repeats credit`() {
        profiles.profile.arcadeTokens = 345
        engine().migrate(player) { assertEquals(StoreResult.GRANTED, it) }
        assertEquals(BigDecimal("2345"), api.balances["BeastCoin"])
        assertEquals(0L, profiles.profile.arcadeTokens)
        profiles.reload()
        repeat(3) { engine().migrate(player) { assertEquals(StoreResult.OWNED, it) } }
        assertEquals(1, api.credits); assertEquals(1, profiles.profile.cosmetics.migrationVersion)
    }
    @Test fun `interrupted migration confirms once then retires legacy balance on reconnect`() {
        profiles.profile.arcadeTokens = 90
        profiles.stopAfterSave = 2
        engine().migrate(player) { fail("Migration interrupted") }
        assertEquals(1, api.credits)
        profiles.reload(); profiles.stopAfterSave = Int.MAX_VALUE
        engine().migrate(player) { assertEquals(StoreResult.GRANTED, it) }
        assertEquals(0L, profiles.profile.arcadeTokens); assertEquals(1, api.credits)
    }
    @Test fun `old token wallet is no longer spendable or credited`() {
        profiles.profile.arcadeTokens = 1_000_000
        api.balances["BeastCoin"] = BigDecimal.ZERO
        assertEquals(StoreResult.INSUFFICIENT, buy(engine()))
        assertEquals(0L, profiles.profile.balance("arcade"))
        assertFalse(profiles.profile.debit("arcade", 1))
        profiles.profile.credit("arcade", 50)
        assertEquals(1_000_000L, profiles.profile.arcadeTokens)
    }
    @Test fun `invalid arena tactician and request are rejected`() {
        val engine = engine()
        CosmeticKind.entries.forEach { kind -> engine.purchase(player, kind, "missing", UUID.randomUUID().toString()) { assertEquals(StoreResult.INVALID, it) } }
        assertEquals(StoreResult.INVALID, buy(engine, request = "bad")); assertEquals(0, api.debits)
    }
    @Test fun `unavailable currency cannot debit or unlock`() {
        api.unavailable = true
        assertEquals(StoreResult.UNAVAILABLE, buy(engine())); assertEquals(0, api.debits)
    }
    @Test fun `unowned cosmetics cannot equip including corrupted equipped references`() {
        val engine = engine()
        engine.equip(player, arena.kind, arena.id) { assertEquals(StoreResult.INVALID, it) }
        profiles.profile.cosmetics.equipped[arena.kind.name] = arena.id
        assertNull(engine.selected(player, arena.kind))
    }
    @Test fun `reward credits are idempotent across reconnect`() {
        engine().creditOnce(player, "reward:match-one", BigDecimal("15")) { assertEquals(StoreResult.GRANTED, it) }
        profiles.reload()
        engine().creditOnce(player, "reward:match-one", BigDecimal("15")) { assertEquals(StoreResult.GRANTED, it) }
        assertEquals(1, api.credits)
    }

    private inner class Profiles : CosmeticProfiles {
        var profile = NativeProfile()
        var saves = 0
        var stopAfterSave = Int.MAX_VALUE
        var failOnSave = Int.MAX_VALUE
        private val file get() = directory.resolve("$player.json")
        override fun read(player: UUID) = profile.cosmetics
        override fun legacyBalance(player: UUID) = profile.arcadeTokens
        override fun save(player: UUID, account: CosmeticAccount, done: () -> Unit) {
            saves++
            if (saves == failOnSave) error("Simulated disk failure")
            profile.cosmetics = account.copyDeep()
            AtomicFiles.writeUtf8(file, Gson().toJson(profile))
            if (saves != stopAfterSave) done()
        }
        override fun retireLegacy(player: UUID, done: () -> Unit) {
            profile.arcadeTokens = 0
            AtomicFiles.writeUtf8(file, Gson().toJson(profile)); done()
        }
        fun reload() { profile = Gson().fromJson(Files.readString(file), NativeProfile::class.java) }
    }

    /** Test double with the exact javap-confirmed API 1.5 signatures. */
    class Api15 {
        val balances = mutableMapOf("BeastCoin" to BigDecimal("2000"), "HunterCoin" to BigDecimal("2000"))
        val history = mutableListOf<Receipt>()
        var debits = 0; var credits = 0; var unavailable = false; var decline = false; var throwAfterDebit = false
        fun currencyExists(currency: String) = !unavailable && currency in balances
        fun getBalance(player: UUID, currency: String): BigDecimal = balances.getValue(currency)
        fun subtractBalance(player: UUID, amount: BigDecimal, currency: String): Boolean {
            if (decline || getBalance(player, currency) < amount) return false
            balances[currency] = getBalance(player, currency) - amount; debits++
            if (throwAfterDebit) error("Interrupted after balance change")
            return true
        }
        fun addBalance(player: UUID, amount: BigDecimal, currency: String) { balances[currency] = getBalance(player, currency) + amount; credits++ }
        fun logTransaction(player: UUID, currency: String, amount: BigDecimal, type: String) { history += Receipt(player, currency, amount, type) }
        fun getTransactionHistory(player: UUID) = history.filter { it.playerId == player }
    }
    data class Receipt(val playerId: UUID, val currencyType: String, val amount: BigDecimal, val type: String)
}
