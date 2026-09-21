package io.github.aristheg201.svhub.native.store

import org.blanketeconomy.api.BlanketEconomy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class BEconomyAdapterResolverTest {
    private val player = UUID.randomUUID()

    @BeforeEach
    fun resetProvider() {
        BlanketEconomy.reset()
    }

    @Test
    fun `default adapter resolves live BlanketEconomy package and both SVHub currencies`() {
        val adapter = BEconomyAdapter()
        val status = adapter.status()

        assertTrue(status.ready)
        assertEquals("org.blanketeconomy.api.BlanketEconomy", status.providerClass)
        assertTrue(status.beastCoin)
        assertTrue(status.hunterCoin)
        assertEquals(BigDecimal("2000"), adapter.balance(player, BEconomyAdapter.BEAST))
        assertEquals(BigDecimal("1500"), adapter.balance(player, BEconomyAdapter.HUNTER))
    }

    @Test
    fun `default bridge performs BeastCoin and HunterCoin mutations through provider API`() {
        val adapter = BEconomyAdapter()

        assertTrue(adapter.debit(player, BigDecimal("250"), BEconomyAdapter.BEAST))
        adapter.credit(player, BigDecimal("25.50"), BEconomyAdapter.HUNTER)

        assertEquals(BigDecimal("1750"), adapter.balance(player, BEconomyAdapter.BEAST))
        assertEquals(BigDecimal("1525.50"), adapter.balance(player, BEconomyAdapter.HUNTER))
    }

    @Test
    fun `custom recovery receipt is read from actual provider transaction shape`() {
        val adapter = BEconomyAdapter()
        val identity = "purchase:$player:arena:dragon_shrine"
        val amount = BigDecimal("-1000")

        adapter.recordReceipt(player, identity, amount, BEconomyAdapter.BEAST)

        assertTrue(adapter.receipt(player, identity, amount, BEconomyAdapter.BEAST))
        assertFalse(adapter.receipt(player, identity + ":other", amount, BEconomyAdapter.BEAST))
    }

    @Test
    fun `required currency health reports missing currency instead of silently falling back`() {
        BlanketEconomy.api.balances.remove(BEconomyAdapter.HUNTER)
        val status = BEconomyAdapter().status()

        assertTrue(status.available)
        assertFalse(status.ready)
        assertTrue(status.beastCoin)
        assertFalse(status.hunterCoin)
        assertEquals("missing HunterCoin", status.detail)
    }
}
