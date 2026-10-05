"""Original voxel furniture models, normal blockstates/items/recipes, resource-pack replaceable."""
from pathlib import Path
import json
root=Path('src/main/resources')
def write(path,value):
 p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def box(a,b,texture='wood'):
 return {'from':a,'to':b,'faces':{side:{'texture':'#'+texture} for side in ['north','south','east','west','up','down']}}
textures={'wood':'minecraft:block/oak_planks','frame':'minecraft:block/dark_oak_planks','cloth':'minecraft:block/red_wool','metal':'minecraft:block/iron_block','books':'minecraft:block/bookshelf','particle':'minecraft:block/oak_planks'}
models={}
legs=lambda top:[box([x,0,z],[x+2,top,z+2],'frame') for x in [2,12] for z in [2,12]]
models['medieval_chair']=legs(7)+[box([1,7,1],[15,9,15]),box([3,9,3],[13,10,12],'cloth'),box([1,9,13],[3,22,15],'frame'),box([13,9,13],[15,22,15],'frame'),box([3,17,13],[13,22,15]),box([3,12,13],[13,14,15])]
models['medieval_table']=legs(12)+[box([0,12,0],[16,14,16]),box([2,3,7],[14,5,9],'frame'),box([7,3,2],[9,5,14],'frame')]
models['medieval_cabinet']=[box([0,0,0],[16,2,16],'frame'),box([0,14,0],[16,16,16],'frame'),box([0,2,0],[2,14,16],'frame'),box([14,2,0],[16,14,16],'frame'),box([2,2,14],[14,14,16]),box([2,2,1],[7.5,14,3]),box([8.5,2,1],[14,14,3]),box([6,7,0],[7,9,1],'metal'),box([9,7,0],[10,9,1],'metal')]
models['medieval_bookshelf']=[box([0,0,0],[2,16,16],'frame'),box([14,0,0],[16,16,16],'frame'),box([2,0,0],[14,2,16]),box([2,14,0],[14,16,16]),box([2,7,0],[14,9,16]),box([2,2,12],[14,14,16]),box([2,2,2],[14,7,12],'books'),box([2,9,2],[14,14,12],'books')]
models['medieval_counter']=[box([0,0,0],[16,2,16],'frame'),box([0,2,1],[16,14,15]),box([0,14,0],[16,16,16],'frame'),box([1,3,0],[3,13,1],'frame'),box([13,3,0],[15,13,1],'frame')]
for name,elements in models.items():
 model={'textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.6,.6,.6]},'fixed':{'rotation':[0,180,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]}}}
 write(Path('assets/worldcomesalive/models/block')/(name+'.json'),model)
 write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name})
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':{'facing='+side:{'model':'worldcomesalive:block/'+name,**({'y':angle} if angle else {})} for side,angle in [('north',0),('east',90),('south',180),('west',270)]}})
 write(Path('data/worldcomesalive/loot_table/blocks')/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'worldcomesalive:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 patterns={'medieval_chair':['P P','PPP','S S'],'medieval_table':['PPP','S S','S S'],'medieval_cabinet':['PPP','P P','PPP'],'medieval_bookshelf':['PPP','PBP','PPP'],'medieval_counter':['PPP','PSP','PPP']}
 keys={'P':{'tag':'minecraft:planks'}}
 if any('S' in row for row in patterns[name]):keys['S']={'item':'minecraft:stick'}
 if any('B' in row for row in patterns[name]):keys['B']={'item':'minecraft:book'}
 write(Path('data/worldcomesalive/recipe')/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'building','pattern':patterns[name],'key':keys,'result':{'id':'worldcomesalive:'+name,'count':1}})
write(Path('assets/worldcomesalive/lang/en_us.json'),{'block.worldcomesalive.'+n:'Medieval '+n.removeprefix('medieval_').replace('_',' ').title() for n in models})
