"""Build reviewable per-species move kits; runtime never chooses an ordinal recipe.

Only moves in the species/form's real learnset are candidates. Curated designs
win explicitly. A duplicate mechanic fails compilation, rather than being hidden
behind a different name, number, counter label, or animation.
"""
import json,copy,itertools,re
from pathlib import Path
from designs import D,op,cond,cost,when,later,effect,design
HERE=Path(__file__).resolve().parent
SPECIES=json.loads((HERE/'species_sources.json').read_text())['species']
MOVES=json.loads((HERE/'move_sources.json').read_text())['moves']

def boosts(stats,target):
 out=[]
 for stat,n in (stats or {}).items():
  if stat in ('atk','spa'):out.append(op('MODIFY_POWER',150*n,target,'TURN_END'))
  elif stat in ('def','spd'):out.append(op('PREVENT_DESTROY',target=target,duration='TURN_END') if n>0 else op('MODIFY_POWER',-200,target,'TURN_END'))
  elif stat=='spe':out.append(op('EXTRA_ATTACK',1,target) if n>0 else op('CANNOT_ATTACK',target=target,duration='TURN_END'))
  elif stat=='accuracy':out.append(op('PREVENT_TARGET' if n>0 else 'CANNOT_ACTIVATE',target=target,duration='TURN_END'))
  elif stat=='evasion':out.append(op('PREVENT_TARGET',target=target,duration='TURN_END') if n>0 else op('REMOVE_STATUS',target=target,status='PREVENT_TARGET'))
 return out

def statuses(status,target='TARGET'):
 return copy.deepcopy({
  'brn':[op('MODIFY_POWER',-250,target,'TURN_END'),later([op('DAMAGE_LP',200,recipient='ENEMY')])],
  'psn':[later([op('DAMAGE_LP',300,recipient='ENEMY')])],
  'tox':[op('ADD_COUNTER',1,target,counter='poison',max=3),later([op('DAMAGE_LP',300,recipient='ENEMY')])],
  'par':[op('CANNOT_ATTACK',target=target,duration='TURN_END')],
  'slp':[op('SET_FACE_DOWN',target=target),op('CANNOT_ACTIVATE',target=target,duration='TURN_END')],
  'frz':[op('CHANGE_POSITION',target=target,position='DEFENSE'),op('CANNOT_ATTACK',target=target,duration='NEXT_TURN_END')],
 }.get(status,[]))

MOVE_RULES={
 'protect':[op('PREVENT_DESTROY',duration='TURN_END')],
 'detect':[op('PREVENT_TARGET',duration='TURN_END')],
 'wideguard':[op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END')],
 'quickguard':[op('PREVENT_TARGET',target='ALL_ALLIES',duration='TURN_END')],
 'endure':[op('PREVENT_DESTROY',duration='TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')],
 'substitute':[op('SEND_GRAVE',target='HAND'),op('PREVENT_TARGET',duration='NEXT_TURN_END')],
 'rest':[op('HEAL_LP',600),op('REMOVE_STATUS',status='ALL'),op('SET_FACE_DOWN'),op('CANNOT_ATTACK',duration='NEXT_TURN_END')],
 'recover':[op('HEAL_LP',500)],
 'roost':[op('HEAL_LP',400),op('CHANGE_POSITION',position='DEFENSE')],
 'wish':[later([op('HEAL_LP',600)])],
 'healingwish':[op('SEND_GRAVE'),op('HEAL_LP',1000),op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL')],
 'leechseed':[op('REMEMBER',target='TARGET'),later([when(cond('SURVIVED',target='REMEMBERED'),[op('DAMAGE_LP',250,recipient='ENEMY'),op('HEAL_LP',250)])])],
 'batonpass':[op('MODIFY_POWER',200,'ALLY_MONSTER','TURN_END'),op('RETURN_HAND')],
 'teleport':[op('RETURN_HAND'),op('SUMMON_FROM_HAND',target='HAND')],
 'roar':[op('RETURN_HAND',target='TARGET')],
 'whirlwind':[op('RETURN_DECK',target='TARGET',placement='BOTTOM')],
 'haze':[op('REMOVE_STATUS',target='ALL_FIELD',status='ALL')],
 'spite':[op('CANNOT_ACTIVATE',target='TARGET',duration='NEXT_TURN_END')],
 'taunt':[op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],
 'encore':[op('CHANGE_POSITION',target='TARGET',position='ATTACK'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],
 'disable':[op('REMOVE_EFFECT',target='TARGET',duration='TURN_END')],
 'perishsong':[op('REMEMBER',target='ALL_FIELD'),later([op('DESTROY',target='REMEMBERED')])],
 'destinybond':[op('REMEMBER',target='TARGET'),op('SEND_GRAVE'),op('DESTROY',target='REMEMBERED')],
 'counter':[op('REFLECT_DAMAGE',duration='TURN_END')],
 'mirrorcoat':[op('REFLECT_DAMAGE',duration='TURN_END'),op('PREVENT_TARGET',duration='TURN_END')],
 'bide':[op('PREVENT_DAMAGE',duration='TURN_END'),later([op('MODIFY_POWER',300,duration='NEXT_TURN_END')])],
 'trick':[op('CONTROL_CHANGE',target='TARGET',duration='TURN_END'),op('RETURN_HAND',target='ALLY_CARD')],
 'switcheroo':[op('SWAP_POWER',target='TARGET',duration='TURN_END'),op('RETURN_HAND',target='ALLY_CARD')],
 'psychup':[op('COPY_EFFECT',target='TARGET',duration='TURN_END')],
 'roleplay':[op('COPY_EFFECT',target='TARGET',duration='NEXT_TURN_END')],
 'spikes':[later([op('MODIFY_POWER',-200,'ALL_ENEMIES','TURN_END')])],
 'stealthrock':[later([op('DAMAGE_LP',250,recipient='ENEMY')])],
 'toxicspikes':[later([op('CANNOT_ACTIVATE',target='ENEMY_MONSTER',duration='TURN_END')])],
 'stickyweb':[op('CANNOT_ATTACK',target='ALL_ENEMIES',duration='TURN_END')],
 'defog':[op('REMOVE_STATUS',target='ALL_FIELD',status='ALL'),op('RETURN_HAND',target='ENEMY_CARD')],
 'safeguard':[op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL'),op('PREVENT_TARGET',target='ALL_ALLIES',duration='TURN_END')],
 'reflect':[op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END')],
 'lightscreen':[op('PREVENT_TARGET',target='ALL_ALLIES',duration='TURN_END')],
 'auroraveil':[op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END'),op('PREVENT_DESTROY',target='ALL_ALLIES',duration='TURN_END')],
 'trickroom':[op('SWAP_POWER',target='TARGET',duration='TURN_END'),op('CHANGE_POSITION',target='ALL_FIELD',position='DEFENSE')],
 'gravity':[op('REMOVE_STATUS',target='ALL_FIELD',status='PREVENT_TARGET'),op('CANNOT_ATTACK',target='ALL_ENEMIES',duration='TURN_END')],
 'raindance':[op('MODIFY_POWER',200,'ALL_ALLIES','TURN_END'),op('REMOVE_STATUS',status='CANNOT_ATTACK')],
 'sunnyday':[op('MODIFY_POWER',250,'ALL_ALLIES','TURN_END'),op('HEAL_LP',200)],
 'sandstorm':[op('PREVENT_DESTROY',duration='TURN_END'),op('MODIFY_POWER',-150,'ALL_ENEMIES','TURN_END')],
 'snowscape':[op('PREVENT_DAMAGE',duration='TURN_END'),op('CHANGE_POSITION',target='ALL_ENEMIES',position='DEFENSE')],
 'electricterrain':[op('REMOVE_STATUS',target='ALL_ALLIES',status='CANNOT_ATTACK'),op('EXTRA_ATTACK',1)],
 'grassyterrain':[later([op('HEAL_LP',300)]),op('PREVENT_DESTROY',duration='TURN_END')],
 'mistyterrain':[op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL'),op('PREVENT_DAMAGE',duration='TURN_END')],
 'psychicterrain':[op('CANNOT_ACTIVATE',target='ALL_ENEMIES',duration='TURN_END'),op('LOOK_TOP_DECK',2)],
 'celebrate':[op('REVEAL',target='HAND'),op('HEAL_LP',200)],
 'happyhour':[op('DRAW',1),op('REVEAL',target='HAND')],
 'sleeptalk':[when(cond('POSITION','FACE_DOWN_DEFENSE'),[op('FLIP_FACE_UP'),op('DRAW',1)])],
 'yawn':[op('REMEMBER',target='TARGET'),later([op('SET_FACE_DOWN',target='REMEMBERED')])],
 'attract':[op('CANNOT_ATTACK',target='TARGET',duration='TURN_END'),op('PREVENT_TARGET',duration='TURN_END')],
 'followme':[op('REDIRECT_ATTACK'),op('PREVENT_DESTROY',duration='TURN_END')],
 'helpinghand':[op('MODIFY_POWER',300,'ALLY_MONSTER','TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')],
 'acupressure':[op('ADD_COUNTER',1,counter='focus',max=3),when(cond('COUNTER_AT_LEAST','focus',2),[op('REMOVE_COUNTER',2,counter='focus'),op('PREVENT_DESTROY',duration='TURN_END'),op('EXTRA_ATTACK',1)])],
 'conversion':[op('COPY_EFFECT',target='ALLY_MONSTER',duration='TURN_END')],
 'conversion2':[op('PREVENT_DAMAGE',duration='NEXT_TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')],
 'recycle':[dict(op('RETURN_HAND',target='GRAVEYARD'),filter=dict(category='item'))],
}

def move_ops(mid):
 m=MOVES.get(mid)
 if not m:return None
 if mid in MOVE_RULES:return copy.deepcopy(MOVE_RULES[mid])
 ops=[];friendly=m.get('target') in ('self','allySide','allies');target='SELF' if friendly else 'TARGET'
 if m.get('category')!='Status':
  # Elemental attack adaptations are primitives, never assigned as entire card identities.
  typ=m['type'].lower()
  attack={
   'normal':[op('MODIFY_POWER',150,duration='TURN_END')],
   'fire':[op('MODIFY_POWER',200,duration='TURN_END'),op('PIERCE',duration='TURN_END')],
   'water':[op('MODIFY_POWER',-200,'TARGET','TURN_END')],
   'electric':[op('CANNOT_ATTACK',target='TARGET',duration='TURN_END')],
   'grass':[op('HEAL_LP',250)],'ice':[op('CHANGE_POSITION',target='TARGET',position='DEFENSE')],
   'fighting':[op('PIERCE',duration='TURN_END')],
   'poison':[op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],
   'ground':[op('CHANGE_POSITION',target='TARGET',position='DEFENSE'),op('MODIFY_POWER',150,duration='TURN_END')],
   'flying':[op('PREVENT_TARGET',duration='TURN_END')],
   'psychic':[op('LOOK_TOP_DECK',2),op('REORDER_DECK',2,order='POWER_DESC')],
   'bug':[op('REVEAL',target='TARGET'),op('MODIFY_POWER',-100,'TARGET','TURN_END')],
   'rock':[op('PREVENT_DESTROY',duration='TURN_END')],
   'ghost':[op('MODIFY_POWER',-150,'TARGET','TURN_END'),op('PREVENT_TARGET',duration='TURN_END')],
   'dragon':[op('MODIFY_POWER',300,duration='TURN_END')],
   'dark':[op('INSPECT_SET',target='ENEMY_CARD')],
   'steel':[op('PREVENT_DAMAGE',duration='TURN_END')],
   'fairy':[op('REMOVE_STATUS',status='ALL')],
  }
  ops=copy.deepcopy(attack.get(typ,attack['normal']))
  ops.append(op('CHANGE_POSITION',position='ATTACK') if m.get('category')=='Physical' else op('MODIFY_POWER',-100,'TARGET','TURN_END'))
  if m.get('multihit'):ops.append(op('EXTRA_ATTACK',1))
  if m.get('critRatio',1)>1 or m.get('willCrit'):ops.append(op('PIERCE',duration='TURN_END'))
  if m.get('breaksProtect'):ops.append(op('REMOVE_STATUS',target='TARGET',status='PREVENT_DESTROY'))
  if m.get('ignoreDefensive'):ops.append(op('REMOVE_STATUS',target='TARGET',status='PREVENT_DAMAGE'))
 ops+=boosts(m.get('boosts'),target)+statuses(m.get('status'))
 v=m.get('volatileStatus')
 if v in ('confusion','flinch'):ops.append(op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'))
 if v in ('partiallytrapped','trapped','octolock'):ops.append(op('CANNOT_ATTACK',target='TARGET',duration='NEXT_TURN_END'))
 if v in ('curse','leechseed'):ops+=statuses('psn')
 if m.get('heal'):ops.append(op('HEAL_LP',400))
 if m.get('drain'):ops.append(op('HEAL_LP',250))
 if m.get('recoil'):ops.append(op('CANNOT_ATTACK',duration='TURN_END'))
 if m.get('selfSwitch'):ops.append(op('RETURN_HAND'))
 if m.get('forceSwitch'):ops.append(op('RETURN_HAND',target='TARGET'))
 for sec in [m.get('secondary')]+m.get('secondaries',[]):
  if isinstance(sec,dict):
   ops+=boosts(sec.get('boosts'),'TARGET')+statuses(sec.get('status'))
   if sec.get('volatileStatus')=='flinch':ops.append(op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'))
   if sec.get('self'):ops+=boosts(sec['self'].get('boosts'),'SELF')
 if m.get('self'):
  ops+=boosts(m['self'].get('boosts'),'SELF')
  if m['self'].get('volatileStatus')=='mustrecharge':ops.append(op('CANNOT_ATTACK',duration='NEXT_TURN_END'))
 if not ops:return None
 seen=set();out=[]
 for o in ops:
  k=json.dumps(o,sort_keys=True)
  if k not in seen:out.append(o);seen.add(k)
 return out

def mechanics(v):
 if isinstance(v,list):return [mechanics(x) for x in v]
 if isinstance(v,dict):
  out={}
  for k,x in sorted(v.items()):
   if k in ('vfx','textKey','id','name','authorship','moves','source') or x in (None,[],{}):continue
   if k=='operations':
    parts={json.dumps(mechanics(part),sort_keys=True,separators=(',',':')):mechanics(part) for part in x};out[k]=[parts[t] for t in sorted(parts)]
   elif k in ('types','tags'):out[k]=['typed-filter']
   elif k=='value' and v.get('type') in ('TYPE','TAG'):out[k]='typed-condition'
   elif k in ('amount','minLevel','maxLevel','minPower','maxPower','max','count'):out[k]=(int(x)>0)-(int(x)<0)
   elif k in ('counter','memory'):out[k]='named-slot'
   elif k=='value' and v.get('type','').startswith('COUNTER_'):out[k]='named-slot'
   else:out[k]=mechanics(x)
  return out
 return v

def signature(e):
 e=copy.deepcopy(e)
 if isinstance(e,dict) and 'spec' in e:e['spec'].pop('stages',None)
 return json.dumps(mechanics(e),sort_keys=True,separators=(',',':'))
def learned(j):return {m.split(':',1)[-1] for m in j.get('moves',[]) if m.split(':',1)[-1] in MOVES}
def needs_target(ops):
 return any(o.get('target')=='TARGET' or needs_target(o.get('children',[])) or needs_target(o.get('otherwise',[])) for o in ops)
def main():
 frequency={}
 for j in SPECIES.values():
  for m in learned(j):frequency[m]=frequency.get(m,0)+1
 result=copy.deepcopy(D);seen={signature(d['effect']):k for k,d in result.items()};assert len(seen)==len(result),'Curated mechanic duplicate'
 entries=[]
 for species,j in SPECIES.items():
  entries.append((species,[],j))
  for form in j.get('forms',[]):
   if not form.get('aspects'):continue
   merged=dict(j);merged.update(form);entries.append((species,sorted(form['aspects']),merged))
 # Restrictive learnsets get first choice. Catalog additions cannot change checked-in designs.
 entries.sort(key=lambda e:(len(learned(e[2])),e[0],e[1]))
 failures=[]
 for species,aspects,j in entries:
  key=species+('|'+'|'.join(aspects) if aspects else '')
  if key in result:continue
  moves=sorted(learned(j),key=lambda m:(frequency.get(m,1),0 if MOVES[m].get('type','').lower()==j['primaryType'] else 1,-MOVES[m].get('basePower',0),m))
  candidates=[];patterns=set()
  for m in moves:
   ops=move_ops(m)
   if ops and signature(ops) not in patterns:patterns.add(signature(ops));candidates.append((m,ops))
  chosen=None
  for count in (2,3,4,1):
   for kit in itertools.combinations(candidates[:18],count):
    ops=[o for _,part in kit for o in part]
    if len(ops)>24:continue
    costs=[]
    # High-power/recharge moves pay in cards/setup, never LP.
    if any(MOVES[m].get('basePower',0)>=100 for m,_ in kit):costs=[cost('REVEAL_HAND',types=[j['primaryType']])]
    e=effect(ops,'enemy' if needs_target(ops) else 'none',costs)
    fp=signature(e)
    if fp not in seen:chosen=(kit,e);seen[fp]=key;break
   if chosen:break
  if chosen is None:failures.append((key,[m for m,_ in candidates]));continue
  kit,e=chosen
  result[key]={'name':' / '.join(MOVES[m]['name'] for m,_ in kit),'effect':e,'authorship':'learnset-backed move adaptation','moves':[m for m,_ in kit]}
 if failures:raise AssertionError('Author explicit missing kits: '+str(failures))
 # Freeze exact individual choices. Applying overrides only reads this resource.
 out=HERE.parent/'resources/data/svarcade_tcg/pokemon_effects.json'
 out.write_text(json.dumps(dict(version=1,definitions=dict(sorted(result.items()))),ensure_ascii=False,indent=2)+'\n')
 print('CARDWORLDS_GAMEPLAY_CONTENT compiled='+str(len(result))+' officialSpecies='+str(len(SPECIES))+' duplicateMechanics=0 monsterLpCosts=0')
if __name__=='__main__':main()
