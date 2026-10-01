"""Verify the remapped deliverable and write machine-readable evidence."""
import hashlib, json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
props={}
for line in (root/'gradle.properties').read_text().splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        k,v=line.split('=',1);props[k.strip()]=v.strip()
version=props['mod_version']
expected=root/'build/libs'/f"SVArcade-TCG-{version}.jar"
jar=expected if expected.exists() else next((p for p in (root/'build/libs').glob('SVArcade-TCG-*.jar') if 'sources' not in p.name and 'dev' not in p.name and 'qa-driver' not in p.name),None)
assert jar and jar.exists(), 'production remapped jar missing'
with ZipFile(jar) as archive:
    names=set(archive.namelist())
    metadata=json.loads(archive.read('fabric.mod.json'))
    catalog=json.loads(archive.read('data/svarcade_tcg/catalog.json'))
    assert metadata['id']=='svarcade_tcg'
    assert metadata['version']==version,(metadata['version'],version)
    for entry in ['vn/svarcade/tcg/duel/Duel.class','vn/svarcade/tcg/economy/CardStore.class',
                  'vn/svarcade/tcg/fabric/TcgMod.class','vn/svarcade/tcg/fabric/TcgClient.class',
                  'vn/svarcade/tcg/client/render/PokemonModels.class','vn/svarcade/tcg/client/animation/PackReveal.class',
                  'INTERNAL-BUILD.md']:
        assert entry in names,entry
    nested=[item['file'] for item in metadata.get('jars',[])]
    assert any('sqlite' in path for path in nested),'SQLite driver must be packaged'
    assert all(path in names for path in nested)
    assert not any('/cobblebr/' in name or '/tft/' in name for name in names)

tests=failures=errors=0
for path in (root/'build/test-results/test').glob('TEST-*.xml'):
    suite=ET.parse(path).getroot();tests+=int(suite.attrib['tests']);failures+=int(suite.attrib['failures']);errors+=int(suite.attrib['errors'])
assert tests>=31,(tests,'expected at least Codex baseline coverage')
assert failures==0 and errors==0,(failures,errors)

evidence=dict(artifact=str(jar.relative_to(root)),sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),bytes=jar.stat().st_size,
              cards=len(catalog['cards']),tests=tests,failures=failures,errors=errors,packagedDependencies=nested,
              clientRuntimeVerified=False,cobblemonRuntimeVerified=False,twoClientVerified=False,productionReady=False,
              note='Automated build evidence only. Release still requires the visual and two-client gates in docs/QA-GATES.md.')
(root/'build/internal-evidence.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8')
print(json.dumps(evidence,indent=2))
