package io.github.aristheg201.svhub.native.store
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*
class ConfiguredEconomyTest {
    @TempDir lateinit var root: Path
    class Currency(private val id: String) { fun getCurrencyType()=id }
    class Provider {
        val balances=linkedMapOf("SeasonCredits" to BigDecimal("150"),"Gems2026" to BigDecimal("33"))
        fun getCurrencyList()=balances.keys.map(::Currency)
        fun currencyExists(id:String)=id in balances
        fun getBalance(player:UUID,id:String)=balances.getValue(id)
        fun subtractBalance(player:UUID,value:BigDecimal,id:String):Boolean { if(balances.getValue(id)<value)return false;balances[id]=balances.getValue(id)-value;return true }
    }
    @Test fun `wallet and purchases use renamed provider ids without recompilation`() {
        val path=root.resolve("economy.json")
        val provider=Provider();val adapter=BEconomyAdapter { provider };val player=UUID.randomUUID()
        fun configure(id:String) { Files.writeString(path,"""{"defaults":{"reward":"coins"},"wallet":["@beconomy:*"],"currencies":{"coins":{"provider":"beconomy","currency":"$id"}}}""");EconomyConfig.start(path) }
        configure("SeasonCredits")
        assertTrue(adapter.status().ready)
        assertEquals(listOf("SeasonCredits","Gems2026"),EconomyConfig.wallet(adapter.availableCurrencyTypes()))
        assertTrue(adapter.debit(player,BigDecimal("12.5"),"coins"))
        assertEquals(BigDecimal("137.5"),adapter.balance(player,"coins"))
        configure("Gems2026")
        assertEquals(BigDecimal("33"),adapter.balance(player,"coins"))
        assertTrue(adapter.debit(player,BigDecimal("3"),"coins"))
        assertEquals(BigDecimal("30"),provider.balances["Gems2026"])
    }
}
