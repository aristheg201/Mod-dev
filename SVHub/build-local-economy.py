from pathlib import Path
import re,json,zipfile
root=Path('src'); main=root/'main/kotlin/io/github/aristheg201/svhub'; client=root/'client/kotlin/io/github/aristheg201/svhub/client/nativeui'
def edit(p,f):
 s=p.read_text(encoding='utf-8');p.write_text(f(s),encoding='utf-8')
p=main/'native/store/BEconomyAdapter.kt'
s=p.read_text();a=s.index('data class BEconomyStatus(');b=s.index('/**',a)
s=s[:a]+'''data class BEconomyStatus(val available: Boolean, val providerClass: String = "", val detail: String = "") {
    val ready: Boolean get() = available
}

'''+s[b:]
a=s.index('    /**\n     * BEconomy 1.5');b=s.index('    private fun canonicalCurrency',a)
s=s[:a]+s[b:];s=s.replace('val provider = target()\n        val exact', 'val provider = target()\n        val currency = EconomyConfig.resolve(currency, availableCurrencyTypes(provider)) ?: return null\n        val exact')
s=s.replace('private fun availableCurrencyTypes','fun availableCurrencyTypes')
a=s.index('    /**\n     * Resolves the real provider');b=s.index('    private fun invoke(',a)
s=s[:a]+'''    fun status(): BEconomyStatus = try {
        target()
        BEconomyStatus(true, resolvedProviderClass, availableCurrencyTypes().joinToString())
    } catch (error: Throwable) {
        BEconomyStatus(false, resolvedProviderClass, rootCause(error).message.orEmpty())
    }

'''+s[b:]
s=s.replace('        const val BEAST = "BeastCoin"\n        const val HUNTER = "HunterCoin"\n','')
p.write_text(s)
(main/'native/store/EconomyConfig.kt').write_text('''package io.github.aristheg201.svhub.native.store

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.aristheg201.svhub.util.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path

/** Currency identifiers and aliases are server data, never compiled assumptions. */
object EconomyConfig {
    private var config = JsonObject()
    fun start(path: Path) {
        if (!Files.exists(path)) AtomicFiles.writeUtf8(path, requireNotNull(javaClass.getResourceAsStream("/data/svhub/economy.json")).bufferedReader().use { it.readText() })
        config = JsonParser.parseString(Files.readString(path)).asJsonObject
    }
    fun defaultCurrency(kind: String): String = config.getAsJsonObject("defaults")?.let { defaults ->
        if (kind in setOf("ARENA", "TACTICIAN")) defaults.getAsJsonObject("store")?.get(kind)?.asString
        else defaults.get(kind)?.asString
    } ?: ""
    fun resolve(key: String, available: List<String>): String? {
        val definition = config.getAsJsonObject("currencies")?.getAsJsonObject(key)
        val id = definition?.get("currency")?.asString ?: key
        if (id.startsWith("@beconomy:")) return id.substringAfter(':').toIntOrNull()?.let(available::getOrNull)
        return id.takeIf { it.isNotBlank() }
    }
    fun wallet(available: List<String>): List<String> {
        val keys = config.getAsJsonArray("wallet")?.map { it.asString } ?: available
        return keys.flatMap { if (it == "@beconomy:*") available else listOfNotNull(resolve(it, available)) }.distinct()
    }
}
''')
p=main/'native/store/CosmeticPurchases.kt'
edit(p,lambda s:s.replace('if (kind == CosmeticKind.ARENA) BEconomyAdapter.BEAST else BEconomyAdapter.HUNTER','EconomyConfig.defaultCurrency(kind.name)').replace('require(currency in setOf(BEconomyAdapter.BEAST, BEconomyAdapter.HUNTER))','require(currency.isNotBlank() || price.signum() == 0)').replace('BEconomyAdapter.BEAST','EconomyConfig.defaultCurrency("reward")'))
p=main/'native/NativeCosmeticService.kt'
edit(p,lambda s:s.replace('fun start(path: Path) {','fun start(path: Path) {\n        EconomyConfig.start(path.resolveSibling("economy.json"))').replace('if (kind == CosmeticKind.ARENA) BEconomyAdapter.BEAST else BEconomyAdapter.HUNTER','EconomyConfig.defaultCurrency(kind.name)').replace('"BEconomy integration ready via {} (BeastCoin={}, HunterCoin={})",\n                status.providerClass, status.beastCoin, status.hunterCoin','"Economy integration ready via {} ({})",\n                status.providerClass, status.detail').replace('paid cosmetics and BeastCoin rewards','paid cosmetics and configured rewards').replace('if (currency == BEconomyAdapter.HUNTER) "not_enough_hunter" else "not_enough_beast"','"insufficient"').replace('listOf(BEconomyAdapter.BEAST, BEconomyAdapter.HUNTER).forEach','EconomyConfig.wallet(runCatching { economy.availableCurrencyTypes() }.getOrDefault(emptyList())).forEach'))
p=client/'CosmeticStoreRenderer.kt';s=p.read_text();a=s.index('        val beast=');b=s.index('        val walletText=',a)
s=s[:a]+'''        val economyReady=state.bool("economyReady")
        val wallet=balances.entrySet().joinToString("   ") { (currency, amount) -> "$currency  ${amount.asString}" }
'''+s[b:];s=s.replace('addProperty("module","dashboard")','addProperty("module","arcade")');p.write_text(s)
p=client/'NativePlatformScreen.kt';s=p.read_text();s=re.sub(r'Triple\("wallet", tr\("gui.svhub.nav.wallet"\), .*?\)\n', 'Triple("wallet", tr("gui.svhub.nav.wallet"), tr("gui.svhub.nav.wallet"))\n',s)
a=s.index('        listOf("BeastCoin", "HunterCoin").forEachIndexed');b=s.index('\n    private fun renderGame',a)
s=s[:a]+'''        val entries = wallet?.entrySet()?.toList().orEmpty()
        moduleContentHeight = maxOf(area.height, entries.size * 44 + 20)
        entries.forEachIndexed { index, (currency, value) ->
            val y=area.y+index*44-moduleScroll
            gui.fill(area.x,y,area.right,y+36,panelAlt)
            gui.drawString(font,fit(currency,area.width-24),area.x+12,y+6,muted,false)
            gui.drawString(font,fit(value.asString,area.width-24),area.x+12,y+21,gold,true)
        }
    }
''' +s[b:];p.write_text(s)
# Keep reference artwork only; do not import screenshot shells.
z=zipfile.ZipFile(r'C:\Users\Admin\Downloads\New folder\SVHub-fabric-1.21.1-0.4.5-ARCADE-FULLSCREEN-UIFIX2.jar')
for n in z.namelist():
 if '/arcade/hero_' in n and n.endswith('.png'):
  p=root/'main/resources'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
cfg=json.loads(z.read('data/svhub/economy.json'));cfg['wallet']=['@beconomy:*'];cfg['currencies']={}
(root/'main/resources/data/svhub/economy.json').write_text(json.dumps(cfg,indent=2))
for p in (root/'main/resources/assets/svhub/lang').glob('*.json'):
 d=json.loads(p.read_text(encoding='utf-8'));d['gui.svhub.store.insufficient']='Insufficient balance for this offer.';p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
# Test fixtures specify arbitrary provider IDs; runtime defines no currency constants.
for p in (root/'test').rglob('*.kt'):
 edit(p,lambda s:s.replace('BEconomyAdapter.BEAST','"BeastCoin"').replace('BEconomyAdapter.HUNTER','"HunterCoin"'))
