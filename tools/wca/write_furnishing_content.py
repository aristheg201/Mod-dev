"""Authored interior compositions. Coordinates are local cluster anchors, never world/NPC coordinates."""
from pathlib import Path
import json
C={}
def atom(kind,x,y,z,facing='north',layer='detail',markers=()):return dict(type=kind,x=x,y=y,z=z,facing=facing,layer=layer,markers=list(markers))
def cluster(name,rows):C[name]=[atom(*r) for r in rows]
cluster('master_bedroom',[
 ('bed_foot',1,0,1,'north','primary',['BED']),('bed_head',1,0,0,'north','primary',[]),('bed_foot',2,0,1,'north','primary',['BED']),('bed_head',2,0,0,'north','primary',[]),
 ('bedside_table',3,0,1,'north','secondary',[]),('candle_holder',3,1,1),('@storage',0,0,3,'east','primary',['FOOD_STORAGE']),('folded_cloth',0,1,3),
 ('rug',2,0,3,'north','detail',[]),('tapestry',0,1,1,'east','detail',[]),('wall_shelf',3,1,0,'south','secondary',[]),('personal_belongings',3,2,0)])
cluster('child_bedroom',[
 ('bed_foot',2,0,1,'east','primary',['BED']),('bed_head',3,0,1,'east','primary',[]),('writing_desk',0,0,1,'south','primary',['READING_POINT']),
 ('wooden_stool',0,0,2,'north','secondary',[]),('book_stack',0,1,1),('storage_chest',2,0,3,'north','secondary',[]),('personal_belongings',2,1,3),('wall_shelf',3,1,3,'west','secondary',[]),('flower_vase',3,2,3)])
cluster('dining_family',[
 ('medieval_table',1,0,1,'north','primary',['DINING_TABLE']),('@seat',0,0,1,'east','primary',['DINING_POINT']),('@seat',2,0,1,'west','primary',['DINING_POINT']),('@seat',1,0,2,'north','primary',['DINING_POINT']),
 ('idle_tableware',1,1,1),('rug',0,0,2),('rug',2,0,2),('tapestry',-1,1,1,'east'),('coat_rack',-1,0,3,'north','secondary',[])])
cluster('kitchen_corner',[
 ('preparation_counter',1,0,1,'south','primary',['KITCHEN_POINT']),('preparation_counter',2,0,1,'south','primary',[]),('chopping_board',1,1,1),('cooking_pot',2,1,1),
 ('food_pantry',2,0,-1,'west','secondary',['FOOD_STORAGE']),('ingredient_shelf',2,1,0,'west','secondary',[]),('hanging_herbs',2,2,0,'west'),('produce_basket',0,0,1),('water_pitcher',2,1,-1),('wall_shelf',1,1,-2,'south','secondary',[]),('plate_stack',1,2,-2)])
cluster('hearth_corner',[
 ('hearth_base',0,0,0,'north','primary',[]),('hearth',0,1,0,'north','primary',[]),('chimney',0,2,0,'north','secondary',[]),('firewood',0,0,1),('armchair',2,0,0,'west','primary',['SOCIAL_POINT']),('rug',1,0,0),('wall_sconce',0,2,2,'east')])
cluster('reading_corner',[
 ('writing_desk',1,0,1,'west','primary',['READING_POINT']),('wooden_stool',0,0,1,'east','secondary',[]),('book_stack',1,1,1),('medieval_bookshelf',1,0,0,'west','secondary',[]),('paper_quill',1,1,0),('wall_sconce',1,2,1,'west')])
cluster('tavern_guest_beds',[
 ('bed_foot',0,0,1,'north','primary',['BED']),('bed_head',0,0,0,'north','primary',[]),('bedside_table',1,0,1,'north','secondary',[]),('candle_holder',1,1,1),
 ('bed_foot',3,0,2,'west','primary',['BED']),('bed_head',2,0,2,'west','primary',[]),('storage_chest',3,0,0,'south','secondary',['FOOD_STORAGE']),('folded_cloth',3,1,0),('rug',1,0,2),('coat_rack',0,0,3,'north','secondary',[]),('tapestry',-1,1,1,'east')])
cluster('tavern_kitchen',[
 ('oven_hearth',3,0,1,'west','primary',['KITCHEN_POINT']),('cooking_pot',3,1,1),('chimney',3,2,1),('preparation_counter',0,0,3,'south','primary',[]),('preparation_counter',1,0,3,'south','primary',[]),('preparation_counter',2,0,3,'south','primary',[]),
 ('chopping_board',0,1,3),('plate_stack',1,1,3),('water_pitcher',2,1,3),('ingredient_shelf',1,1,0,'south','secondary',[]),('hanging_herbs',1,2,0,'south'),('food_pantry',3,0,3,'west','secondary',['FOOD_STORAGE']),('produce_basket',3,1,3),('flour_sack',0,0,0),('firewood',3,0,2),('cookware_rack',3,2,3,'west')])
cluster('bar_service',[
 *[('bar_counter',x,0,1,'south','primary',['SHOP_COUNTER'] if x==2 else []) for x in range(5)],
 ('bar_counter',0,0,2,'east','primary',[]),('bar_counter',0,0,3,'east','primary',[]),('bar_counter',0,0,4,'east','primary',[]),
 ('bottle_shelf',2,1,-2,'south','secondary',[]),('mug_rack',3,1,-2,'south','secondary',[]),('bottle_shelf',4,1,0,'west','secondary',[]),
 ('keg',4,0,0,'south','secondary',['DRINK_STORAGE']),('keg',3,0,0,'south','secondary',[]),('serving_tray_prop',1,1,1),('mug_cluster',3,1,1),('wine_rack',4,1,2,'west','secondary',[]),('wall_sconce',4,2,3,'west')])
cluster('long_dining_group',[
 ('long_tavern_table',1,0,0,'north','primary',['DINING_TABLE']),('medieval_table',1,0,1,'north','primary',['DINING_TABLE']),
 ('wooden_bench',0,0,0,'east','primary',['DINING_POINT','TAVERN_SEAT']),('wooden_bench',0,0,1,'east','primary',['DINING_POINT','TAVERN_SEAT']),('@seat',2,0,0,'west','primary',['DINING_POINT','TAVERN_SEAT']),('@seat',2,0,1,'west','primary',['DINING_POINT','TAVERN_SEAT']),
 ('idle_tableware',1,1,0),('bread_basket',1,1,1),('rug',0,0,2),('rug',1,0,2),('rug',2,0,2)])
cluster('small_tavern_group',[
 ('small_table',1,0,0,'north','primary',['DINING_TABLE']),('tavern_stool',0,0,0,'east','primary',['DINING_POINT','TAVERN_SEAT']),('dining_chair',2,0,0,'west','primary',['DINING_POINT','TAVERN_SEAT']),('idle_tableware',1,1,0),('tapestry',-1,1,0,'east')])
cluster('card_corner',[
 ('dining_table',1,0,1,'north','primary',['DINING_TABLE','CARD_DUEL_TABLE']),('dining_table',2,0,1,'north','primary',['DINING_TABLE','CARD_DUEL_TABLE']),
 ('dining_chair',1,0,0,'south','primary',['DINING_POINT','TAVERN_SEAT']),('dining_chair',2,0,2,'north','primary',['DINING_POINT','TAVERN_SEAT']),('wooden_bench',4,0,1,'west','secondary',['SOCIAL_POINT']),('card_set',1,1,1),('mug_cluster',2,1,1),('medieval_bookshelf',4,0,0,'west','secondary',[]),('card_set',4,1,0),('tapestry',4,1,2,'west'),('wall_sconce',4,2,3,'west')])
cluster('smith_workspace',[
 ('anvil',1,0,0,'north','primary',['WORKSTATION']),('work_table',3,0,0,'west','primary',[]),('tool_samples',3,1,0),('tool_rack',3,1,-1,'west'),('ore_crate',3,0,1,'north','secondary',[]),('oven_hearth',2,0,-2,'south','primary',[]),('chimney',2,1,-2),('cookware_rack',2,2,-2,'south')])
cluster('merchant_display',[
 ('serving_counter',1,0,0,'south','primary',['SHOP_COUNTER']),('serving_counter',2,0,0,'south','primary',[]),('bread_basket',1,1,0),('produce_basket',2,1,0),('crate',3,0,0,'north','secondary',[]),('flour_sack',3,1,0),('ingredient_shelf',3,1,-1,'west','secondary',[]),('wall_sconce',3,2,0,'west')])
cluster('personal_scholar',[('medieval_bookshelf',0,0,0,'east','secondary',[]),('paper_quill',0,1,0),('wall_shelf',0,1,-1,'east','secondary',[]),('book_stack',0,2,-1)])
cluster('personal_farmer',[('produce_basket',0,0,0),('tool_rack',0,1,-1,'east'),('flour_sack',0,0,-1)])
cluster('personal_cards',[('card_set',0,1,0),('wall_shelf',0,1,-1,'east','secondary',[]),('book_stack',0,2,-1)])
# Room kits select materials, deliberate storage/seating/decoration and complexity budget.
styles=[]
for region,wood in [('nordic','spruce'),('highland','spruce'),('forest','dark_oak'),('coastal','oak'),('mountain','spruce'),('swamp','mangrove'),('desert','acacia'),('temperate_kingdom','oak')]:
 for wealth in ['poor','common','wealthy']:
  styles.append(dict(id='wca:'+region+'_'+wealth,region=region,wealth=wealth,wood=wood,seat='wooden_stool' if wealth=='poor' else 'armchair' if wealth=='wealthy' else 'medieval_chair',storage='crate' if wealth=='poor' else 'wardrobe' if wealth=='wealthy' else 'medieval_cabinet',decorationBudget=24 if wealth=='poor' else 40 if wealth=='common' else 64,density=.4 if wealth=='poor' else .52 if wealth=='common' else .62,lighting='candle' if wealth=='poor' else 'lantern',tableware=wealth))
value=dict(styles=styles,clusters=C,variants=dict(BEDROOM=['master_bedroom','child_bedroom'],KITCHEN=['kitchen_corner','tavern_kitchen'],DINING_ROOM=['dining_family','small_tavern_group','long_dining_group'],TAVERN_COMMON_ROOM=['long_dining_group','small_tavern_group'],TAVERN_BAR=['bar_service','bar_service_mirrored'],SHOP_FLOOR=['merchant_display','merchant_display_mirrored'],BLACKSMITH_WORKSPACE=['smith_workspace','smith_workspace_mirrored'],TAVERN_GUEST_ROOM=['tavern_guest_beds','tavern_guest_beds_mirrored']))
p=Path('src/main/resources/data/worldcomesalive/living_world/furnishing.json');p.write_text(json.dumps(value,indent=2)+'\n')
