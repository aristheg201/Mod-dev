"""Explicit card designs. Costs/conditions/operations are authoritative, not display prose."""
import copy

def op(t,n=0,target='SELF',duration=None,**flags):
 return dict(type=t,amount=n,target=target,duration=duration,flags={k:str(v).lower() if isinstance(v,bool) else str(v) for k,v in flags.items()})
def cond(t,value='',n=0,target='SELF',children=None):return dict(type=t,target=target,value=value,amount=n,children=children or [])
def cost(t,n=1,target=None,counter=None,types=None):
 d=dict(type=t,amount=n,target=target,counter=counter)
 if types:d['filter']=dict(category='pokemon',types=types)
 return d

def when(c,ops,otherwise=None):return dict(type='IF',amount=0,target='SELF',condition=c,children=ops,otherwise=otherwise or [],flags={})
def later(ops,trigger='ON_TURN_END',duration='TURN_END'):
 return dict(type='DELAY',amount=0,target='SELF',duration=duration,flags=dict(trigger=trigger),children=ops)
def effect(ops,target='none',costs=None,conditions=None,speed=1,triggers=None):
 return dict(operation='composite',amount=0,speed=speed,lifeCost=0,target=target,phases=['MAIN1','MAIN2'] if speed==1 else ['DRAW','STANDBY','MAIN1','BATTLE','MAIN2','END'],oncePerTurn=True,
  spec=dict(triggers=triggers or ['ON_ACTIVATE'],conditions=conditions or [],costs=costs or [],targets=dict(selector='TARGET' if target!='none' else 'SELF',min=1 if target!='none' else 0,max=1),operations=ops,oncePerDuel=False,optional=False,stages=[],limitScope='CARD_NAME'))
def stage(id,e,zones=None,listen=False):return dict(id=id,effect=e,sourceZones=zones or ['FIELD'],listenAny=listen)
def design(name,primary,stages=None):
 primary=copy.deepcopy(primary);primary['spec']['stages']=stages or []
 return {'name':name,'effect':primary,'authorship':'explicit card design'}

D={
 'charmander':design('Ember',effect([op('MODIFY_POWER',200,duration='TURN_END'),op('PIERCE',duration='TURN_END')]),[stage('blaze',effect([op('MODIFY_POWER',250,duration='TURN_END')],conditions=[cond('LP_AT_MOST',n=2500)],speed=2))]),
 'charmeleon':design('Scorching Claws',effect([op('MODIFY_POWER',300,duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],'enemy',[cost('REVEAL_HAND',types=['fire'])])),
 'charizard':design('Flame Momentum',effect([op('DESTROY',target='TARGET'),op('ADD_COUNTER',1,counter='momentum',max=3),when(cond('COUNTER_AT_LEAST','momentum',2),[op('REMOVE_COUNTER',2,counter='momentum'),op('MODIFY_POWER',350,duration='TURN_END'),op('PIERCE',duration='TURN_END')])],'enemy',[cost('DISCARD',types=['fire'])])),
 'squirtle':design('Withdraw',effect([op('CHANGE_POSITION',position='DEFENSE'),op('PREVENT_DESTROY',duration='TURN_END')])),
 'wartortle':design('Tail Sweep',effect([op('CHANGE_POSITION',target='TARGET',position='DEFENSE'),op('PREVENT_DAMAGE',duration='TURN_END')],'enemy',[cost('REVEAL_HAND',types=['water'])])),
 'blastoise':design('Shell Fortress',effect([op('ADD_COUNTER',1,counter='shell',max=3),op('PREVENT_DAMAGE',duration='TURN_END')]),[stage('hydro_cannon',effect([op('DESTROY',target='TARGET'),op('CANNOT_ATTACK',duration='NEXT_TURN_END')],'enemy',[cost('COUNTER_COST',2,counter='shell')],conditions=[cond('POSITION','DEFENSE',target='TARGET')]))]),
 'bulbasaur':design('Seed Nursery',effect([op('ADD_COUNTER',1,counter='growth',max=3),when(cond('COUNTER_AT_LEAST','growth',2),[op('REMOVE_COUNTER',2,counter='growth'),op('SEARCH',1,'DECK',reveal=True)])])),
 'ivysaur':design('Vine Snare',effect([op('CANNOT_ATTACK',target='TARGET',duration='TURN_END'),op('ADD_COUNTER',1,counter='growth',max=3)],'enemy',[cost('REVEAL_HAND',types=['grass'])])),
 'venusaur':design('Leech Garden',effect([op('REMEMBER',target='TARGET',memory='seeded'),later([when(cond('SURVIVED',target='REMEMBERED'),[op('DAMAGE_LP',300,recipient='ENEMY'),op('HEAL_LP',300),op('ADD_COUNTER',1,counter='growth',max=3)])])],'enemy'),[stage('regrowth',effect([dict(op('REVIVE',target='GRAVEYARD'),filter=dict(category='pokemon',types=['grass']))],costs=[cost('COUNTER_COST',2,counter='growth')]))]),
 'pikachu':design('Charge',effect([op('ADD_COUNTER',1,counter='charge',max=3),op('PREVENT_TARGET',duration='TURN_END')]),[stage('thunderbolt',effect([op('CANNOT_ATTACK',target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],'enemy',[cost('COUNTER_COST',2,counter='charge')],speed=2))]),
 'gastly':design('Night Veil',effect([op('SET_FACE_DOWN',target='TARGET'),op('PREVENT_TARGET',duration='TURN_END')],'enemy',[cost('BANISH_GRAVE',types=['ghost'])])),
 'haunter':design('Dream Eater',effect([op('DAMAGE_LP',400,recipient='ENEMY'),op('HEAL_LP',400)],conditions=[cond('POSITION','FACE_DOWN_DEFENSE',target='ENEMY_MONSTER')])),
 'gengar':design('Cursed Escape',effect([op('BANISH',target='TARGET'),op('RETURN_HAND')],'enemy',[cost('BANISH_GRAVE',types=['ghost'])]),[stage('haunt',effect([op('RETURN_HAND')],triggers=['ON_SEND_GRAVE']),['DISCARD'])]),
 'eevee':design('Evolution Guide',effect([dict(op('SEARCH',1,'DECK',reveal=True),filter=dict(category='pokemon',tags=['family:cobblemon:eevee'])),op('REVEAL',target='HAND')])),
 'onix':design('Rock Bind',effect([op('CHANGE_POSITION',target='TARGET',position='DEFENSE'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END'),op('CHANGE_POSITION',position='DEFENSE')],'enemy',[cost('REVEAL_HAND',types=['rock'])])),
 'lapras':design('Healing Aria',effect([op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL'),op('SET_FACE_DOWN',target='TARGET')],'enemy',[cost('REVEAL_HAND',types=['water'])])),
 'cloyster':design('Shell Smash',effect([op('REMOVE_STATUS',status='PREVENT_DESTROY'),op('EXTRA_ATTACK',2),op('CANNOT_ACTIVATE',duration='NEXT_TURN_END')],costs=[cost('DISCARD',types=['water'])])),
 'gyarados':design('Rampage',effect([op('DESTROY',target='TARGET'),op('CANNOT_ACTIVATE',duration='NEXT_TURN_END')],'enemy',[cost('DISCARD',types=['water'])])),
 'feraligatr':design('Crushing Jaws',effect([op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'),op('MODIFY_POWER',300,duration='TURN_END'),op('PIERCE',duration='TURN_END')],'enemy',[cost('REVEAL_HAND',types=['water'])])),
 'swampert':design('Mud Bank',effect([op('CHANGE_POSITION',target='ALL_ENEMIES',position='DEFENSE'),op('PREVENT_DESTROY',duration='TURN_END')],costs=[cost('BANISH_GRAVE',types=['ground'])])),
 'empoleon':design('Imperial Guard',effect([op('PREVENT_TARGET',target='TARGET',duration='TURN_END'),op('PREVENT_DAMAGE',duration='TURN_END')],'ally',conditions=[cond('TYPE','water',target='TARGET')])),
 'greninja':design('Water Shuriken',effect([op('MODIFY_POWER',-350,'TARGET','TURN_END'),op('RETURN_HAND')],'enemy'),[stage('substitute',effect([op('PREVENT_TARGET',duration='TURN_END')],costs=[cost('DISCARD')],speed=2))]),
 'inteleon':design('Snipe Shot',effect([op('INSPECT_SET',target='ENEMY_CARD'),when(cond('FACE_DOWN',target='TARGET'),[op('BANISH',target='TARGET')],[op('MODIFY_POWER',-400,'TARGET','TURN_END')])],'enemy',[cost('REVEAL_HAND',types=['water'])])),
 'quagsire':design('Unaware',effect([op('SWAP_POWER',target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')],'enemy',[cost('BANISH_GRAVE',types=['ground'])])),
 'milotic':design('Restoring Scales',effect([op('HEAL_LP',800),op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL')],conditions=[cond('LP_AT_MOST',n=2500)]),[stage('coil',effect([op('PREVENT_DESTROY',duration='TURN_END'),op('MODIFY_POWER',200,duration='TURN_END')],costs=[cost('REVEAL_HAND',types=['water'])]))]),
 'suicune':design('Purifying Mist',effect([op('PREVENT_DAMAGE',target='TARGET',duration='NEXT_TURN_END'),op('PREVENT_TARGET',target='TARGET',duration='NEXT_TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')],'ally')),
 'kyogre':design('Origin Tide',effect([op('RETURN_DECK',target='ALL_ENEMIES',placement='BOTTOM'),op('CANNOT_ATTACK',target='ALL_ALLIES',duration='TURN_END')],costs=[cost('TRIBUTE',types=['water']),cost('DISCARD',types=['water'])])),
 'ditto':design('Transform',effect([op('COPY_EFFECT',target='TARGET',duration='NEXT_TURN_END'),op('SWAP_POWER',target='TARGET',duration='NEXT_TURN_END')],'enemy')),
 'smeargle':design('Sketch',effect([op('COPY_EFFECT',target='TARGET',duration='PERMANENT'),op('DRAW',1)],'enemy',[cost('DISCARD')])),
 'wobbuffet':design('Patient Counter',effect([op('CHANGE_POSITION',position='DEFENSE'),op('REFLECT_DAMAGE',duration='TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')]),[stage('destiny_bond',effect([op('REMEMBER',target='TARGET'),op('SEND_GRAVE'),op('DESTROY',target='REMEMBERED')],'enemy',speed=2))]),
 'magikarp':design('Surprising Splash',effect([op('ADD_COUNTER',1,counter='splash',max=3),when(cond('COUNTER_AT_LEAST','splash',3),[op('REMOVE_COUNTER',3,counter='splash'),dict(op('SEARCH',1,'DECK',reveal=True),filter=dict(category='pokemon',tags=['family:cobblemon:magikarp']))])])),
}
# Unown's glyphs are authored psychic sigils; cosmetic letters never count as uniqueness.
GLYPHS={
 'a':('Archive',[op('LOOK_TOP_DECK',3),op('REORDER_DECK',3,order='POWER_DESC')]),
 'b':('Barrier',[op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END')]),
 'c':('Cycle',[op('RETURN_DECK',target='GRAVEYARD',placement='BOTTOM'),op('DRAW',1)]),
 'd':('Dispel',[op('REMOVE_STATUS',target='ALL_FIELD',status='ALL')]),
 'e':('Exchange',[op('SWAP_POWER',target='TARGET',duration='TURN_END')]),
 'f':('Fetter',[op('CANNOT_ATTACK',target='TARGET',duration='NEXT_TURN_END')]),
 'g':('Gate',[op('SUMMON_FROM_HAND',target='HAND'),op('CANNOT_ATTACK',target='ALLY_MONSTER',duration='TURN_END')]),
 'h':('Haven',[op('REMOVE_STATUS',target='ALL_ALLIES',status='ALL'),op('HEAL_LP',300)]),
 'i':('Insight',[op('INSPECT_SET',target='ENEMY_CARD'),op('REVEAL',target='RANDOM_HAND',controller='ENEMY')]),
 'j':('Judgement',[op('DESTROY',target='TARGET'),op('SEND_GRAVE')]),
 'k':('Key',[op('REMOVE_STATUS',target='TARGET',status='PREVENT_TARGET')]),
 'l':('Lantern',[op('FLIP_FACE_UP',target='TARGET'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')]),
 'm':('Memory',[op('COPY_EFFECT',target='TARGET',duration='TURN_END'),op('CANNOT_ATTACK',duration='TURN_END')]),
 'n':('Night',[op('SET_FACE_DOWN',target='TARGET'),op('CHANGE_POSITION',position='DEFENSE')]),
 'o':('Orbit',[op('RETURN_HAND',target='TARGET'),op('RETURN_DECK',placement='BOTTOM')]),
 'p':('Pulse',[op('DAMAGE_LP',250,recipient='ENEMY'),op('HEAL_LP',250)]),
 'q':('Quiet',[op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'),op('PREVENT_TARGET',duration='TURN_END')]),
 'r':('Recall',[op('RETURN_HAND',target='GRAVEYARD'),op('DISCARD',target='HAND')]),
 's':('Seal',[op('PREVENT_DESTROY',target='TARGET',duration='TURN_END'),op('CANNOT_ATTACK',target='TARGET',duration='TURN_END')]),
 't':('Tether',[op('CONTROL_CHANGE',target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END')]),
 'u':('Undo',[op('RETURN_DECK',target='TARGET',placement='TOP')]),
 'v':('Veil',[op('PREVENT_TARGET',target='ALL_ALLIES',duration='TURN_END')]),
 'w':('Wake',[op('REVIVE',target='GRAVEYARD'),op('SEND_GRAVE')]),
 'x':('Exile',[op('BANISH',target='TARGET'),op('RETURN_HAND')]),
 'y':('Yield',[op('DRAW',2),op('RETURN_DECK',target='HAND',placement='BOTTOM')]),
 'z':('Zenith',[op('EXTRA_ATTACK',1,target='ALLY_MONSTER'),op('CANNOT_ATTACK',duration='TURN_END')]),
 '!':('Release',[op('MODIFY_POWER',300,'ALL_ALLIES','TURN_END'),op('SEND_GRAVE')]),
 '?':('Riddle',[op('LOOK_TOP_DECK',2),op('REORDER_DECK',2,order='REVERSE'),op('DRAW',1)]),
}
for glyph,(name,ops) in GLYPHS.items():
 target='enemy' if any(o.get('target')=='TARGET' for o in ops) else 'none'
 # Ally protection must select a friendly creature, not an opponent.
 if glyph=='s':target='ally'
 D['unown|character-'+glyph]=design('Sigil of '+name,effect(ops,target,costs=[cost('REVEAL_HAND',types=['psychic'])]))
D['unown']=design('Runic Assembly',effect([op('LOOK_TOP_DECK',2),when(cond('COUNT_AT_LEAST',n=2,target='ALLY_MONSTER'),[op('DRAW',1)])]))
# Existing extra-deck identities remain ordinary executable card definitions.
D.update({
 'mega_charizard':design('Inferno Dive',effect([op('BANISH',target='TARGET'),op('EXTRA_ATTACK',1),op('CANNOT_ACTIVATE',duration='NEXT_TURN_END')],'enemy',[cost('BANISH_GRAVE',types=['fire'])])),
 'ancient_mew':design('Ancient Memory',effect([op('LOOK_TOP_DECK',3),op('REORDER_DECK',3,order='POWER_ASC'),op('DRAW',1)],costs=[cost('REVEAL_HAND',types=['psychic'])])),
 'shadow_lugia':design('Shadow Tempest',effect([op('RETURN_DECK',target='ALL_ENEMIES',placement='BOTTOM'),op('DISCARD',target='HAND')],costs=[cost('BANISH_GRAVE',types=['dark'])])),
 'arceus_defense':design('Divine Sanctuary',effect([op('CHANGE_POSITION',position='DEFENSE'),op('PREVENT_DESTROY',target='ALL_ALLIES',duration='TURN_END')],costs=[cost('REVEAL_HAND',types=['normal'])])),
 'arceus_judgement':design('Judgement',effect([op('BANISH',target='TARGET'),op('CANNOT_ATTACK',duration='TURN_END')],'enemy',[cost('BANISH_GRAVE')])),
 'ultimate_arceus':design('Final Judgement',effect([op('DESTROY',target='ALL_ENEMIES'),op('CANNOT_ACTIVATE',duration='NEXT_TURN_END')],costs=[cost('DISCARD'),cost('TRIBUTE')])),
 'training_ground':design('Training Formation',effect([op('MODIFY_POWER',200,'ALL_ALLIES','TURN_END'),op('CHANGE_POSITION',target='TARGET',position='DEFENSE')],'ally')),
 'mirror_barrier':design('Mirror Barrier',effect([op('NEGATE_EFFECT'),op('PREVENT_DAMAGE',target='ALL_ALLIES',duration='TURN_END')],target='chain',costs=[cost('DISCARD')],speed=2,conditions=[cond('CHAIN_AT_LEAST',n=1)])),
 'counter_seal':design('Counter Seal',effect([op('NEGATE_ACTIVATION'),op('BANISH',target='CHAIN_SOURCE')],target='chain',costs=[cost('DISCARD')],speed=3,conditions=[cond('CHAIN_AT_LEAST',n=1)])),
})
# Chain targets are link sources; the selected input remains the canonical link number.
for key in ('mirror_barrier','counter_seal'):
 D[key]['effect']['spec']['targets']={'selector':'CHAIN_SOURCE','min':0,'max':1}
D.update({
 'palkia':design('Spacial Rend',effect([op('RETURN_HAND',target='TARGET'),op('SUMMON_FROM_HAND',target='HAND')],'enemy',[cost('REVEAL_HAND',types=['dragon'])])),
 'dialga':design('Roar of Time',effect([op('REMEMBER',target='TARGET'),later([op('RETURN_DECK',target='REMEMBERED',placement='BOTTOM')],duration='NEXT_TURN_END'),op('CANNOT_ATTACK',duration='NEXT_TURN_END')],'enemy',[cost('DISCARD',types=['dragon'])])),
 'giratina':design('Distortion Gate',effect([op('BANISH'),dict(op('REVIVE',target='GRAVEYARD'),filter=dict(category='pokemon',types=['ghost']))],costs=[cost('BANISH_GRAVE',types=['ghost'])]),[stage('shadow_return',effect([op('RETURN_HAND'),op('RETURN_DECK',target='ENEMY_MONSTER',placement='BOTTOM')]),['BANISHED'])]),
 'mewtwo':design('Psystrike',effect([op('SWAP_POWER',target='TARGET',duration='TURN_END'),op('CANNOT_ACTIVATE',target='TARGET',duration='TURN_END'),op('DRAW',1)],'enemy',[cost('REVEAL_HAND',types=['psychic'])])),
 'rayquaza':design('Dragon Ascent',effect([op('MODIFY_POWER',500,duration='TURN_END'),op('PIERCE',duration='TURN_END'),op('EXTRA_ATTACK',1),op('CANNOT_ACTIVATE',duration='NEXT_TURN_END')],costs=[cost('TRIBUTE')])),
})
