"""Check the final generated sources, and optionally remapped JARs, before runtime QA."""
from pathlib import Path
import json
import sys
import zipfile

root = Path.cwd()
main = root / "src/main/java/vn/svarcade/tcg"
initializer = "vn.svarcade.tcg.qa.FocusedEconomyEffectVisualRun"
focused = root / "src/focusedQa"
descriptor = json.loads((focused / "resources/fabric.mod.json").read_text())
assert descriptor["entrypoints"] == {"client": [initializer]}, descriptor
currency = (main / "client/component/CurrencyPurchaseUi.java").read_text()
assert "BEconomy" not in currency, "CurrencyPurchaseUi must be client safe"
header=(main / "client/CardWorldsScreen.java").read_text()
assert "CurrencyPurchaseUi.renderBalances(ui,state.currencyBalances())" in header
assert "state.profile().coins()" not in header and "Items.GOLD_NUGGET" not in header
assert "draw(u,coin(currency)" in currency and '"Beast Coin"' in currency and '"Hunter Coin"' in currency
assert (main / "economy/CardWorldsCurrency.java").is_file()
for path in (main / "client").rglob("*.java"):
    assert "BEconomyCardWorlds" not in path.read_text(), path
runner = (focused / "java/vn/svarcade/tcg/qa/FocusedEconomyEffectVisualRun.java").read_text()
assert "Boolean.getBoolean" not in runner and "System.getenv" not in runner
assert 'a.fields.put("search", "Charizard")' in runner
assert 'a.category = "pokemon"' in runner and 'a.favorites.add("charizard")' in runner
assert 'a.ownership = "Favorites"' in runner and 'visible.size() != 1' in runner
assert "BEconomyCardWorlds" not in runner and "Class.forName" not in runner
assert 'send("pull"' not in runner and "VisualRun.class" not in runner
assert runner.count('shot(c, "focused-') == 3
assert 'withBanners(' not in runner, "Pack verification must retain every production banner"
assert 'packIds.size() != 14' in runner and 'CARDWORLDS_FOCUSED_PACK_VERIFIED' in runner
assert 'packIndex * 112' in runner and 'packShot(c,' in runner
assert 'Unable to find a poser' in runner and 'purchases=0' in runner
models=(main / 'client/render/PokemonModels.java').read_text()
assert 'descriptor.aspects().isEmpty()' in models and 'actual.getStandardForm().getAspects()' in models
assert 'SpeciesFeatures.getFeaturesFor(actual)' in models and 'choice.getDefault()' in models
gradle = (root / "source-overrides/duel-realm/production-visual/qa/focused.gradle").read_text()
client_task = gradle.split("tasks.register('runFocusedCardWorldsClient'", 1)[1]
assert "remapFocusedQaJar" in client_task and "remapQaJar" not in client_task
assert "mods.setFrom(configurations.focusedClientRuntimeMods" in client_task
assert "cardworlds.qa" not in client_task
assert 'focusedServerRuntimeMods "maven.modrinth:4Kma4Oms:9rGoa3aP"' in gradle
assert 'focusedClientRuntimeMods "maven.modrinth:4Kma4Oms' not in gradle
startup = (main / "fabric/TcgMod.java").read_text()
assert 'if(server.isDedicated()&&Boolean.getBoolean("cardworlds.qa.focused"))' in startup
assert 'p.getServer().isDedicated()?vn.svarcade.tcg.integration.BEconomyCardWorlds.snapshotBalances(p)' in startup
assert 'card.effect().lifeCost()==0' in (main / "data/EffectContent.java").read_text()
assert '"LP_COST"' not in (main / "data/CardIdentities.java").read_text()
effect_qa = (main / "data/EffectEconomyQa.java").read_text()
assert "lpCosts(stage.effect(),kinds)" in effect_qa and "effect.lifeCost()>0" in effect_qa
if "--jars" in sys.argv:
    jars = list((root / "build/libs").glob("*-focused-qa-driver.jar"))
    assert len(jars) == 1, jars
    with zipfile.ZipFile(jars[0]) as jar:
        assert json.loads(jar.read("fabric.mod.json"))["entrypoints"] == {"client": [initializer]}
        assert initializer.replace(".", "/") + ".class" in jar.namelist()
        assert not any(name.endswith("/VisualRun.class") for name in jar.namelist())
    production = [p for p in (root / "build/libs").glob("SVArcade-TCG-*.jar")
                  if not any(part in p.name for part in ("sources", "dev", "qa-driver"))]
    assert len(production) == 1, production
    with zipfile.ZipFile(production[0]) as jar:
        assert "vn/svarcade/tcg/economy/CardWorldsCurrency.class" in jar.namelist()
        assert not any("/qa/" in name for name in jar.namelist())
        assert "beconomy" not in json.loads(jar.read("fabric.mod.json")).get("depends", {})
        for name in jar.namelist():
            if name.startswith("vn/svarcade/tcg/client/") and name.endswith(".class"):
                assert b"BEconomyCardWorlds" not in jar.read(name), name
print("CARDWORLDS_FOCUSED_PREFLIGHT_OK source=true jars=" + str("--jars" in sys.argv).lower())
