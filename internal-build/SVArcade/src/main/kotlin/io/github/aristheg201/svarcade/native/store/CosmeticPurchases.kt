package io.github.aristheg201.svarcade.native.store

import java.math.BigDecimal
import java.util.UUID

enum class CosmeticKind { ARENA, TACTICIAN }
data class CosmeticOffer(
    val kind: CosmeticKind,
    val id: String,
    val price: BigDecimal,
    val currency: String = EconomyConfig.defaultCurrency(kind.name)
) {
    init {
        require(price.signum() >= 0)
        require(currency.isNotBlank() || price.signum() == 0)
    }
    val key: String get() = "${kind.name.lowercase()}:$id"
}
enum class MoneyPhase { STARTED, CONFIRMED, COMPLETE, DECLINED }
data class CosmeticPayment(
    val identity: String, val currency: String, val amount: BigDecimal,
    val requestId: String, var phase: MoneyPhase = MoneyPhase.STARTED
)
data class CosmeticAccount(
    val schema: Int = 1,
    val entitlements: MutableSet<String> = linkedSetOf(),
    val equipped: MutableMap<String, String> = linkedMapOf(),
    val payments: MutableMap<String, CosmeticPayment> = linkedMapOf(),
    var migrationVersion: Int = 0
) {
    fun copyDeep() = copy(entitlements = entitlements.toMutableSet(), equipped = equipped.toMutableMap(),
        payments = payments.mapValuesTo(linkedMapOf()) { it.value.copy() })
}

/** All callbacks and economy operations run on the server thread; saves complete after fsync. */
interface CosmeticProfiles {
    fun read(player: UUID): CosmeticAccount?
    fun save(player: UUID, account: CosmeticAccount, done: () -> Unit)
    fun legacyBalance(player: UUID): Long
    fun retireLegacy(player: UUID, done: () -> Unit)
}
enum class StoreResult { PURCHASED, OWNED, EQUIPPED, INVALID, INSUFFICIENT, UNAVAILABLE, PENDING, GRANTED }

class CosmeticPurchases(
    private val economy: CosmeticEconomy,
    private val profiles: CosmeticProfiles,
    private val catalog: () -> List<CosmeticOffer>,
    private val log: (UUID, Throwable) -> Unit = { _, _ -> }
) {
    private val busy = mutableSetOf<UUID>()
    fun owns(player: UUID, offer: CosmeticOffer): Boolean = offer.price.signum() == 0 || profiles.read(player)?.entitlements?.contains(offer.key) == true
    fun selected(player: UUID, kind: CosmeticKind): String? = profiles.read(player)?.equipped?.get(kind.name)?.takeIf { id ->
        catalog().any { it.kind == kind && it.id == id && owns(player, it) }
    }
    fun purchase(player: UUID, kind: CosmeticKind, id: String, requestId: String, done: (StoreResult) -> Unit) {
        val offer = catalog().find { it.kind == kind && it.id == id }
        if (offer == null || runCatching { UUID.fromString(requestId) }.isFailure) { done(StoreResult.INVALID); return }
        if (!busy.add(player)) { done(StoreResult.PENDING); return }
        val finish: (StoreResult) -> Unit = { busy.remove(player); done(it) }
        val account = profiles.read(player)?.copyDeep() ?: run { finish(StoreResult.UNAVAILABLE); return }
        if (owns(player, offer)) { finish(StoreResult.OWNED); return }
        val previous = account.payments[offer.key]
        if (previous != null && previous.phase != MoneyPhase.DECLINED) {
            recoverPurchase(player, offer, account, previous, finish); return
        }
        guarded(player, finish) {
            if (!economy.currencyExists(offer.currency)) { finish(StoreResult.UNAVAILABLE); return@guarded }
            if (economy.balance(player, offer.currency) < offer.price) { finish(StoreResult.INSUFFICIENT); return@guarded }
            // Identity is bound to player + catalog item. A different client request cannot charge it twice.
            val identity = "purchase:$player:${offer.key}"
            val payment = CosmeticPayment(identity, economy.resolveCurrency(offer.currency), offer.price, requestId)
            account.payments[offer.key] = payment
            profiles.save(player, account.copyDeep()) {
                guarded(player, finish) {
                    if (!economy.debit(player, payment.amount, payment.currency)) {
                        payment.phase = MoneyPhase.DECLINED
                        profiles.save(player, account) { finish(StoreResult.INSUFFICIENT) }
                    } else {
                        // Persist our confirmation even if the optional history annotation fails.
                        payment.phase = MoneyPhase.CONFIRMED
                        runCatching { economy.recordReceipt(player, identity, payment.amount.negate(), payment.currency) }.onFailure { log(player, it) }
                        profiles.save(player, account.copyDeep()) { grantPaid(player, offer, account, finish) }
                    }
                }
            }
        }
    }
    private fun recoverPurchase(player: UUID, offer: CosmeticOffer, account: CosmeticAccount, payment: CosmeticPayment, done: (StoreResult) -> Unit) {
        guarded(player, done) {
            val confirmed = payment.phase == MoneyPhase.CONFIRMED || payment.phase == MoneyPhase.COMPLETE ||
                economy.receipt(player, payment.identity, payment.amount.negate(), payment.currency)
            if (confirmed) grantPaid(player, offer, account, done) else done(StoreResult.PENDING)
        }
    }
    private fun grantPaid(player: UUID, offer: CosmeticOffer, account: CosmeticAccount, done: (StoreResult) -> Unit) {
        account.entitlements += offer.key
        account.payments.getValue(offer.key).phase = MoneyPhase.COMPLETE
        profiles.save(player, account) { done(StoreResult.PURCHASED) }
    }
    fun equip(player: UUID, kind: CosmeticKind, id: String, done: (StoreResult) -> Unit) {
        val offer = catalog().find { it.kind == kind && it.id == id }
        if (offer == null || !owns(player, offer)) { done(StoreResult.INVALID); return }
        if (!busy.add(player)) { done(StoreResult.PENDING); return }
        val account = profiles.read(player)?.copyDeep() ?: run { busy.remove(player); done(StoreResult.UNAVAILABLE); return }
        account.equipped[kind.name] = id
        profiles.save(player, account) { busy.remove(player); done(StoreResult.EQUIPPED) }
    }
    /** Server reward hook; never exposed as a client intent. */
    fun grant(player: UUID, kind: CosmeticKind, id: String, done: (StoreResult) -> Unit) {
        val offer = catalog().find { it.kind == kind && it.id == id }
        if (offer == null) { done(StoreResult.INVALID); return }
        if (!busy.add(player)) { done(StoreResult.PENDING); return }
        val account = profiles.read(player)?.copyDeep() ?: run { busy.remove(player); done(StoreResult.UNAVAILABLE); return }
        account.entitlements += offer.key
        profiles.save(player, account) { busy.remove(player); done(StoreResult.GRANTED) }
    }
    fun migrate(player: UUID, done: (StoreResult) -> Unit) {
        val account = profiles.read(player) ?: run { done(StoreResult.UNAVAILABLE); return }
        if (account.migrationVersion >= 1) { done(StoreResult.OWNED); return }
        creditOnce(player, "migration:v1", BigDecimal.valueOf(profiles.legacyBalance(player).coerceAtLeast(0)), done)
    }
    fun recover(player: UUID, done: () -> Unit) {
        val account = profiles.read(player) ?: return
        val unfinished = catalog().filter { it.key !in account.entitlements && account.payments[it.key]?.phase in setOf(MoneyPhase.STARTED, MoneyPhase.CONFIRMED, MoneyPhase.COMPLETE) }.iterator()
        fun next() {
            if (!unfinished.hasNext()) { done(); return }
            val offer = unfinished.next()
            purchase(player, offer.kind, offer.id, account.payments.getValue(offer.key).requestId) { next() }
        }
        next()
    }
    fun creditOnce(player: UUID, identity: String, amount: BigDecimal, done: (StoreResult) -> Unit) {
        require(amount.signum() >= 0)
        if (!busy.add(player)) { done(StoreResult.PENDING); return }
        val finish: (StoreResult) -> Unit = { busy.remove(player); done(it) }
        val account = profiles.read(player)?.copyDeep() ?: run { finish(StoreResult.UNAVAILABLE); return }
        val key = "credit:$identity"
        fun complete() {
            account.payments[key]?.phase = MoneyPhase.COMPLETE
            if (identity == "migration:v1") account.migrationVersion = 1
            profiles.save(player, account) {
                if (identity == "migration:v1") profiles.retireLegacy(player) { finish(StoreResult.GRANTED) }
                else finish(StoreResult.GRANTED)
            }
        }
        guarded(player, finish) {
            val old = account.payments[key]
            if (old != null) {
                if (old.phase == MoneyPhase.CONFIRMED || old.phase == MoneyPhase.COMPLETE ||
                    economy.receipt(player, old.identity, old.amount, old.currency)) complete()
                else finish(StoreResult.PENDING)
                return@guarded
            }
            if (amount.signum() == 0) { complete(); return@guarded }
            if (!economy.currencyExists(EconomyConfig.defaultCurrency("reward"))) { finish(StoreResult.UNAVAILABLE); return@guarded }
            val payment = CosmeticPayment("$key:$player", economy.resolveCurrency(EconomyConfig.defaultCurrency("reward")), amount, identity)
            account.payments[key] = payment
            profiles.save(player, account.copyDeep()) {
                guarded(player, finish) {
                    economy.credit(player, payment.amount, payment.currency)
                    payment.phase = MoneyPhase.CONFIRMED
                    runCatching { economy.recordReceipt(player, payment.identity, amount, payment.currency) }.onFailure { log(player, it) }
                    profiles.save(player, account.copyDeep()) { complete() }
                }
            }
        }
    }
    private inline fun guarded(player: UUID, done: (StoreResult) -> Unit, action: () -> Unit) {
        try { action() } catch (error: Exception) { log(player, error); done(StoreResult.UNAVAILABLE) }
        catch (error: LinkageError) { log(player, error); done(StoreResult.UNAVAILABLE) }
    }
}
