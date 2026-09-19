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
    counts = {'units.json': 43, 'teams.json': 1, 'traits.json': 23, 'components.json': 8, 'full_items.json': 36, 'augments.json': 9, 'pve.json': 7}
    for name, count in counts.items():
        if not isinstance(data[name], list) or len(data[name]) != count:
            raise ValueError(f'{name}: expected {count} reviewed definitions')
    units = {v['id'] for v in data['units.json']}
    traits = {v['id'] for v in data['traits.json']}
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
    print('TFT bundle verified:', ', '.join(f'{name}={count}' for name, count in counts.items()))
finally:
    if jar:
        jar.close()
