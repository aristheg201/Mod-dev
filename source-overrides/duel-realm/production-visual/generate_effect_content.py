"""Reproducible authored effect compositions and generic registry species rules."""
import json
from pathlib import Path
root=Path(__file__).resolve().parent/'resources/data/svarcade_tcg'
root.mkdir(parents=True,exist_ok=True)
def op(kind,amount=0,target='SELF',duration=None,**flags):
    return dict(type=kind,amount=amount,target=target,duration=duration,flags={k:str(v).lower() if isinstance(v,bool) else str(v) for k,v in flags.items()})
def condition(kind,value='',amount=0,target='SELF',children=None):
    return dict(type=kind,value=value,amount=amount,target=target,children=children or [])
def effect(operations,target='none',speed=1,costs=None,conditions=None,once=True,triggers=None,mode='STATUS',element=None,filter=None):
    selectors={'enemy':'TARGET','ally':'TARGET','grave':'TARGET','chain':'CHAIN_SOURCE','none':'SELF'}
    return dict(operation='composite',amount=0,speed=speed,lifeCost=0,target=target,phases=['DRAW','STANDBY','MAIN1','BATTLE','MAIN2','END'] if speed>1 else ['MAIN1','MAIN2'],oncePerTurn=once,
      spec=dict(triggers=triggers or ['ON_ACTIVATE'],conditions=conditions or [],costs=costs or [],targets=dict(selector=selectors[target],min=0 if target=='none' else 1,max=1,filter=filter),operations=operations,oncePerDuel=False,optional=False,textKey=None,
      vfx=dict(mode=mode,profile=element,duration=1400,animation=dict(semantic='CAST_STATUS' if mode=='STATUS' else 'ATTACK_PHYSICAL' if mode=='MELEE' else 'ATTACK_SPECIAL',windup=.25,release=.46,impact=.72,recovery=.9))))
def cost(kind,amount=1,target=None,filter=None,counter=None):
    return dict(type=kind,amount=amount,target=target,filter=filter,counter=counter)
def pokemon_filter(types=None,minLevel=0):
    return dict(category='pokemon',types=types or [],tags=[],minLevel=minLevel,maxLevel=12,minPower=0,maxPower=100000,position=None,zone=None,controller=None,faceUp=None)
def ifop(test,children,target='SELF'):
    return dict(type='IF',amount=0,target=target,zone=None,duration=None,flags={},children=children,condition=test,otherwise=[],filter=None)
rules=[]
identities={
 'fire':([op('MODIFY_POWER',200,duration='TURN_END'),op('PIERCE',target='SELF',duration='TURN_END')],'none','MELEE'),
 'water':([op('RETURN_HAND',target='TARGET'),op('MODIFY_POWER',-150,target='ALL_ENEMIES',duration='TURN_END')],'enemy','PROJECTILE'),
 'electric':([op('APPLY_STATUS',target='TARGET',duration='TURN_END',status='CANNOT_ATTACK'),op('MODIFY_POWER',-200,target='TARGET',duration='TURN_END')],'enemy','PROJECTILE'),
 'grass':([op('HEAL_LP',300),op('ADD_COUNTER',1,counter='growth'),op('SEARCH',1,target='DECK',count=1,reveal=True)],'none','STATUS'),
 'ice':([op('CHANGE_POSITION',target='TARGET',position='DEFENSE'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END')],'enemy','BEAM'),
 'psychic':([op('LOOK_TOP_DECK',3),op('REORDER_DECK',3,order='POWER_DESC'),op('DRAW',1)],'none','STATUS'),
 'ghost':([op('REVIVE',target='TARGET'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END')],'grave','STATUS'),
 'dark':([op('DISCARD',target='RANDOM_HAND',controller='ENEMY'),op('MODIFY_POWER',150,duration='TURN_END')],'none','MELEE'),
 'steel':([op('PREVENT_DESTROY',duration='TURN_END'),op('MODIFY_POWER',100,duration='TURN_END')],'none','MELEE'),
 'dragon':([op('MODIFY_POWER',300,duration='TURN_END'),op('EXTRA_ATTACK',1)],'none','BEAM'),
 'fairy':([op('REMOVE_STATUS',target='TARGET',status='ALL'),op('PREVENT_TARGET',target='TARGET',duration='TURN_END'),op('HEAL_LP',200)],'ally','STATUS'),
 'fighting':([op('MODIFY_POWER',200,duration='TURN_END'),op('PIERCE',duration='TURN_END')],'none','MELEE'),
 'poison':([op('MODIFY_POWER',-300,target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],'enemy','PROJECTILE'),
 'ground':([op('CHANGE_POSITION',target='ALL_ENEMIES',position='DEFENSE'),op('MODIFY_POWER',100,duration='TURN_END')],'none','AOE'),
 'rock':([op('PREVENT_DESTROY',duration='TURN_END'),op('ADD_COUNTER',1,counter='fortitude')],'none','MELEE'),
 'bug':([op('SEARCH',1,target='DECK',reveal=True),op('MODIFY_POWER',100,duration='TURN_END')],'none','PROJECTILE'),
 'flying':([op('PREVENT_TARGET',duration='TURN_END'),op('CHANGE_POSITION',target='ENEMY_MONSTER',count=1,position='DEFENSE')],'none','MELEE'),
 'normal':([op('DRAW',1),op('DISCARD',target='HAND')],'none','MELEE'),
}
high_costs={
 'fire':[cost('DISCARD',filter=pokemon_filter(['fire']))],
 'water':[cost('RETURN_SELF')],
 'electric':[cost('REVEAL_HAND',filter=pokemon_filter(['electric']))],
 'grass':[],
 'ice':[cost('DISCARD',filter=pokemon_filter(['ice']))],
 'psychic':[cost('REVEAL_HAND',filter=pokemon_filter(['psychic']))],
 'ghost':[],
 'dark':[cost('DISCARD',filter=pokemon_filter(['dark']))],
 'steel':[],
 'dragon':[cost('TRIBUTE',filter=pokemon_filter(minLevel=4))],
 'fairy':[cost('REVEAL_HAND',filter=pokemon_filter(['fairy']))],
 'fighting':[cost('SEND_GRAVE',target='HAND',filter=pokemon_filter())],
 'poison':[cost('BANISH_GRAVE',filter=pokemon_filter(['poison']))],
 'ground':[cost('TRIBUTE',filter=pokemon_filter(minLevel=3))],
 'rock':[cost('REVEAL_HAND',filter=pokemon_filter(['rock']))],
 'bug':[cost('REVEAL_HAND',filter=pokemon_filter(['bug']))],
 'flying':[cost('REVEAL_HAND',filter=pokemon_filter(['flying']))],
 'normal':[cost('REVEAL_HAND',filter=pokemon_filter())],
}
low_costs={
 'ghost':[cost('BANISH_GRAVE',filter=pokemon_filter(['ghost']))],
 'psychic':[cost('REVEAL_HAND',filter=pokemon_filter(['psychic']))],
 'dark':[cost('DISCARD',filter=pokemon_filter(['dark']))],
}
high_ops={
 'fire':[op('DESTROY',target='TARGET'),op('ADD_COUNTER',1,counter='momentum',max=3),
         ifop(condition('COUNTER_AT_LEAST',value='momentum',amount=2),[op('REMOVE_COUNTER',2,counter='momentum'),op('MODIFY_POWER',350,duration='TURN_END'),op('PIERCE',duration='TURN_END')])],
 'water':[op('RETURN_HAND',target='TARGET'),op('DRAW',1),op('HEAL_LP',250)],
 'electric':[op('APPLY_STATUS',target='TARGET',duration='TURN_END',status='CANNOT_ATTACK'),op('ADD_COUNTER',1,counter='charge',max=3),
             ifop(condition('COUNTER_AT_LEAST',value='charge',amount=2),[op('REMOVE_COUNTER',2,counter='charge'),op('MODIFY_POWER',250,duration='TURN_END'),op('EXTRA_ATTACK',1)])],
 'ice':[op('CHANGE_POSITION',target='TARGET',position='DEFENSE'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END'),op('PREVENT_TARGET',duration='TURN_END')],
 'psychic':[op('LOOK_TOP_DECK',3),op('REORDER_DECK',3,order='POWER_DESC'),op('DRAW',1),op('ADD_COUNTER',1,counter='focus',max=3),
            ifop(condition('COUNTER_AT_LEAST',value='focus',amount=2),[op('REMOVE_COUNTER',2,counter='focus'),op('PREVENT_TARGET',duration='TURN_END')])],
 'dark':[op('DISCARD',target='RANDOM_HAND',controller='ENEMY'),op('BANISH',target='TARGET'),op('MODIFY_POWER',200,duration='TURN_END')],
 'dragon':[op('DESTROY',target='TARGET'),op('MODIFY_POWER',300,duration='TURN_END'),op('EXTRA_ATTACK',1)],
 'fairy':[op('REMOVE_STATUS',target='TARGET',status='ALL'),op('PREVENT_TARGET',target='TARGET',duration='TURN_END'),op('PREVENT_DESTROY',target='TARGET',duration='TURN_END'),op('HEAL_LP',300)],
 'fighting':[op('DESTROY',target='TARGET'),op('MODIFY_POWER',300,duration='TURN_END'),op('PIERCE',duration='TURN_END')],
 'poison':[op('MODIFY_POWER',-400,target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'),op('MILL',1)],
 'ground':[op('CHANGE_POSITION',target='ALL_ENEMIES',position='DEFENSE'),op('CANNOT_ATTACK',target='ALL_ENEMIES',duration='TURN_END'),op('MODIFY_POWER',150,duration='TURN_END')],
 'rock':[op('PREVENT_DESTROY',duration='TURN_END'),op('ADD_COUNTER',1,counter='fortitude',max=3),
         ifop(condition('COUNTER_AT_LEAST',value='fortitude',amount=2),[op('REMOVE_COUNTER',2,counter='fortitude'),op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END')])],
 'bug':[op('SEARCH',1,target='DECK',reveal=True),op('MODIFY_POWER',150,duration='TURN_END'),op('DRAW',1)],
 'flying':[op('PREVENT_TARGET',duration='TURN_END'),op('CHANGE_POSITION',target='ENEMY_MONSTER',count=1,position='DEFENSE'),op('EXTRA_ATTACK',1)],
 'normal':[op('DRAW',2),op('DISCARD',target='HAND'),op('PREVENT_DESTROY',duration='TURN_END')],
}
for typ,(ops,target,mode) in identities.items():
    high_target='enemy' if typ in ('fire','water','electric','ice','dark','dragon','fighting','poison') else 'ally' if typ=='fairy' else 'none'
    rules.append(dict(types=[typ],minLevel=7,maxLevel=12,form=None,effect=effect(high_ops.get(typ,ops),high_target,2 if typ=='electric' else 1,high_costs.get(typ,[]),mode=mode,element=typ)))
    rules.append(dict(types=[typ],minLevel=1,maxLevel=6,form=None,effect=effect(ops,target,2 if typ=='electric' else 1,low_costs.get(typ,[]),mode=mode,element=typ)))
cards={}
def card(id,name,category,ops,target='none',speed=1,costs=None,conditions=None,mode='STATUS',triggers=None,element='psychic',once=True,filter=None):
    e=effect(ops,target,speed,costs,conditions,once,triggers,mode,element,filter)
    cards[id]=dict(id=id,name=name,category=category,species='',aspects=[],type=element,family='arcane_tactics',evolvesFrom='',extra=False,level=0,power=0,text='@effect',set='Duel Tactics',rarity='Rare',sources=['PACK','STARTER'],effect=e,triggers=[],modifiers=[])
card('shatter_gate','Shatter Gate','item',[op('DESTROY',target='TARGET'),op('MODIFY_POWER',150,target='ALL_ALLIES',duration='TURN_END')],'enemy')
card('void_seal','Void Seal','technique',[op('BANISH',target='TARGET'),op('DRAW',1)],'enemy',2,[cost('DISCARD')],element='ghost')
card('tidal_recall','Tidal Recall','technique',[op('RETURN_HAND',target='TARGET'),op('CHANGE_POSITION',target='ENEMY_MONSTER',position='DEFENSE')],'enemy',2,element='water')
card('recruit_signal','Recruit Signal','item',[op('SEARCH',1,target='DECK',reveal=True),op('SHUFFLE')],filter=dict(category='pokemon',maxLevel=4))
card('grave_bloom','Grave Bloom','item',[op('REVIVE',target='TARGET'),op('PREVENT_DESTROY',target='TARGET',duration='TURN_END')],'grave',1,[cost('BANISH_GRAVE')],element='grass')
card('counter_gate','Counter Gate','reaction',[op('NEGATE_ACTIVATION'),op('DRAW',1)],'chain',3,[cost('DISCARD')],[condition('CHAIN_AT_LEAST',amount=1)])
card('attack_mirror','Attack Mirror','reaction',[op('NEGATE_ATTACK'),op('CHANGE_POSITION',target='ATTACKER',position='DEFENSE')],speed=2,conditions=[condition('COUNT_AT_LEAST',amount=1,target='ATTACKER')],triggers=['ON_ATTACK_DECLARE'])
card('summon_snare','Summon Snare','reaction',[op('RETURN_HAND',target='ENEMY_MONSTER'),op('CANNOT_SUMMON',target='ALL_ENEMIES',duration='TURN_END')],speed=2,conditions=[condition('COUNT_AT_LEAST',amount=1,target='ENEMY_MONSTER'),condition('EVENT_CONTROLLER',value='ENEMY')],triggers=['ON_SUMMON','ON_SPECIAL_SUMMON'])
card('ember_domain','Ember Domain','stadium',[op('MODIFY_POWER',250,target='ALL_ALLIES')],triggers=['CONTINUOUS'],element='fire',filter=dict(types=['fire']))
card('guardian_domain','Guardian Domain','trainer',[op('PREVENT_DESTROY',target='ALL_ALLIES')],triggers=['CONTINUOUS'],element='steel',filter=dict(position='DEFENSE'))
card('soul_exchange','Soul Exchange','item',[op('SUMMON_FROM_BANISHED',target='BANISHED'),op('MILL',2)],costs=[cost('BANISH_GRAVE',filter=pokemon_filter())],element='ghost')
card('mind_theft','Mind Theft','technique',[op('CONTROL_CHANGE',target='TARGET',duration='TURN_END'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END')],'enemy',1,[cost('LP_COST',800)],element='psychic')
card('battle_drive','Battle Drive','item',[op('MODIFY_POWER',400,target='TARGET',duration='TURN_END'),op('PIERCE',target='TARGET',duration='TURN_END'),op('EXTRA_ATTACK',1,target='TARGET')],'ally',element='fighting')
card('crystal_ward','Crystal Ward','technique',[op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END'),op('PREVENT_TARGET',target='TARGET',duration='TURN_END')],'ally',2,[cost('REVEAL_HAND',filter=pokemon_filter())],element='ice')
card('memory_echo','Memory Echo','trainer',[op('COPY_EFFECT',target='TARGET',duration='TURN_END'),op('RETURN_HAND',target='TARGET')],'enemy',1,[cost('DISCARD')],element='psychic')
card('grave_harvest','Grave Harvest','item',[op('RETURN_HAND',target='GRAVEYARD',count=2),op('HEAL_LP',300)],costs=[cost('BANISH_GRAVE',filter=pokemon_filter())],element='grass')
card('stellar_filter','Stellar Filter','item',[op('DRAW',2),op('DISCARD',target='HAND'),op('LOOK_TOP_DECK',1)],element='fairy')
card('dark_current','Dark Current','item',[op('DESTROY',target='ALL_ENEMIES'),op('CANNOT_ATTACK',target='ALL_ALLIES',duration='TURN_END')],costs=[cost('TRIBUTE',filter=pokemon_filter(minLevel=4)),cost('DISCARD',filter=pokemon_filter())],element='dark',conditions=[condition('COUNT_AT_LEAST',target='ENEMY_MONSTER',amount=2)])
card('reversal_seal','Reversal Seal','reaction',[op('NEGATE_EFFECT'),op('RETURN_DECK',target='CHAIN_SOURCE',placement='BOTTOM')],'chain',3,conditions=[condition('CHAIN_AT_LEAST',amount=1)],element='ghost')
card('emergency_rise','Emergency Rise','reaction',[op('REVIVE',target='GRAVEYARD'),op('HEAL_LP',500)],speed=2,conditions=[condition('LP_AT_MOST',amount=2500),condition('COUNT_AT_LEAST',target='GRAVEYARD',amount=1)],element='fairy')
for rule in rules:
 typ=rule['types'][0]
 if typ=='grass':
  operations=rule['effect']['spec']['operations']
  operations.append(dict(type='IF',amount=0,target='SELF',condition=condition('COUNTER_AT_LEAST',value='growth',amount=2),children=[op('REMOVE_COUNTER',2,counter='growth'),op('HEAL_LP',500)]))
 if rule['minLevel']==7 and typ=='grass':rule['effect']['spec']['triggers']=['ON_SUMMON','ON_SPECIAL_SUMMON'];rule['effect']['spec']['costs']=[]
 if rule['minLevel']==7 and typ=='ghost':
  rule['effect']['spec']['triggers']=['ON_SEND_GRAVE'];rule['effect']['target']='none';rule['effect']['spec']['targets']=dict(selector='SELF',min=0,max=1);rule['effect']['spec']['operations']=[op('RETURN_HAND'),op('HEAL_LP',200)]
 if rule['minLevel']==7 and typ=='steel':rule['effect']['spec']['triggers']=['CONTINUOUS'];rule['effect']['spec']['costs']=[];rule['effect']['oncePerTurn']=False
rules.insert(0,dict(types=[],minLevel=1,maxLevel=12,form=True,effect=effect([op('MODIFY_POWER',200,duration='NEXT_TURN_END'),op('PREVENT_DESTROY',duration='TURN_END')],triggers=['ON_SUMMON','ON_SPECIAL_SUMMON'])))
high={'counter_gate','reversal_seal','mind_theft','dark_current'}
for id in high:cards[id]['rarity']='Super Rare'
banner=dict(name='Duel Tactics',family='arcane_tactics',source='PACK',price=800,slots=5,hardPity=80,softPity=60,softBonus=3,starts=0,ends=9223372036854775807,pool=[dict(card=id,weight=6 if id in high else 12,featured=id in high,high=id in high) for id in cards])
all_costs=[c for r in rules for c in r['effect']['spec'].get('costs',[])] + [c for v in cards.values() for c in v['effect']['spec'].get('costs',[])]
monster_lp=sum(1 for r in rules if r.get('types') for c in r['effect']['spec'].get('costs',[]) if c['type']=='LP_COST')
total_lp=sum(1 for c in all_costs if c['type']=='LP_COST')
cost_kinds=sorted({c['type'] for c in all_costs})
assert monster_lp==0, monster_lp
assert total_lp==1, total_lp
assert len(cost_kinds)>=6, cost_kinds
effectEconomy=dict(policy='LP_COST is signature-only; monster tiers never pay LP by default',monsterLpCosts=monster_lp,totalLpCosts=total_lp,costKinds=cost_kinds)
(root/'deep_effects.json').write_text(json.dumps(dict(monsterRules=rules,cards=cards,banners={'duel_tactics':banner},effectEconomy=effectEconomy),ensure_ascii=False,indent=2)+'\n')
print('Authored',len(cards),'support compositions and',len(rules),'generic monster identities')
