#!/usr/bin/env python3
"""Validate content references, not runtime gameplay. Fails rather than hiding bad content."""
import json,pathlib,collections
root=pathlib.Path(__file__).resolve().parents[1]/'src/main/resources'
w=json.loads((root/'data/cobblemonworld/narrative/world.json').read_text())
langs={l:json.loads((root/f'assets/cobblemonworld/lang/{l}.json').read_text()) for l in ('vi_vn','en_us')}
actors={a['id']:a for a in w['actors']};pois={p['id']:p for p in w['pois']};scenes={s['id']:s for s in w['scenes']};teams={t['id']:t for t in w['teams']}
for label,rows in [('actor',w['actors']),('point',w['pois']),('scene',w['scenes']),('team',w['teams']),('chain',w['chains']),('season',w['seasons'])]:
 assert len({r['id'] for r in rows})==len(rows),('Duplicate identity',label)
allstages=w['campaign']+[s for c in w['chains'] for s in c['stages']]
known_flags={f for s in allstages for f in s.get('flags',[])}
known_flags.update(t['id']+'_defeated' for t in w['teams'])
for npc_file in (root/'data/cobblemonworld/npcs').glob('*.json'):
 npc=json.loads(npc_file.read_text())
 known_flags.update(npc.get('flagsOnDefeat',[])+npc.get('flagsOnInteract',[]))
 known_flags.update(v for v in (npc.get('defeatFlag'),npc.get('interactionFlag')) if v)
stage_ids={s['id'] for s in allstages}
for c in w['chains']:
 assert c['giver'] in actors,('Missing giver',c['id'])
 assert not c['prerequisite'] or c['prerequisite'] in known_flags or c['prerequisite'] in stage_ids,('Unknown prerequisite',c['id'])
 assert 0<=c['reward']<=20
 assert c['id']=='ren_optional_supplies' or len({s['type'] for s in c['stages']})>=2,('One-mechanic placeholder',c['id'])
reward_ids=set()
for season in w['seasons']:
 assert season['chain'] in {c['id'] for c in w['chains']}
 assert all(f in known_flags for f in season['requirements'])
 for reward in season['rewards']:
  assert reward['id'] not in reward_ids,('Duplicate seasonal reward',reward['id']);reward_ids.add(reward['id'])
  assert reward['battle'] in teams
  assert any(s['type']=='battle' and s['target']==reward['battle'] for c in w['chains'] if c['id']==season['chain'] for s in c['stages'])
  assert len(reward['ivs'])==6 and all(0<=v<=31 for v in reward['ivs'])
  assert any(s['type']=='claim' and s['item']==reward['id'] for c in w['chains'] if c['id']==season['chain'] for s in c['stages'])
assert len({s['id'] for s in allstages})==len(allstages),'Duplicate stage ID'
assert len(w['campaign'])==69
assert not any(s['type']=='buy' for s in w['campaign'])
assert 'ren_supplies' not in {s['id'] for s in w['campaign']}
regular=[c for c in w['chains'] if c['id']!='weather_duo_01'];assert len(regular)>=35
assert all(c['id']=='ren_optional_supplies' or 3<=len(c['stages'])<=15 for c in regular)
assert len(next(c for c in w['chains'] if c['id']=='weather_duo_01')['stages'])>=20
for s in allstages:
 assert s['scene'] in scenes
 assert s['target'] in actors or s['target'] in pois,s['id']
 assert s['amount']>0
 if s['type'] in ('deliver','collect'):assert s['item'] and ':' in s['item']
 if s['type']=='battle':assert s['target'] in teams
for scene in scenes.values():
 nodes={n['id']:n for n in scene['nodes']};seen=set();visiting=set()
 def visit(id):
  assert id in nodes,(scene['id'],id)
  assert id not in visiting,('Dialogue cycle',scene['id'],id)
  if id in seen:return
  visiting.add(id);seen.add(id);n=nodes[id]
  assert n['speaker'] in actors or n['speaker']=='narrator'
  assert n['choices'];assert len({c['id'] for c in n['choices']})==len(n['choices'])
  for c in n['choices']:
   if c['next']:visit(c['next'])
  visiting.remove(id)
 visit(scene['start']);assert len(seen)==len(nodes),('Unreachable node',scene['id'])
for t in teams.values():
 assert 1<=len(t['members'])<=6
 for m in t['members']:
  assert len(m['moves'])==4 and len(set(m['moves']))==4
  assert len(m['evs'])==6 and all(0<=v<=252 for v in m['evs']) and sum(m['evs'])<=510
  assert all(k in ('hp','atk','def','spa','spd','spe') and v in (0,31) for k,v in m['ivOverrides'].items())
  assert m['nature'] and m['ability'] and 1<=m['level']<=100
keys=set()
for s in allstages:keys.update((s['title'],s['objective']))
for c in w['chains']:keys.add(c['title'])
for s in scenes.values():
 for n in s['nodes']:
  keys.add(n['text']);keys.update(c['text'] for c in n['choices'])
for locale,lang in langs.items():
 assert not keys-set(lang),(locale,'Missing localization',keys-set(lang))
 for key in keys:
  value=lang[key];assert '\ufffd' not in value and '/cworld' not in value and 'first_anomaly_site' not in value,(locale,key)
for locale,lang in langs.items():
 for result in ('win','loss'):
  outcome=[lang[n['text']] for scene in w['scenes'] if scene['id'].startswith('outcome.') and scene['id'].endswith('.'+result) for n in scene['nodes'] if n['id']=='start']
  assert len(outcome)==len(set(outcome)),(locale,'Repeated trainer outcome',result)
 assert not any('magic seal' in lang[k].lower() or 'phong ấn' in lang[k].lower() for k in keys),(locale,'Obsolete seal copy')
order=[s['target'] for s in w['campaign'] if s['type']=='battle']
assert order.index('captain_dorian')<order.index('battle_tower_trainer_01')<order.index('league_elite_01')<order.index('aurelia')<order.index('school_wolf_master')<order.index('mysterious')
archive=[s for s in w['campaign'] if s['id'].startswith('archive_record')];assert len(archive)==3
assert w['campaign'].index(archive[0])>next(i for i,s in enumerate(w['campaign']) if s['target']=='school_wolf_master')
assert not any(s['target']=='resonance_heart' for s in allstages)
assert json.loads((root/'data/cobblemonworld/npcs/mysterious.json').read_text())['specialActor']
assert not any(a['id']=='mysterious' and a.get('spawnPolicy')=='personal' for a in w['actors'])
assert {k for k in langs['vi_vn'] if k.startswith('narrative.')}=={k for k in langs['en_us'] if k.startswith('narrative.')},'Narrative locale key mismatch' 
for p in (root/'data/cobblemonworld/shops').glob('*.json'):
 d=json.loads(p.read_text())
 for e in d.get('entries',[])+d.get('rules',[])+list(d.get('overrides',{}).values()):assert 0<e['price']<=500
print('CONTENT_VALIDATED',dict(main=len(w['campaign']),substantial_chains=len(regular),season_stages=len(next(c for c in w['chains'] if c['id']=='weather_duo_01')['stages']),scenes=len(scenes),teams=len(teams),localized_keys=len(keys),objective_types=dict(collections.Counter(s['type'] for s in allstages))))
