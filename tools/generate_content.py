"""Author the first test set. Engine code never branches on these card identities."""
import json
from pathlib import Path

cards = {}
phases = ['DRAW', 'STANDBY', 'MAIN1', 'BATTLE', 'MAIN2', 'END']
def effect(op, amount=0, speed=1, cost=0, target='none', once=True):
    return dict(operation=op, amount=amount, speed=speed, lifeCost=cost, target=target,
                phases=phases if speed>1 else ['MAIN1','MAIN2'], oncePerTurn=once)
def card(id, name, category='pokemon', power=0, type='normal', parent='', extra=False,
         ability=None, sources=None, rarity='Common', text='', aspects=None):
    cards[id]=dict(id=id,name=name,category=category,species=id if category=='pokemon' else '',
      aspects=aspects or [],type=type,family=parent or id,evolvesFrom=parent,extra=extra,power=power,
      text=text,set='Kanto Crossroads',rarity=rarity,sources=sources or ['PACK','GACHA','STARTER'],effect=ability)
card('charmander','Charmander',power=1000,type='fire',text='A quick beginning for an evolution deck.')
card('charmeleon','Charmeleon',power=1700,type='fire',parent='charmander',text='Evolve Charmander. Retain its battle state.')
card('charizard','Charizard',power=2500,type='fire',parent='charmeleon',rarity='Rare',text='Evolve Charmeleon. A powerful finisher.')
card('mega_charizard','Mega Charizard X',power=3000,type='dragon',parent='charizard',extra=True,aspects=['mega','mega-x'],rarity='Super Rare',text='Extra Deck evolution of Charizard.')
cards['mega_charizard']['species']='charizard'
card('squirtle','Squirtle',power=1100,type='water',text='A resilient start for a water deck.')
card('wartortle','Wartortle',power=1800,type='water',parent='squirtle',text='Evolve Squirtle.')
card('blastoise','Blastoise',power=2600,type='water',parent='wartortle',rarity='Rare',text='Evolve Wartortle.')
card('bulbasaur','Bulbasaur',power=1100,type='grass',text='The first seed of an evolution strategy.')
card('ivysaur','Ivysaur',power=1800,type='grass',parent='bulbasaur',text='Evolve Bulbasaur.')
card('venusaur','Venusaur',power=2500,type='grass',parent='ivysaur',rarity='Rare',text='Evolve Ivysaur.')
card('pikachu','Pikachu',power=1300,type='electric',text='A flexible attacker against water decks.')
card('gastly','Gastly',power=900,type='ghost',ability=effect('draw',1,cost=400),text='Once each turn: pay 400 Life; draw 1 card.')
card('haunter','Haunter',power=1700,type='ghost',parent='gastly',text='Evolve Gastly.')
card('gengar','Gengar',power=2400,type='ghost',parent='haunter',rarity='Super Rare',ability=effect('negate_effect',speed=2,cost=600,target='chain'),text='Evolve Haunter. Once each turn: pay 600 Life; negate the latest chain effect.')
card('eevee','Eevee',power=1200,text='A flexible starter Pokemon.')
card('onix','Onix',power=1800,type='rock',sources=['NPC_DUEL'],text='Win a duel against the Pewter challenger.')
card('ancient_mew','Ancient Mew',power=1500,type='psychic',sources=['ARCHAEOLOGY'],rarity='Secret',text='Discover an ancient desert temple. Collector edition; no rarity power bonus.')
cards['ancient_mew']['species']='mew'
card('shadow_lugia','Shadow Lugia',power=2200,type='psychic',sources=['BLACK_MARKET'],aspects=['shadow'],rarity='Secret',text='Underground edition. Forbidden in Ranked.')
cards['shadow_lugia']['species']='lugia'
card('flamethrower','Flamethrower','technique',ability=effect('damage',600,cost=200),text='Pay 200 Life; deal 600 damage to the opposing Trainer.')
card('protect','Protect','reaction',ability=effect('negate_effect',speed=2,cost=200,target='chain'),text='Pay 200 Life; negate the latest chain effect.')
card('feint','Feint','reaction',ability=effect('negate_activation',speed=3,cost=300,target='chain'),rarity='Rare',text='Pay 300 Life; negate the latest activation. Costs remain paid.')
card('exit','Emergency Exit','reaction',ability=effect('return',speed=2,target='ally'),text='Return one of your Pokemon to your hand.')
card('potion','Potion','item',ability=effect('heal',500),text='Recover 500 Trainer Life.')
card('research','Professor’s Research','trainer',ability=effect('draw',2,cost=500),text='Pay 500 Life; draw 2 cards.')
card('revival','Revival Seed','item',ability=effect('revive',target='grave',cost=800),text='Pay 800 Life; summon a non-Extra Pokemon from your discard pile.')
card('lost_zone','Lost Zone','technique',ability=effect('banish',cost=600,target='enemy'),text='Pay 600 Life; banish an opposing Pokemon.')
card('rock_tomb','Rock Tomb','technique',ability=effect('destroy',cost=500,target='enemy'),text='Pay 500 Life; destroy an opposing Pokemon.')
card('hard_stone','Hard Stone','item',ability=effect('shield',1,target='ally'),text='Protect an ally from its next destruction.')
card('battle_training','Battle Training','trainer',ability=effect('boost',400,target='ally'),text='An ally gains 400 power until the next turn.')
card('training_ground','Training Ground','stadium',ability=effect('boost',200,target='ally'),text='Once each turn: an ally gains 200 power until the next turn. Remains in your Stadium zone.')
cards['training_ground']['modifiers']=[dict(zone='STADIUM',affectedType='any',power=100)]
cards['training_ground']['text']='Your Pokemon gain 100 power while this Stadium remains. Once each turn: an ally gains another 200 power until the next turn.'
cards['eevee']['triggers']=[dict(cause='PLAY',zone='FIELD',relation='self',effect=effect('heal',200))]
cards['eevee']['text']='When normally played: recover 200 Trainer Life. Opponents may respond.'
starter=['charmander','charmeleon','charizard','squirtle','wartortle','blastoise','bulbasaur','ivysaur','venusaur','pikachu','eevee','protect','potion','research']
starter=[x for x in starter for _ in range(3)][:40]
pool=[dict(card=c['id'],weight=8 if c['rarity']=='Super Rare' else 25 if c['rarity']=='Rare' else 100,featured=c['id']=='gengar',high=c['rarity']=='Super Rare') for c in cards.values() if 'PACK' in c['sources']]
data=dict(rules=dict(minDeck=40,maxDeck=60,extraDeck=15,copies=3,hand=5,life=8000,pokemonZones=5,supportZones=5,normalSummons=1,typeBonus=300,rankedLimits={'shadow_lugia':0},effective={'fire':['grass'],'water':['fire','rock'],'grass':['water','rock'],'electric':['water'],'ghost':['psychic'],'rock':['fire']}),cards=cards,
 banners={'crossroads':dict(name='Kanto Crossroads Booster',family='crossroads-pack',source='PACK',price=160,slots=8,hardPity=40,softPity=25,softBonus=10,starts=0,ends=0,pool=pool),
          'ghost_carnival':dict(name='Ghost Carnival',family='ghost',source='GACHA',price=100,slots=1,hardPity=20,softPity=12,softBonus=20,starts=0,ends=0,pool=pool)},
 rewards={'desert_discovery':dict(name='Ancient Sands',source='ARCHAEOLOGY',dimension='minecraft:overworld',biome='minecraft:desert',count=1,card='ancient_mew',supplyCap=100),
          'pewter_victory':dict(name='Pewter Challenger',source='NPC_DUEL',dimension='',biome='',count=1,card='onix',supplyCap=0,coins=100,dailyLimit=5)},
 dealers={'midnight':dict(name='Midnight Collector',dimension='minecraft:the_nether',startHour=23,endHour=2,reputation=1,requestedCard='gastly',count=3,offeredCard='shadow_lugia')},starters={'crossroads':starter})
p=Path('src/main/resources/data/svarcade_tcg/catalog.json');p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Wrote {len(cards)} cards, {len(starter)} starter instances')
