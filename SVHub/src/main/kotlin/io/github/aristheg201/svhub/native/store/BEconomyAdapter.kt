package io.github.aristheg201.svhub.native.store

import io.github.aristheg201.svhub.SVHubRuntime
import net.minecraft.server.MinecraftServer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
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

data class BEconomyStatus(
    val available: Boolean,
    val providerClass: String = "",
    val beastCoin: Boolean = false,
    val hunterCoin: Boolean = false,
    val detail: String = ""
) {
    val ready: Boolean get() = available && beastCoin && hunterCoin
}

/**
 * Server-only bridge to BEconomy / BlanketEconomy.
 *
 * The provider remains reflection-isolated so clients and servers without the optional
 * economy mod can still load SVHub. The live provider currently exposes the Kotlin object
 * org.blanketeconomy.api.BlanketEconomy; the older package name remains as a compatibility
 * candidate for servers that still ship that API surface.
 */
class BEconomyAdapter(private val apiSupplier: (() -> Any)? = null) : CosmeticEconomy {
    @Volatile private var cachedApi: Any? = null
    @Volatile private var resolvedProviderClass: String = if (apiSupplier != null) "injected" else ""

    override fun currencyExists(currency: String) =
        invoke("currencyExists", arrayOf(String::class.java), currency) as Boolean

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
        invoke(
            "logTransaction",
            arrayOf(UUID::class.java, String::class.java, BigDecimal::class.java, String::class.java),
            player, currency, amount, "svhub:$identity"
        )
    }

    override fun receipt(player: UUID, identity: String, amount: BigDecimal, currency: String): Boolean {
        val history = invoke("getTransactionHistory", arrayOf(UUID::class.java), player) as List<*>
        return history.filterNotNull().any { transaction ->
            fun field(name: String) = transaction.javaClass.getMethod(name).invoke(transaction)
            field("getPlayerId") == player &&
                field("getType") == "svhub:$identity" &&
                field("getCurrencyType") == currency &&
                (field("getAmount") as BigDecimal).compareTo(amount) == 0
        }
    }

    /**
     * Resolves the real provider and verifies the two currencies SVHub actually consumes.
     * Failures are intentionally not cached: BEconomy may finish initializing after SVHub.
     */
    fun status(): BEconomyStatus = try {
        target()
        val beast = currencyExists(BEAST)
        val hunter = currencyExists(HUNTER)
        BEconomyStatus(
            available = true,
            providerClass = resolvedProviderClass,
            beastCoin = beast,
            hunterCoin = hunter,
            detail = when {
                beast && hunter -> "ready"
                !beast && !hunter -> "missing BeastCoin and HunterCoin"
                !beast -> "missing BeastCoin"
                else -> "missing HunterCoin"
            }
        )
    } catch (error: Throwable) {
        BEconomyStatus(
            available = false,
            providerClass = resolvedProviderClass,
            detail = rootCause(error).let { cause ->
                cause.javaClass.simpleName + (cause.message?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
            }
        )
    }

    private fun invoke(name: String, types: Array<Class<*>>, vararg values: Any): Any? {
        val target = target()
        return target.javaClass.getMethod(name, *types).invoke(target, *values)
    }

    private fun target(): Any {
        cachedApi?.let { return it }
        synchronized(this) {
            cachedApi?.let { return it }
            val resolved = apiSupplier?.invoke() ?: resolveProviderApi()
            cachedApi = resolved
            return resolved
        }
    }

    private fun resolveProviderApi(): Any {
        var lastFailure: Throwable? = null
        for (className in PROVIDER_CLASSES) {
            val entry = try {
                Class.forName(className)
            } catch (missing: ClassNotFoundException) {
                lastFailure = missing
                continue
            }
            resolvedProviderClass = className
            try {
                val receiver = runCatching { entry.getField("INSTANCE").get(null) }.getOrNull()
                val initialized = runCatching {
                    invokeProvider(entry, receiver, "isInitialized", emptyArray()) as? Boolean
                }.getOrNull()

                if (initialized != false) {
                    runCatching { invokeProvider(entry, receiver, "getAPI", emptyArray()) }
                        .getOrNull()?.let { return it }
                }

                val server = SVHubRuntime.server
                if (server != null) {
                    runCatching {
                        invokeProvider(
                            entry, receiver, "getAPI",
                            arrayOf(MinecraftServer::class.java), server
                        )
                    }.getOrNull()?.let { return it }

                    runCatching {
                        invokeProvider(
                            entry, receiver, "setServer",
                            arrayOf(MinecraftServer::class.java), server
                        )
                    }
                    runCatching { invokeProvider(entry, receiver, "getAPI", emptyArray()) }
                        .getOrNull()?.let { return it }
                }

                if (initialized == false) {
                    throw IllegalStateException("$className is present but its API is not initialized")
                }
                throw IllegalStateException("$className did not expose a usable getAPI entrypoint")
            } catch (error: Throwable) {
                lastFailure = rootCause(error)
            }
        }
        resolvedProviderClass = ""
        throw IllegalStateException(
            "BEconomy provider API unavailable; tried ${PROVIDER_CLASSES.joinToString()}",
            lastFailure
        )
    }

    private fun invokeProvider(
        entry: Class<*>,
        receiver: Any?,
        name: String,
        types: Array<Class<*>>,
        vararg values: Any
    ): Any? {
        val method = entry.getMethod(name, *types)
        return method.invoke(if (Modifier.isStatic(method.modifiers)) null else receiver, *values)
    }

    companion object {
        const val BEAST = "BeastCoin"
        const val HUNTER = "HunterCoin"

        internal val PROVIDER_CLASSES = listOf(
            "org.blanketeconomy.api.BlanketEconomy",
            "org.krripe.beconomy.api.BEconomy"
        )

        private val MONEY_TYPES =
            arrayOf<Class<*>>(UUID::class.java, BigDecimal::class.java, String::class.java)

        private fun rootCause(error: Throwable): Throwable {
            var current = error
            while (current is InvocationTargetException && current.targetException != null) {
                current = current.targetException
            }
            return current
        }
    }
}
