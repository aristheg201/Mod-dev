import json,math
from pathlib import Path
from PIL import Image
here=Path(__file__).resolve().parent
root=here/'resources/data/svarcade_tcg';root.mkdir(parents=True,exist_ok=True)
aliases={
 'IDLE':['idle'],'SPAWN':['spawn','sendout','cry'],'CRY':['cry'],
 'ATTACK_PHYSICAL':['physical','attack_physical','attack','melee','cry'],
 'ATTACK_SPECIAL':['special','attack_special','cast','cry'],'CAST_STATUS':['status','cast','special','cry'],
 'CHARGE':['charge','special','status','cry'],'PROJECTILE_CAST':['special','cast','cry'],
 'DASH':['dash','physical','run','cry'],'HIT':['recoil','hit','hurt','cry'],'HEAVY_HIT':['recoil','hurt','cry'],
 'FAINT':['faint'],'TRANSFORM':['transform','evolve','cry'],'EVOLVE':['evolve','transform','cry'],
 'VICTORY':['victory','happy','cry'],
}
(root/'animation_semantics.json').write_text(json.dumps(aliases,indent=2)+'\n')
profiles={}
defaults={
 'normal':('end_rod','RING',0xE8D59F),'fire':('flame','TRAIL',0xFC8D42),
 'water':('splash','RING',0x5EC9F7),'electric':('electric_spark','HELIX',0xFFE563),
 'grass':('happy_villager','SPIRAL',0x75E88C),'ice':('snowflake','BURST',0xB1EAF7),
 'psychic':('witch','RING',0xDDA4FF),'ghost':('soul','VORTEX',0x79D6E1),
 'dark':('smoke','VORTEX',0x9476BB),'fairy':('glow','ORB',0xF6AED4),
 'steel':('scrape','IMPACT_CONE',0xCCD8E0),'dragon':('dragon_breath','HELIX',0x917DFF),
 'fighting':('crit','IMPACT_CONE',0xF3AF75),'poison':('witch','SPIRAL',0xBE81E0),
 'ground':('cloud','SHOCKWAVE',0xD5AF79),'rock':('crit','BURST',0xC9BEA7),
 'bug':('happy_villager','ARC',0xBDDA75),'flying':('cloud','TRAIL',0xCDEBFA),
}
for typ,(particle,shape,color) in defaults.items():
 profiles[typ]=dict(particle='minecraft:'+particle,fallback='minecraft:end_rod',emitter='cobblemon:impact_'+typ,shape=shape,color=color,maxParticles=96,maxLifetime=2400,spawnRate=60,maxTrailSegments=48,maxConcurrentInstances=18,distanceCull=72,sound='minecraft:block.amethyst_block.chime')
(root/'duel_vfx_profiles.json').write_text(json.dumps(profiles,indent=2)+'\n')
actions={}
for typ in defaults:
 mode='MELEE' if typ in ('normal','fire','steel','fighting','rock','flying','dark') else 'BEAM' if typ in ('ice','dragon') else 'AOE' if typ=='ground' else 'PROJECTILE'
 actions[typ]=dict(mode=mode,profile=typ,duration=1400,animation=dict(semantic='ATTACK_PHYSICAL' if mode=='MELEE' else 'ATTACK_SPECIAL',windup=.25,release=.46,impact=.72,recovery=.9))
(root/'action_defaults.json').write_text(json.dumps(actions,indent=2)+'\n')
image=Image.new('RGBA',(64,64))
for y in range(64):
 for x in range(64):
  r=math.hypot((x-31.5)/31.5,(y-31.5)/31.5)
  alpha=int(255*max(0,1-r)**.65)
  image.putpixel((x,y),(255,255,255,alpha))
path=here/'resources/assets/svarcade_tcg/textures/duel/vfx.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
