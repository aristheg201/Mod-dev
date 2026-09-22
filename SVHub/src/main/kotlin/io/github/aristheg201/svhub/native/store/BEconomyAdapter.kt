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
 * economy mod can still load SVHub. BEconomy 1.5 exposes
 * org.krripe.beconomy.api.BEconomy; the older BlanketEconomy package remains as a
 * compatibility candidate for servers that still ship that API surface.
 */
class BEconomyAdapter(private val apiSupplier: (() -> Any)? = null) : CosmeticEconomy {
    @Volatile private var cachedApi: Any? = null
    @Volatile private var resolvedProviderClass: String = if (apiSupplier != null) "injected" else ""

    override fun currencyExists(currency: String) = canonicalCurrency(currency) != null

    override fun balance(player: UUID, currency: String): BigDecimal {
        val canonical = requireCurrency(currency)
        return invoke("getBalance", arrayOf(UUID::class.java, String::class.java), player, canonical) as BigDecimal
    }

    override fun debit(player: UUID, amount: BigDecimal, currency: String): Boolean {
        require(amount.signum() > 0)
        val canonical = requireCurrency(currency)
        return invoke("subtractBalance", MONEY_TYPES, player, amount, canonical) as Boolean
    }

    override fun credit(player: UUID, amount: BigDecimal, currency: String) {
        require(amount.signum() > 0)
        val canonical = requireCurrency(currency)
        invoke("addBalance", MONEY_TYPES, player, amount, canonical)
    }

    override fun recordReceipt(player: UUID, identity: String, amount: BigDecimal, currency: String) {
        val canonical = requireCurrency(currency)
        invoke(
            "logTransaction",
            arrayOf(UUID::class.java, String::class.java, BigDecimal::class.java, String::class.java),
            player, canonical, amount, "svhub:$identity"
        )
    }

    override fun receipt(player: UUID, identity: String, amount: BigDecimal, currency: String): Boolean {
        val canonical = requireCurrency(currency)
        val history = invoke("getTransactionHistory", arrayOf(UUID::class.java), player) as List<*>
        return history.filterNotNull().any { transaction ->
            fun field(name: String) = transaction.javaClass.getMethod(name).invoke(transaction)
            field("getPlayerId") == player &&
                field("getType") == "svhub:$identity" &&
                field("getCurrencyType") == canonical &&
                (field("getAmount") as BigDecimal).compareTo(amount) == 0
        }
    }

    private fun requireCurrency(currency: String): String =
        canonicalCurrency(currency) ?: throw IllegalStateException(
            "BEconomy currency '$currency' is unavailable; configured currencies: " +
                availableCurrencyTypes().joinToString().ifBlank { "<none>" }
        )

    /**
     * BEconomy 1.5 treats currencyType as case-sensitive. Server configs commonly use
     * lower-case or separator variants (beastcoin, beast_coin, hunter-coin), while SVHub
     * exposes stable UI names BeastCoin/HunterCoin. Resolve those aliases to the provider's
     * canonical currencyType before every balance mutation/receipt operation.
     */
    private fun canonicalCurrency(currency: String): String? {
        val provider = target()
        val exact = runCatching {
            provider.javaClass.getMethod("currencyExists", String::class.java)
                .invoke(provider, currency) as? Boolean
        }.getOrNull()
        if (exact == true) return currency

        val configured = availableCurrencyTypes(provider)
        configured.firstOrNull { it.equals(currency, ignoreCase = true) }?.let { return it }

        val wanted = normalizeCurrency(currency)
        val normalizedMatches = configured.filter { normalizeCurrency(it) == wanted }
        return when (normalizedMatches.size) {
            0 -> null
            1 -> normalizedMatches.single()
            else -> throw IllegalStateException(
                "Ambiguous BEconomy currency '$currency': ${normalizedMatches.joinToString()}"
            )
        }
    }

    private fun availableCurrencyTypes(provider: Any = target()): List<String> {
        val currencies = runCatching {
            provider.javaClass.getMethod("getCurrencyList").invoke(provider) as? Iterable<*>
        }.getOrNull() ?: return emptyList()
        return currencies.filterNotNull().mapNotNull { config ->
            runCatching {
                config.javaClass.getMethod("getCurrencyType").invoke(config) as? String
            }.getOrNull()?.takeIf(String::isNotBlank)
        }.distinct()
    }

    private fun normalizeCurrency(value: String): String =
        value.filter(Char::isLetterOrDigit).lowercase()

    /**
     * Resolves the real provider and verifies the two currencies SVHub actually consumes.
     * Failures are intentionally not cached: BEconomy may finish initializing after SVHub.
     */
    fun status(): BEconomyStatus = try {
        target()
        val beastId = canonicalCurrency(BEAST)
        val hunterId = canonicalCurrency(HUNTER)
        val beast = beastId != null
        val hunter = hunterId != null
        BEconomyStatus(
            available = true,
            providerClass = resolvedProviderClass,
            beastCoin = beast,
            hunterCoin = hunter,
            detail = when {
                beast && hunter -> "ready ($BEAST=$beastId, $HUNTER=$hunterId)"
                !beast && !hunter -> "missing BeastCoin and HunterCoin; configured=${availableCurrencyTypes().joinToString()}"
                !beast -> "missing BeastCoin; configured=${availableCurrencyTypes().joinToString()}"
                else -> "missing HunterCoin; configured=${availableCurrencyTypes().joinToString()}"
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
