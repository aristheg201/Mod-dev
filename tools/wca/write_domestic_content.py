"""Reproducible editable domestic catalog. One runtime implementation serves every definition."""
from pathlib import Path
import json
G={}
def good(id,name,category,nutrition=0,value=1,container='',alcohol=0,tags=(),regions=(),method='raw',quality=True):
 G['worldcomesalive:'+id]=dict(name=name,category=category,nutrition=nutrition,saturation=.6,value=value,container=('worldcomesalive:'+container if container else ''),alcohol=alcohol,tags=list(tags),regions=list(regions),method=method,quality=quality,freshnessTicks=72000)
foods=[('dark_bread','Dark Bread','grain',5,3),('flatbread','Flatbread','grain',4,2),('roll','Bread Roll','grain',3,2),('porridge','Porridge','grain',5,4),('roast_beef','Roast Beef','meat',8,7),('roast_pork','Roast Pork','meat',8,7),('roast_poultry','Roast Poultry','meat',6,5),('game_roast','Roast Game','meat',8,8),('sausage','Sausage','meat',6,5),('smoked_meat','Smoked Meat','meat',7,7),('roast_fish','Roast Fish','fish',6,5),('smoked_fish','Smoked Fish','fish',6,6),('fish_stew','Fish Stew','meal',9,8),('potato_dish','Herbed Potatoes','vegetable',6,4),('carrot_dish','Roast Carrots','vegetable',5,4),('cabbage','Cabbage','vegetable',3,2),('mushroom_dish','Mushroom Skillet','vegetable',6,5),('vegetable_stew','Vegetable Stew','meal',8,6),('cheese','Cheese','dairy',5,5),('butter','Butter','dairy',2,3),('grapes','Grapes','fruit',3,3),('berry_compote','Berry Compote','fruit',5,4),('beef_stew','Beef Stew','meal',10,8),('vegetable_soup','Vegetable Soup','meal',7,5),('roast_dinner','Roast Dinner','meal',12,12),('meat_pie','Meat Pie','meal',10,9),('vegetable_pie','Vegetable Pie','meal',8,7),('fish_meal','Fish Supper','meal',9,8),('breakfast','Farmhouse Breakfast','meal',8,6),('travel_ration','Travel Ration','meal',8,6),('feast_platter','Feast Platter','meal',16,20),('honey_pastry','Honey Pastry','dessert',6,5),('berry_tart','Berry Tart','dessert',6,5),('apple_pie','Apple Pie','dessert',7,6),('honey_cake','Honey Cake','dessert',8,7)]
for ident,name,category,nutrition,value in foods:
 good(ident,name,category,nutrition,value,'bowl' if 'stew' in ident or ident in ['porridge','vegetable_soup'] else 'plate' if category=='meal' else '',tags=[category,'food'],method='cook',regions=['temperate_kingdom'] if category=='grain' else ['nordic'] if 'smoked' in ident else [])
for ident,name,container,value,strength in [('water_jug','Water Jug','jug',1,0),('milk_cup','Milk Cup','cup',2,0),('tea','Tea','cup',3,0),('herbal_tea','Herbal Tea','cup',3,0),('apple_juice','Apple Juice','wooden_mug',3,0),('berry_juice','Berry Juice','cup',3,0),('ale','Ale Tankard','tankard',3,.8),('beer','Beer Mug','wooden_mug',3,.7),('stout','Stout Tankard','tankard',4,1),('cider','Cider Mug','wooden_mug',4,.8),('mead','Mead Goblet','goblet',5,1.1),('red_wine','Red Wine Goblet','goblet',7,1.2),('white_wine','White Wine Goblet','goblet',7,1.2),('berry_wine','Berry Wine Bottle','bottle',6,1.1),('mulled_wine','Mulled Wine Goblet','goblet',8,1.2)]:
 good(ident,name,'drink',1 if not strength else 0,value,container,strength,tags=['drink','alcohol' if strength else 'nonalcoholic'],regions=['temperate_kingdom','coastal'] if 'wine' in ident else ['nordic','highland'] if ident in ['ale','stout'] else [],method='ferment' if strength else 'brew')
for ident in ['plate','bowl','fork','spoon','knife','cup','wooden_mug','tankard','goblet','wine_glass','bottle','serving_tray','jug','cooking_pot','pan','letter']:
 good(ident,ident.replace('_',' ').title(),'tableware',value=3 if ident in ['goblet','wine_glass'] else 1,quality=False,tags=['tableware'])
for ident in ['malt','wort','crushed_grapes','unfermented_mead']:
 good(ident,ident.replace('_',' ').title(),'ingredient',value=1,quality=False,tags=['ingredient'])
R=[]
def recipe(id,output,inputs,station='hearth',ticks=200,count=2,roles=('cook','innkeeper','resident'),quality=1):
 R.append(dict(id=id,output='worldcomesalive:'+output,inputs=inputs,station=station,ticks=ticks,count=count,roles=list(roles),quality=quality))
recipe('malt','malt',{'minecraft:wheat':2},'preparation',100,3,('brewer','innkeeper'))
recipe('wort','wort',{'worldcomesalive:malt':2,'worldcomesalive:water_jug':1},'keg',160,3,('brewer','innkeeper'))
for ident in ['ale','beer','stout']:recipe(ident,ident,{'worldcomesalive:wort':1},'fermentation',300,3,('brewer','innkeeper'))
recipe('crush_grapes','crushed_grapes',{'worldcomesalive:grapes':2},'preparation',100,3,('vintner','innkeeper'))
for ident in ['red_wine','white_wine','berry_wine']:recipe(ident,ident,{'worldcomesalive:crushed_grapes':1},'fermentation',360,2,('vintner','innkeeper'))
recipe('unfermented_mead','unfermented_mead',{'minecraft:honey_bottle':1,'worldcomesalive:water_jug':1},'keg',100,3,('brewer','innkeeper'))
recipe('mead','mead',{'worldcomesalive:unfermented_mead':1},'fermentation',300,2,('brewer','innkeeper'))
recipe('cider','cider',{'minecraft:apple':2},'fermentation',300,3,('brewer','innkeeper'))
recipe('mulled_wine','mulled_wine',{'worldcomesalive:red_wine':1,'minecraft:sweet_berries':1},'hearth',160,2,('cook','innkeeper'))
for ident,raw in [('tea','minecraft:kelp'),('herbal_tea','minecraft:dandelion'),('apple_juice','minecraft:apple'),('berry_juice','minecraft:sweet_berries')]:recipe(ident,ident,{raw:1,'worldcomesalive:water_jug':1},'hearth',100,3)
recipe('milk_cup','milk_cup',{'minecraft:milk_bucket':1},'preparation',80,4)
for ident,name,category,nutrition,value in foods:
 inputs={'minecraft:wheat':2} if category in ['grain','dessert'] or 'pie' in ident else {'minecraft:potato':1,'minecraft:carrot':1}
 if category=='meat' or ident in ['beef_stew','roast_dinner','meat_pie','breakfast','feast_platter']:inputs['minecraft:beef']=1
 if category=='fish' or ident in ['fish_stew','fish_meal']:inputs['minecraft:cod']=1
 if category=='dairy':inputs={'minecraft:milk_bucket':1}
 if ident=='grapes':inputs={'minecraft:sweet_berries':2}
 if ident=='cabbage':inputs={'minecraft:carrot':2}
 if 'honey' in ident:inputs['minecraft:honey_bottle']=1
 if 'berry' in ident:inputs['minecraft:sweet_berries']=1
 if 'stew' in ident or ident=='vegetable_soup':inputs['worldcomesalive:water_jug']=1
 recipe(ident,ident,inputs,'oven' if category in ['grain','dessert'] or 'pie' in ident else 'hearth',160,2,('cook','innkeeper','resident','baker') if category in ['grain','dessert'] else ('cook','innkeeper','resident'))
for ident in ['plate','bowl','wooden_mug','tankard','serving_tray','jug']:recipe('make_'+ident,ident,{'minecraft:oak_planks':1},'work_table',140,2,('carpenter','potter'))
for ident in ['cup','bottle','wine_glass','goblet']:recipe('make_'+ident,ident,{'minecraft:clay_ball':1},'oven',180,2,('potter',))
for ident in ['fork','spoon','knife','pan','cooking_pot']:recipe('make_'+ident,ident,{'minecraft:iron_nugget':1},'forge',120,2,('blacksmith','carpenter'))
# Vanilla foods participate in the same observable economy and preference metadata.
for ident,name,category,nutrition,value in [('bread','Bread','grain',5,3),('carrot','Carrot','vegetable',3,1),('potato','Potato','vegetable',1,1),('apple','Apple','fruit',4,2),('sweet_berries','Berries','fruit',2,1),('cooked_beef','Cooked Beef','meat',8,5),('cooked_cod','Cooked Fish','fish',5,4)]:
 G['minecraft:'+ident]=dict(name=name,category=category,nutrition=nutrition,saturation=.6,value=value,container='',alcohol=0,tags=[category,'food'],regions=[],method='cook' if ident.startswith('cooked_') else 'raw',quality=False,freshnessTicks=72000)
D=dict(goods=G,recipes=R,alcohol=dict(enabled=True,decayPerTick=.0003,relaxed=.3,tipsy=1.5,drunk=3,veryDrunk=5,maxCardPenalty=.2),spoilage=False,publicTableware=True,npcDrinkInterval=2400,initialFarmResources={'minecraft:wheat':60,'minecraft:potato':32,'minecraft:carrot':32,'minecraft:beef':16,'minecraft:cod':12,'minecraft:apple':16,'minecraft:sweet_berries':18,'worldcomesalive:grapes':16,'worldcomesalive:water_jug':40,'minecraft:honey_bottle':6,'minecraft:milk_bucket':8,'minecraft:dandelion':8,'minecraft:kelp':8,'minecraft:oak_planks':24,'minecraft:clay_ball':16})
p=Path('src/main/resources/data/worldcomesalive/living_world/domestic.json');p.write_text(json.dumps(D,indent=2)+'\n')
