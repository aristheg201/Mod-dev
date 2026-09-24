package org.blanketeconomy.api

import net.minecraft.server.MinecraftServer
import java.math.BigDecimal
import java.util.UUID

/**
 * Test-only stand-in with the same Kotlin-object entrypoint shape as BEconomy.
 * Production never packages this source set.
 */
object BlanketEconomy {
    val api = TestEconomyApi()
    var initialized = true

    fun isInitialized(): Boolean = initialized
    fun getAPI(): Any {
        check(initialized)
        return api
    }
    fun getAPI(server: MinecraftServer): Any {
        initialized = true
        return api
    }
    fun setServer(server: MinecraftServer) = Unit
    fun reset() {
        initialized = true
        api.reset()
    }
}

class TestEconomyApi {
    val balances = linkedMapOf(
        "BeastCoin" to BigDecimal("2000"),
        "HunterCoin" to BigDecimal("1500")
    )
    val history = mutableListOf<TestTransaction>()

    fun reset() {
        balances.clear()
        balances["BeastCoin"] = BigDecimal("2000")
        balances["HunterCoin"] = BigDecimal("1500")
        history.clear()
    }

    fun currencyExists(currencyType: String) = currencyType in balances
    fun getBalance(playerId: UUID, currencyType: String): BigDecimal = balances.getValue(currencyType)

    fun addBalance(playerId: UUID, amount: BigDecimal, currencyType: String) {
        balances[currencyType] = getBalance(playerId, currencyType).add(amount)
        logTransaction(playerId, currencyType, amount, "credit")
    }

    fun subtractBalance(playerId: UUID, amount: BigDecimal, currencyType: String): Boolean {
        val balance = getBalance(playerId, currencyType)
        if (balance < amount) return false
        balances[currencyType] = balance.subtract(amount)
        logTransaction(playerId, currencyType, amount, "debit")
        return true
    }

    fun logTransaction(playerId: UUID, currencyType: String, amount: BigDecimal, type: String) {
        history += TestTransaction(playerId, currencyType, amount, type)
    }

    fun getTransactionHistory(playerId: UUID): List<TestTransaction> =
        history.filter { it.playerId == playerId }
}

data class TestTransaction(
    val playerId: UUID,
    val currencyType: String,
    val amount: BigDecimal,
    val type: String
)
