#!/usr/bin/env python3
"""Verify canonical bundled TFT resources, either in source or in the final JAR."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

project = Path(__file__).resolve().parents[1]
prefix = 'data/svhub/tft/sets/kanto_rising/'
expected = json.loads((project / 'tools/tft-bundled-sha256.json').read_text())
jar = zipfile.ZipFile(sys.argv[1]) if len(sys.argv) > 1 else None
try:
    def read(name):
        return jar.read(prefix + name) if jar else (project / 'src/main/resources' / prefix / name).read_bytes()

    data = {}
    for name, digest in expected.items():
        raw = read(name)
        if hashlib.sha256(raw).hexdigest() != digest:
            raise ValueError(f'{name}: content hash differs from the reviewed bundled set')
        data[name] = json.loads(raw)
    if not isinstance(data['set.json'], dict):
        raise ValueError('set.json must contain the manifest object, not component definitions')
    manifest = data['set.json']
    required_round_types = {'pvp', 'pve', 'augment', 'carousel', 'boss'}
    actual_round_types = {round_['type'] for round_ in manifest.get('roundSchedule', [])}
    if not required_round_types <= actual_round_types:
        raise ValueError(f'Missing scheduled round types: {sorted(required_round_types - actual_round_types)}')
    carousel = manifest.get('carousel', {})
    if carousel.get('offerCount', 0) < 2 or carousel.get('pickupRadius', 0) <= 0 or carousel.get('releaseWaveSize', 0) < 1:
        raise ValueError('set.json: invalid physical carousel definition')
    arenas = set(manifest.get('rules', {}).get('arenas', []))
    for arena in arenas:
        path = project / 'src/main/resources/assets/svhub/arenas' / f'{arena}.json'
        if not path.is_file() or not isinstance(json.loads(path.read_text()), dict):
            raise ValueError(f'Missing or invalid arena {arena}')
    counts = {'units.json': 84, 'teams.json': 6, 'traits.json': 34, 'components.json': 8, 'full_items.json': 36, 'augments.json': 32, 'pve.json': 7, 'loot.json': 2}
    for name, count in counts.items():
        if not isinstance(data[name], list) or len(data[name]) != count:
            raise ValueError(f'{name}: expected {count} reviewed definitions')
    units = {v['id'] for v in data['units.json']}
    traits = {v['id'] for v in data['traits.json']}
    pokemon_types = {'normal','fire','water','electric','grass','ice','fighting','poison','ground','flying','psychic','bug','rock','ghost','dragon','dark','steel','fairy'}
    if not pokemon_types <= traits:
        raise ValueError(f'Missing Pokemon type traits: {sorted(pokemon_types - traits)}')
    used_traits = {trait for unit in data['units.json'] for trait in unit.get('traits', [])}
    if traits - used_traits:
        raise ValueError(f'Unused TFT traits: {sorted(traits - used_traits)}')
    role_traits = {'guardian':'guardian','caster':'caster','striker':'striker','ranger':'ranger','support':'support','fighter':'bruiser'}
    for unit in data['units.json']:
        expected = role_traits.get(unit.get('role', ''))
        if expected and expected not in unit.get('traits', []):
            raise ValueError(f"Unit {unit['id']} is missing class trait {expected}")
    tiers = {'Silver': 0, 'Gold': 0, 'Prismatic': 0}
    for augment in data['augments.json']:
        tier = augment.get('tier', 'Gold')
        if tier not in tiers:
            raise ValueError(f"Invalid augment tier {tier} for {augment['id']}")
        tiers[tier] += 1
        if not augment.get('tags'):
            raise ValueError(f"Augment {augment['id']} has no bot/content tags")
        for trait in augment.get('traitEffects', {}):
            if trait not in traits:
                raise ValueError(f"Augment {augment['id']} references unknown trait {trait}")
    if tiers != {'Silver': 10, 'Gold': 14, 'Prismatic': 8}:
        raise ValueError(f'Unexpected augment tier distribution: {tiers}')
    for round_ in manifest.get('roundSchedule', []):
        if round_.get('type') == 'augment':
            tier = round_.get('augmentTier')
            if not tier or tiers.get(tier, 0) < 3:
                raise ValueError(f"Augment round {round_.get('label')} has invalid tier pool {tier}")
    components = {v['id'] for v in data['components.json']}
    for unit in data['units.json']:
        if not set(unit['traits']) <= traits:
            raise ValueError(f"Unknown trait in unit {unit['id']}")
    for item in data['full_items.json']:
        if len(item['components']) != 2 or not set(item['components']) <= components:
            raise ValueError(f"Invalid recipe {item['id']}")
    for round_ in data['pve.json']:
        if not all(e['unit'] in units for e in round_['enemies']):
            raise ValueError(f"Unknown unit in PvE round {round_['round']}")
    for team in data['teams.json']:
        members = team['members'] + team.get('bench', [])
        if not all(member['unit'] in units for member in members):
            raise ValueError(f"Unknown unit in team {team['id']}")
        arena = team.get('arena', '').removeprefix('svhub:')
        if arena and arena not in arenas:
            raise ValueError(f"Unknown arena in team {team['id']}: {arena}")
    required_teams = {'svhub:monsterverse', 'svhub:dc_universe', 'svhub:green_lantern_corps', 'svhub:than_tai', 'svhub:one_piece'}
    team_ids = {team['id'] for team in data['teams.json']}
    if not required_teams <= team_ids:
        raise ValueError(f'Missing production teams: {sorted(required_teams - team_ids)}')
    by_id = {unit['id']: unit for unit in data['units.json']}
    expected_aspects = {
        'mv_godzilla': {'cosmetic_item-godzilla'}, 'mv_ghidorah': {'cosmetic_item-kingghidora'},
        'mv_kong': {'cosmetic_item-kingkong'}, 'mv_mothra': {'cosmetic_item-mothra'}, 'mv_rodan': {'cosmetic_item-rodan'},
        'gl_mewtwo': {'greenlantern'}, 'gl_mewtwo_x': {'greenlantern'}, 'gl_mewtwo_y': {'greenlantern'},
        **{name: {'op'} for name in ('op_luffy','op_zoro','op_nami','op_sanji','op_robin','op_usopp','op_franky','op_brook','op_jinbe','op_chopper','op_sunny')}
    }
    for unit_id, aspects in expected_aspects.items():
        actual = set(by_id[unit_id]['pokemon'].get('aspects', []))
        if actual != aspects:
            raise ValueError(f'{unit_id}: expected exact aspects {sorted(aspects)}, got {sorted(actual)}')
    if by_id['gl_mewtwo_x']['pokemon'].get('form') != 'mega-x' or by_id['gl_mewtwo_y']['pokemon'].get('form') != 'mega-y':
        raise ValueError('Green Lantern Mega provider forms are not canonical')
    print('TFT bundle verified:', ', '.join(f'{name}={count}' for name, count in counts.items()))
finally:
    if jar:
        jar.close()
