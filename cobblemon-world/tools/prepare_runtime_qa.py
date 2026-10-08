#!/usr/bin/env python3
"""Prepare isolated localhost QA directories without touching an existing world."""
import hashlib
import json
import shutil
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
mods = root / 'qa-runtime/mods'
mods.mkdir(parents=True, exist_ok=True)
for entry in json.loads((root / 'docs/RUNTIME_DEPENDENCIES.json').read_text()):
    target = mods / entry['filename']
    if not target.exists():
        if 'url' in entry:
            request = urllib.request.Request(entry['url'], headers={'User-Agent': 'CobblemonWorld-Runtime-QA/1.0'})
            with urllib.request.urlopen(request) as response:
                data = response.read()
        else:
            data = (root / 'libs/BEconomy-1.5.jar').read_bytes()
        if hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise RuntimeError('Downloaded dependency hash mismatch: ' + target.name)
        target.write_bytes(data)
    if hashlib.sha256(target.read_bytes()).hexdigest() != entry['sha256']:
        raise RuntimeError('Existing QA dependency hash mismatch: ' + target.name)
    print('Verified', target.name)

dependency_override = {'version': 1, 'overrides': {'cobblemonarmors': {'+depends': {'cobblemon': '1.8.1+1.21.1'}}}}
for name in ('server', 'server-services', 'server-narrative', 'server-narrative-opening', 'server-no-economy', 'client'):
    directory = root / 'qa-runtime' / name
    directory.mkdir(parents=True, exist_ok=True)
    config = directory / 'config'
    config.mkdir(exist_ok=True)
    target = config / 'fabric_loader_dependencies.json'
    if not target.exists():
        target.write_text(json.dumps(dependency_override, indent=2) + '\n')
    if name == 'client':
        continue
    files = {
        'eula.txt': 'eula=true\n',
        'ops.json': '[]\n',
        'server.properties': '\n'.join([
            'server-ip=127.0.0.1', 'server-port=25571', 'online-mode=false',
            'gamemode=survival', 'difficulty=peaceful', 'spawn-protection=0',
            'view-distance=6', 'simulation-distance=4', 'max-players=3',
            ('level-name=CWorldNarrativeQA' if directory.name == 'server-narrative' else 'level-name=CWorldQA'), 'level-seed=20261008', 'enable-command-block=false', ''
        ])
    }
    for name, content in files.items():
        target = directory / name
        if not target.exists():
            target.write_text(content)
print('Isolated QA files ready. Build once, then launch server/client with -x remapJar.')
