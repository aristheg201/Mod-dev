"""Preflight checks final generated sources/resources, before spending a CI runtime."""
from pathlib import Path
import json,sys,copy
sys.path.insert(0,str(Path(__file__).resolve().parent))
from importlib import import_module
signature=import_module('compile').signature
base=Path('src/main');j=json.loads((base/'resources/data/svarcade_tcg/pokemon_effects.json').read_text());definitions=j['definitions']
source=json.loads((Path(__file__).resolve().parent/'species_sources.json').read_text())['species']
assert len(source)==1025
seen={}
for key,d in definitions.items():
 fp=signature(d['effect']);assert fp not in seen,(key,seen.get(fp));seen[fp]=key
 if key not in ('mirror_barrier','counter_seal','training_ground'):
  assert d['effect']['lifeCost']==0,key
  assert not any(c['type']=='LP_COST' for c in d['effect']['spec']['costs']),key
 assert all(s['id']!='signature' for s in d['effect']['spec']['stages']),key
 if 'moves' in d:
  species=key.split('|')[0];data=source[species]
  for f in data.get('forms',[]):
   if '|'.join(sorted(f.get('aspects',[])))=='|'.join(key.split('|')[1:]):data=dict(data,**f);break
  learned={m.split(':',1)[-1] for m in data.get('moves',[])}
  assert set(d['moves'])<=learned,(key,d['moves'])
for species,data in source.items():
 assert species in definitions,species
 for f in data.get('forms',[]):
  if f.get('aspects'):assert species+'|'+'|'.join(sorted(f['aspects'])) in definitions,(species,f['aspects'])
s=(base/'java/vn/svarcade/tcg/data/CardIdentities.java').read_text()
assert 'CardGameplayEffects.identity(card,presentation)' in s and 'RECIPES.setup' not in s and 'RECIPES.payoff' not in s
assert 'duplicate primary gameplay effect' in s
s=(base/'java/vn/svarcade/tcg/duel/Duel.java').read_text();assert 'event.sourceTurn()<0?turn:event.sourceTurn()' in s
assert set(json.loads((base/'resources/data/svarcade_tcg/identity_recipes.json').read_text()))=={'shapes'}
for lang in ('en_us','vi_vn'):
 data=json.loads((base/f'resources/assets/svarcade_tcg/lang/{lang}.json').read_text());assert 'HunterCoin' not in data['cardworlds.world.practice_rewards'];assert 'generated' not in data['cardworlds.ui.deck_catalog_summary']
 assert 'Beast Coin' in data['cardworlds.market.beast_price']
market=(base/'java/vn/svarcade/tcg/client/screens/MarketScreen.java').read_text()
assert 'HunterCoin' not in market and 'profile().coins()' not in market and 'BEconomy' not in market
assert 'CurrencyPurchaseUi.coin(CardWorldsCurrency.BEAST)' in market
server=(base/'java/vn/svarcade/tcg/fabric/TcgMod.java').read_text()
assert 'store.listEconomy(owner' in server and 'MarketCheckout.buy(store' in server
assert 'Market payments require the dedicated server economy.' in server
print('CARDWORLDS_GAMEPLAY_PREFLIGHT_OK definitions='+str(len(definitions))+' species=1025 duplicatePrimary=0 ordinalGameplay=0')
