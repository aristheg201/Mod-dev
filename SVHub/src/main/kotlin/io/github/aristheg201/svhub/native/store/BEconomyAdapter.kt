package io.github.aristheg201.svhub.native.store

import java.math.BigDecimal
import java.util.UUID

interface CosmeticEconomy {
    fun currencyExists(currency: String): Boolean
    fun balance(player: UUID, currency: String): BigDecimal
    fun debit(player: UUID, amount: BigDecimal, currency: String): Boolean
    fun credit(player: UUID, amount: BigDecimal, currency: String)
    fun receipt(player: UUID, identity: String, amount: BigDecimal, currency: String): Boolean
    fun recordReceipt(player: UUID, identity: String, amount: BigDecimal, currency: String)
}

/** Optional server dependency. Signatures verified against the supplied BEconomy 1.5 JAR.
 * Reflection stays here so neither the client nor servers without the mod link its classes.
 */
class BEconomyAdapter(private val api: () -> Any = ::resolveAPI) : CosmeticEconomy {
    override fun currencyExists(currency: String) = invoke("currencyExists", arrayOf(String::class.java), currency) as Boolean
    override fun balance(player: UUID, currency: String) =
        invoke("getBalance", arrayOf(UUID::class.java, String::class.java), player, currency) as BigDecimal
    override fun debit(player: UUID, amount: BigDecimal, currency: String): Boolean {
        require(amount.signum() > 0)
        return invoke("subtractBalance", MONEY_TYPES, player, amount, currency) as Boolean
    }
    override fun credit(player: UUID, amount: BigDecimal, currency: String) {
        require(amount.signum() > 0)
        invoke("addBalance", MONEY_TYPES, player, amount, currency)
    }
    override fun recordReceipt(player: UUID, identity: String, amount: BigDecimal, currency: String) {
        invoke("logTransaction", arrayOf(UUID::class.java, String::class.java, BigDecimal::class.java, String::class.java),
            player, currency, amount, "svhub:$identity")
    }
    override fun receipt(player: UUID, identity: String, amount: BigDecimal, currency: String): Boolean {
        val history = invoke("getTransactionHistory", arrayOf(UUID::class.java), player) as List<*>
        return history.filterNotNull().any { transaction ->
            fun field(name: String) = transaction.javaClass.getMethod(name).invoke(transaction)
            field("getPlayerId") == player && field("getType") == "svhub:$identity" &&
                field("getCurrencyType") == currency && (field("getAmount") as BigDecimal).compareTo(amount) == 0
        }
    }
    private fun invoke(name: String, types: Array<Class<*>>, vararg values: Any): Any? {
        val target = api()
        return target.javaClass.getMethod(name, *types).invoke(target, *values)
    }
    companion object {
        const val BEAST = "BeastCoin"
        const val HUNTER = "HunterCoin"
        private val MONEY_TYPES = arrayOf<Class<*>>(UUID::class.java, BigDecimal::class.java, String::class.java)
        private fun resolveAPI(): Any {
            val entry = Class.forName("org.krripe.beconomy.api.BEconomy")
            val instance = entry.getField("INSTANCE").get(null)
            check(entry.getMethod("isInitialized").invoke(instance) == true) { "BEconomy is not initialized" }
            return requireNotNull(entry.getMethod("getAPI").invoke(instance))
        }
    }
}
