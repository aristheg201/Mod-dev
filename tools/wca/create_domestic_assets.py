"""Original cuboid props and dish/container models, with replaceable vanilla texture references."""
from pathlib import Path
import json,runpy
ns=runpy.run_path('tools/wca/create_furniture_assets.py');write=ns['write'];box=ns['box'];textures=ns['textures'];base=ns['models']
textures.update(pottery='minecraft:block/terracotta',drink='minecraft:block/brown_terracotta',wine='minecraft:block/red_terracotta',water='minecraft:block/light_blue_terracotta',food='minecraft:block/orange_terracotta',leaf='minecraft:block/green_wool',paper='minecraft:block/white_wool')
def mug(liquid='drink',goblet=False,bottle=False):
 if goblet:return [box([5,0,5],[11,1,11],'metal'),box([7,1,7],[9,6,9],'metal'),box([4,6,4],[12,7,12],'metal'),box([4,7,4],[5,12,12],'metal'),box([11,7,4],[12,12,12],'metal'),box([5,7,4],[11,12,5],'metal'),box([5,7,11],[11,12,12],'metal'),box([5,10,5],[11,10.5,11],liquid)]
 if bottle:return [box([4,0,4],[12,10,12],liquid),box([6,10,6],[10,15,10],liquid),box([6,15,6],[10,16,10],'wood')]
 return [box([4,0,4],[12,1,12]),box([4,1,4],[5,10,12]),box([11,1,4],[12,10,12]),box([5,1,4],[11,10,5]),box([5,1,11],[11,10,12]),box([12,3,5],[15,4,11],'frame'),box([14,4,5],[15,8,11],'frame'),box([12,8,5],[15,9,11],'frame'),box([5,8,5],[11,8.5,11],liquid)]
def plate(food=True):
 result=[box([2,0,2],[14,1,14],'pottery')]
 if food:result+=[box([4,1,4],[12,3,12],'food'),box([6,3,5],[9,4,9],'wood')]
 return result
def bowl(food=True):
 result=[box([4,0,4],[12,1,12],'pottery')]+[box(a,b,'pottery') for a,b in [([3,1,3],[5,5,13]),([11,1,3],[13,5,13]),([5,1,3],[11,5,5]),([5,1,11],[11,5,13])]]
 if food:result += [box([5,3,5],[11,4,11],'food')]
 return result
models={
 'wooden_stool':ns['legs'](7)+[box([1,7,1],[15,9,15])],
 'wooden_bench':[box([1,0,3],[3,7,13],'frame'),box([13,0,3],[15,7,13],'frame'),box([0,7,2],[16,9,14])],
 'writing_desk':base['medieval_table']+[box([0,9,10],[16,12,16],'frame')],
 'cooking_pot':bowl(False)+[box([2,5,2],[14,6,14],'metal'),box([7,6,7],[9,8,9],'metal')],
 'bottle_shelf':[box([0,0,2],[16,2,15])]+[box([x,2,7],[x+3,10,10],'wine') for x in [1,6,11]],
 'mug_rack':[box([0,0,2],[16,2,15])]+[box([x,2,5],[x+3,7,9]) for x in [1,6,11]],
 'book_stack':[box([2,0,3],[14,2,12],'cloth'),box([3,2,2],[13,4,13],'paper'),box([2,4,4],[14,6,12],'leaf')],
 'flower_vase':[box([5,0,5],[11,6,11],'pottery'),box([7,6,7],[9,12,9],'leaf'),box([5,11,5],[11,14,11],'cloth')]
}
for name,elements in models.items():
 write(Path('assets/worldcomesalive/models/block')/(name+'.json'),{'textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'scale':[.65,.65,.65]}}})
 write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name})
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':{'facing='+f:{'model':'worldcomesalive:block/'+name,'y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
 write(Path('data/worldcomesalive/loot_table/blocks')/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'worldcomesalive:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 write(Path('data/worldcomesalive/recipe')/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'building','pattern':['PP','SS'],'key':{'P':{'tag':'minecraft:planks'},'S':{'item':'minecraft:stick'}},'result':{'id':'worldcomesalive:'+name,'count':1}})
# Settings are complete reusable table meshes: dishes, utensil, food and appropriate container.
for course in ['meal','drink','both','dirty']:
 elements=[]
 if course in ['meal','both','dirty']:
  for e in bowl(course!='dirty'):
   e={**e,'from':[e['from'][0]*.6+1,e['from'][1]*.6-2,e['from'][2]*.6+2],'to':[e['to'][0]*.6+1,e['to'][1]*.6-2,e['to'][2]*.6+2]};elements.append(e)
  elements += [box([12,-2,2],[12.5,-1.5,10],'metal'),box([11.5,-2,10],[13,-1.5,12],'metal')]
 if course in ['drink','both']:
  for e in mug():elements.append({**e,'from':[e['from'][0]*.45+7,e['from'][1]*.55-2,e['from'][2]*.45+7],'to':[e['to'][0]*.45+7,e['to'][1]*.55-2,e['to'][2]*.45+7]})
 write(Path('assets/worldcomesalive/models/block')/('setting_'+course+'.json'),{'textures':textures,'elements':elements})
write(Path('assets/worldcomesalive/blockstates/table_setting.json'),{'variants':{'course='+c:{'model':'worldcomesalive:block/setting_'+c} for c in ['meal','drink','both','dirty']}})
catalog=json.loads(Path('src/main/resources/data/worldcomesalive/living_world/domestic.json').read_text())
lang=json.loads(Path('src/main/resources/assets/worldcomesalive/lang/en_us.json').read_text())
for name in models:lang['block.worldcomesalive.'+name]=name.replace('_',' ').title()
for id,g in catalog['goods'].items():
 name=id.split(':')[1];lang['item.worldcomesalive.'+name]=g['name']
 container=g['container'].split(':')[-1]
 if g['category']=='drink' or name in ['cup','wooden_mug','tankard','goblet','wine_glass','bottle','jug']:
  liquid='wine' if 'wine' in name else 'water' if name in ['water_jug','milk_cup'] else 'drink'
  elements=mug(liquid,goblet=container in ['goblet','wine_glass'] or name in ['goblet','wine_glass'],bottle=container in ['bottle','jug'] or name in ['bottle','jug'])
 elif container=='bowl' or name in ['bowl','cooking_pot']:elements=bowl(g['nutrition']>0)
 elif container=='plate' or name in ['plate','serving_tray']:elements=plate(g['nutrition']>0)
 elif name in ['fork','spoon','knife']:elements=[box([7,0,0],[9,1,12],'metal'),box([5,0,12],[11,1,16],'metal')]
 elif name=='pan':elements=[box([2,0,2],[12,2,12],'metal'),box([12,0,6],[16,1,8],'frame')]
 else:elements=[box([3,0,3],[13,5,13],'food'),box([4,5,4],[12,6,12],'wood')]
 write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'textures':textures,'elements':elements,'display':{'gui':{'rotation':[25,225,0],'translation':[0,-2,0],'scale':[.8,.8,.8]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.6,.6,.6]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1,3,1],'scale':[.6,.6,.6]}}})
write(Path('assets/worldcomesalive/lang/en_us.json'),lang)
for tag,values in {'sittable':['medieval_chair','wooden_stool','wooden_bench'],'dining_surface':['medieval_table','writing_desk'],'food_storage':['medieval_cabinet'],'drink_storage':['medieval_cabinet','bottle_shelf'],'cooking_station':['cooking_pot'],'reading_place':['medieval_bookshelf','writing_desk'],'sleeping_place':['#minecraft:beds']}.items():
 write(Path('data/worldcomesalive/tags/block')/(tag+'.json'),{'replace':False,'values':[v if v.startswith('#') else 'worldcomesalive:'+v for v in values]})

# Regional child models replace textures while retaining original geometry and capabilities.
for name in list(base)+list(models):
 variants={}
 for wood in ['oak','spruce','dark_oak','acacia','mangrove']:
  model=name if wood=='oak' else name+'_'+wood
  if wood!='oak':write(Path('assets/worldcomesalive/models/block')/(model+'.json'),{'parent':'worldcomesalive:block/'+name,'textures':{'wood':'minecraft:block/'+wood+'_planks','particle':'minecraft:block/'+wood+'_planks'}})
  for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing+',wood='+wood]={'model':'worldcomesalive:block/'+model,'y':rotation}
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':variants})
# Additional families reuse capabilities, not costly ticking BlockEntities.
extra={
'dining_chair':base['medieval_chair'],'tavern_stool':models['wooden_stool'],
'armchair':base['medieval_chair']+[box([0,9,1],[2,15,13]),box([14,9,1],[16,15,13])],
'throne':base['medieval_chair']+[box([0,20,12],[16,28,16],'metal')],
'outdoor_bench':models['wooden_bench']+[box([0,9,12],[16,20,14])],
'small_table':base['medieval_table'],'dining_table':base['medieval_table'],'long_tavern_table':base['medieval_table'],
'work_table':base['medieval_table']+[box([3,3,3],[13,8,13],'frame')],
'bedside_table':[box([2,0,2],[4,9,4]),box([12,0,12],[14,9,14]),box([1,9,1],[15,11,15])],
'cabinet':base['medieval_cabinet'],'cupboard':base['medieval_cabinet'],'wardrobe':base['medieval_cabinet'],
'dresser':base['medieval_cabinet']+[box([2,5,0],[14,6,1],'frame'),box([2,10,0],[14,11,1],'frame')],
'shelf':models['bottle_shelf'][:1],'food_pantry':base['medieval_cabinet'],'wine_rack':models['bottle_shelf'],
'barrel_rack':base['medieval_bookshelf'],'crate':base['medieval_counter'],'storage_chest':base['medieval_cabinet'],
'bar_counter':base['medieval_counter'],'serving_counter':base['medieval_counter'],'preparation_counter':base['medieval_counter'],
'keg':[box([1,1,1],[15,15,15]),box([0,3,0],[16,4,16],'metal'),box([0,12,0],[16,13,16],'metal'),box([7,5,0],[9,7,2],'metal')],
'chopping_board':[box([2,0,2],[14,1,14]),box([7,1,3],[8,1.5,12],'metal')],
'pan':[box([2,0,2],[12,2,12],'metal'),box([12,0,6],[16,1,8],'frame')],
'oven_hearth':[box([0,0,0],[16,16,16],'frame'),box([3,2,-.1],[13,10,.2],'metal')],
'drying_rack':base['medieval_bookshelf'],'ingredient_shelf':models['bottle_shelf'],
'rug':[box([0,0,0],[16,.3,16],'cloth')],
'candle_holder':[box([4,0,4],[12,1,12],'metal'),box([7,1,7],[9,5,9],'metal'),box([6,5,6],[10,11,10],'paper')],
'wall_shelf':models['bottle_shelf'][:1],
'coat_rack':[box([3,0,3],[13,1,13]),box([7,1,7],[9,23,9]),box([2,18,7],[14,20,9])],
'tapestry':[box([0,0,14],[16,24,15],'cloth')]
}
seats=['dining_chair','tavern_stool','armchair','throne','outdoor_bench']
storage=['cabinet','cupboard','wardrobe','dresser','food_pantry','crate','storage_chest']
tables=['small_table','dining_table','long_tavern_table','work_table','bedside_table']
for name,elements in extra.items():
 variants={}
 for wood in ['oak','spruce','dark_oak','acacia','mangrove']:
  child=name if wood=='oak' else name+'_'+wood
  model={'textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'scale':[.6,.6,.6]}}} if wood=='oak' else {'parent':'worldcomesalive:block/'+name,'textures':{'wood':'minecraft:block/'+wood+'_planks','particle':'minecraft:block/'+wood+'_planks'}}
  write(Path('assets/worldcomesalive/models/block')/(child+'.json'),model)
  for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing+',wood='+wood]={'model':'worldcomesalive:block/'+child,'y':rot}
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':variants})
 write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name})
 write(Path('data/worldcomesalive/loot_table/blocks')/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'worldcomesalive:'+name}]}]})
 write(Path('data/worldcomesalive/recipe')/(name+'.json'),{'type':'minecraft:crafting_shaped','category':'building','pattern':['PP','SS'],'key':{'P':{'tag':'minecraft:planks'},'S':{'item':'minecraft:stick'}},'result':{'id':'worldcomesalive:'+name,'count':1}})
 lang['block.worldcomesalive.'+name]=name.replace('_',' ').title()
for tag,entries in {'sittable':seats,'dining_surface':tables,'food_storage':storage,'drink_storage':['keg','wine_rack','barrel_rack'],'cooking_station':['oven_hearth','preparation_counter','pan'],'reading_place':['work_table']}.items():
 path=Path('data/worldcomesalive/tags/block')/(tag+'.json');value=json.loads((Path('src/main/resources')/path).read_text());value['values']+=['worldcomesalive:'+x for x in entries];write(path,value)
write(Path('assets/worldcomesalive/lang/en_us.json'),lang)
Path('tools/wca/furniture_catalog.json').write_text(json.dumps(list(extra),indent=2)+'\n')
# Three-dimensional composed clutter: one baked block per coherent cluster.
props={
'idle_tableware':plate(False)+[box([12,0,2],[12.5,.5,12],'metal')]+[{**e,'from':[e['from'][0]*.45+6,e['from'][1]*.5,e['from'][2]*.45+6],'to':[e['to'][0]*.45+6,e['to'][1]*.5,e['to'][2]*.45+6]} for e in mug()],
'folded_cloth':[box([2,0,2],[14,2,14],'cloth'),box([3,2,3],[13,4,13],'paper')],
'personal_belongings':[box([2,0,2],[7,3,10],'frame'),box([8,0,4],[13,2,12],'cloth'),box([4,3,3],[5,4,8],'metal')],
'hanging_herbs':[box([0,12,13],[16,14,15])]+[box([x,2,13],[x+2,12,15],'leaf') for x in [2,7,12]],
'produce_basket':[box([2,0,2],[14,2,14]),box([2,2,2],[4,7,14]),box([12,2,2],[14,7,14]),box([4,2,2],[12,7,4]),box([4,2,12],[12,7,14]),box([4,2,4],[12,6,12],'leaf'),box([5,6,5],[8,8,8],'food')],
'water_pitcher':mug('water',bottle=True),
'plate_stack':[box([2,0,2],[14,1,14],'pottery'),box([3,1,3],[13,2,13],'paper'),box([2,2,2],[14,3,14],'pottery')],
'firewood':[box([2,0,2],[5,3,14],'frame'),box([6,0,2],[9,3,14]),box([10,0,2],[13,3,14],'frame'),box([4,3,2],[7,6,14]),box([8,3,2],[11,6,14],'frame')],
'wall_sconce':[box([6,0,14],[10,12,16],'metal'),box([6,3,10],[10,5,14],'metal'),box([7,5,10],[9,11,12],'paper'),box([7,11,10],[9,13,12],'food')],
'paper_quill':[box([2,0,2],[12,.3,12],'paper'),box([10,0,5],[12,2,7],'metal'),box([9,2,6],[10,9,7],'paper')],
'flour_sack':[box([3,0,3],[13,7,13],'paper'),box([5,7,5],[11,8,11],'frame')],
'cookware_rack':[box([0,12,13],[16,14,15])]+[box([x,3,13],[x+4,9,15],'metal') for x in [1,6,11]],
'serving_tray_prop':plate(False)+[box([4,1,4],[12,4,12],'food')],
'mug_cluster':mug(),
'bread_basket':plate(True)+[box([2,2,3],[5,5,12]),box([10,2,3],[13,5,12])],
'card_set':[box([1,0,2],[5,.2,8],'cards'),box([7,0,6],[11,.2,12],'cards'),box([10,0,1],[14,2,5],'cards')],
'tool_samples':[box([3,0,3],[13,2,6],'metal'),box([7,2,4],[9,6,6],'frame'),box([2,0,9],[12,2,11],'metal')],
'tool_rack':[box([0,12,13],[16,14,15])]+[box([x,2,13],[x+2,12,15],'frame') for x in [2,7,12]]+[box([1,2,13],[6,5,15],'metal')],
'ore_crate':[box([1,0,1],[15,7,15]),box([3,7,3],[7,10,7],'metal'),box([8,7,7],[13,9,13],'metal')]
}
textures['cards']='svarcade_tcg:item/card_back'
for name,elements in props.items():
 variants={}
 for wood in ['oak','spruce','dark_oak','acacia','mangrove']:
  model=name if wood=='oak' else name+'_'+wood
  data={'textures':textures,'elements':elements,'display':{'gui':{'rotation':[30,225,0],'scale':[.6,.6,.6]}}} if wood=='oak' else {'parent':'worldcomesalive:block/'+name,'textures':{'wood':'minecraft:block/'+wood+'_planks'}}
  write(Path('assets/worldcomesalive/models/block')/(model+'.json'),data)
  for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing+',wood='+wood]={'model':'worldcomesalive:block/'+model,'y':rot}
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':variants})
 write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name})
 write(Path('data/worldcomesalive/loot_table/blocks')/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'worldcomesalive:'+name}]}]})
 write(Path('data/worldcomesalive/recipe')/(name+'.json'),{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:stick'},{'tag':'minecraft:planks'}],'result':{'id':'worldcomesalive:'+name,'count':1}})
 lang['block.worldcomesalive.'+name]=name.replace('_',' ').title()
for tag,values in {'table_display':['idle_tableware','bread_basket','mug_cluster','card_set'],'wall_decoration':['wall_sconce','hanging_herbs','cookware_rack','tool_rack','tapestry','wall_shelf'],'domestic_clutter':list(props)}.items():write(Path('data/worldcomesalive/tags/block')/(tag+'.json'),{'replace':False,'values':['worldcomesalive:'+x for x in values]})
write(Path('assets/worldcomesalive/lang/en_us.json'),lang)
# Cookware remains placeable even when it also appears in the domestic goods catalog.
for name in ['cooking_pot','pan']:write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name})
Path('tools/wca/prop_catalog.json').write_text(json.dumps(list(props),indent=2)+'\n')
# Functional storage and request-board surfaces used by generated agricultural lots.
for name,elements in {'hay_storage':[box([0,0,0],[16,16,16],'hay')],'notice_board':[box([2,0,6],[4,24,8],'frame'),box([12,0,6],[14,24,8],'frame'),box([1,9,5],[15,22,7]),box([3,12,4.5],[8,19,5],'paper'),box([9,11,4.5],[13,17,5],'paper')]}.items():
 textures['hay']='minecraft:block/hay_block_side';variants={}
 for wood in ['oak','spruce','dark_oak','acacia','mangrove']:
  model=name+'_'+wood
  write(Path('assets/worldcomesalive/models/block')/(model+'.json'),{'textures':{**textures,'wood':'minecraft:block/'+wood+'_planks'},'elements':elements})
  for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+facing+',wood='+wood]={'model':'worldcomesalive:block/'+model,'y':rot}
 write(Path('assets/worldcomesalive/blockstates')/(name+'.json'),{'variants':variants});write(Path('assets/worldcomesalive/models/item')/(name+'.json'),{'parent':'worldcomesalive:block/'+name+'_oak'})
