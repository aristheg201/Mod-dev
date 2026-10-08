#!/usr/bin/env python3
"""Data regression checks. These supplement, and never replace, Minecraft runtime QA."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
data = root / 'src/main/resources/data/cobblemonworld'
locales = {name: json.loads((root / f'src/main/resources/assets/cobblemonworld/lang/{name}.json').read_text()) for name in ('en_us', 'vi_vn')}
def need_key(key):
    for locale, values in locales.items():
        assert key in values, f'{locale}: missing {key}'
for path in (data / 'shops').glob('*.json'):
    shop = json.loads(path.read_text())
    categories = set(shop['categories'])
    need_key(shop['titleKey']); need_key(shop['descriptionKey'])
    for category in categories: need_key('shop.cobblemonworld.category.' + category)
    for entry in shop.get('entries', []) + shop.get('rules', []):
        assert 1 <= entry['price'] <= 500, (path.name, entry)
        assert set(entry['categories']) <= categories, (path.name, entry)
    if shop['id'] == 'fashion_elle':
        assert {'cobblemonarmory', 'cobblemonarmors'} <= set(shop['registryNamespaces'])
        assert not shop['entries'], 'Fashion must enumerate the item registry'
npcs = {p.stem: json.loads(p.read_text()) for p in (data / 'npcs').glob('*.json')}
assert npcs['mysterious']['specialActor']
assert 'town8_qualifier_defeated' in npcs['battle_tower_receptionist']['requiredFlags']
assert 'divinos_eight_towns_complete' in npcs['battle_tower_receptionist']['requiredFlags']
assert npcs['captain_dorian']['team'] and npcs['captain_dorian']['defeatFlag'] == 'town8_qualifier_defeated'
assert 'town8_qualifier_ready' in npcs['captain_dorian']['requiredFlags']
assert 'school_wolf_trainer_03_defeated' in npcs['school_wolf_master']['requiredFlags']
for path in (data / 'quests').glob('*.json'):
    quest=json.loads(path.read_text());need_key(quest['title']);need_key(quest['description'])
for route in json.loads((data / 'progression/routes.json').read_text()):
    assert route['npc'] in npcs and route['npc'] != 'resonance_heart'
    need_key(route['objectiveKey'])
for locale, values in locales.items():
    for key, value in values.items():
        assert '/cworld' not in value and 'first_anomaly_site' not in value, (locale, key)
print('Static data regression checks passed; runtime features remain separately assessed.')
