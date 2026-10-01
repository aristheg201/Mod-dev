from pathlib import Path
import json, re, struct

root=Path(__file__).resolve().parent
log=(root/'qa-runtime/logs/latest.log').read_text(encoding='utf-8',errors='replace')
workflow=(root/'.github/workflows/cardworlds-ci.yml').read_text(encoding='utf-8')
expected=re.findall(r'^            (\d\d-[a-z0-9-]+)$',workflow,re.M)
assert len(expected)==38
for name in expected:
    path=root/'qa-runtime/screenshots'/f'{name}.png'
    data=path.read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n'
    width,height=struct.unpack('>II',data[16:24])
    assert width>=1280 and height>=720 and len(data)>=20000,(name,width,height,len(data))
    assert f'Captured {name}' in log,name
assert len(list((root/'qa-runtime/screenshots').glob('[0-9][0-9]-*.png')))==38
assert not list((root/'qa-runtime/screenshots').glob('failure-*.png'))
markers=[
 'CARDWORLDS_QA_COMPLETE','CARDWORLDS_QA_DUEL_REALM_CONFIRMED',
 'CARDWORLDS_QA_SPECTATOR_CONFIRMED','CARDWORLDS_QA_FIELD_PILES_CONFIRMED occupied=8 hidden=4 public=4',
 'CARDWORLDS_QA_SPELLTRAP_CHAIN_CONFIRMED','CARDWORLDS_QA_SPELLTRAP_RESOLVE_CONFIRMED',
 'CARDWORLDS_QA_CREATION_CONFIRMED','CARDWORLDS_QA_MONSTER_POSITIONS_CONFIRMED',
 'CARDWORLDS_QA_CATALOG_CONFIRMED','CARDWORLDS_PLACEHOLDER_PROOF','CARDWORLDS_CARD_IDENTITIES',
 'CARDWORLDS_ARCEUS_RENDERED',
 'CARDWORLDS_ARCEUS_PROVIDER megaShowdownLoaded=true model=assets/cobblemon/bedrock/pokemon/models/0493_arceus/arceus.geo.json',
 'CARDWORLDS_FACEUP_DEFENSE_POKEMON_RENDERED',
 'CARDWORLDS_VFX_PROFILE semantic=ATTACK_PHYSICAL element=fire profile=fire',
 'CARDWORLDS_VFX_CAMERA distance=14.5','CARDWORLDS_NATIVE_PARTICLE registered=cobblemon:snowstorm',
 'CARDWORLDS_ANIMATION_FALLBACK_PROOF','CARDWORLDS_VIETNAMESE_PROOF language=vi_vn',
 'CARDWORLDS_VI_DYNAMIC_PROOF turn=Lượt 3 lp=SL 7500','CARDWORLDS_ENGLISH_FALLBACK_PROOF language=en_us',
]
missing=[m for m in markers if m not in log]
assert not missing,missing
for intent in ['SPAWN','ATTACK_PHYSICAL','ATTACK_SPECIAL','HIT','CAST_STATUS','TRANSFORM']:
    assert f'CARDWORLDS_NATIVE_POSE_PROOF intent={intent} ' in log,intent
assert not re.search(r'CARDWORLDS_NATIVE_POSE_PROOF intent=(ATTACK_PHYSICAL|ATTACK_SPECIAL|CAST_STATUS|HIT|HEAVY_HIT) animation=cry',log)
assert not re.search(r'CARDWORLDS_QA_FAILED|Minecraft has crashed!|ReportedException|Rendering screen|OpenGL error|Exception in thread "Render thread"',log,re.I)
assert log.count('CARDWORLDS_SPECIAL_EFFECT_CAPTURE')==4
assert re.search(r'CARDWORLDS_CARD_BACK_RENDERED.*position=FACE_DOWN_DEFENSE.*pokemonActorPresent=false',log)
exec((root/'tools/verify_visual_pixels.py').read_text().replace("Path('run/screenshots')","Path('qa-runtime/screenshots')"))
evidence={'mode':'local','screenshots':expected,'screenshotCount':38,'completeMarker':True,'visualPixelChecks':True,'nativeAnimationProofs':True,'twoClientVerified':False}
(root/'build/local-visual-evidence.json').write_text(json.dumps(evidence,indent=2))
print(json.dumps(evidence,indent=2))
