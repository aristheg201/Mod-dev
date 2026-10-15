#!/usr/bin/env python3
"""Original portable Blockbench projects and matching Java block exports. No third-party textures."""
import base64,json,uuid
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/svextract'
PROJECTS=ROOT/'art/blockbench'

def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
def build(name,accent):
 texture=ASSETS/'textures/block'/f'{name}.png';texture.parent.mkdir(parents=True,exist_ok=True)
 image=Image.new('RGBA',(64,64),'#253744');d=ImageDraw.Draw(image)
 d.rectangle((2,2,61,61),outline='#78919d',width=2);d.rectangle((7,7,56,56),fill='#15242d',outline='#485e6a',width=2)
 d.rectangle((8,24,55,39),fill=accent);d.rectangle((26,19,37,44),fill='#afbac0',outline='#0c161c',width=2)
 d.rectangle((29,25,34,31),fill='#1a232b');d.line((12,13,51,13),fill='#415565',width=2)
 for x in (5,58):
  for y in (5,58):d.rectangle((x-1,y-1,x+1,y+1),fill='#c2d4dc')
 image.save(texture)
 boxes=[('body',[1,1,1],[15,11,15]),('reinforced_lid',[0,11,0],[16,14,16]),('front_latch',[6,7,0],[10,13,1]),('left_rail',[0,0,1],[3,3,15]),('right_rail',[13,0,1],[16,3,15])]
 elements=[];bb=[]
 for label,start,end in boxes:
  faces={side:{'uv':[0,0,16,16],'texture':'#0'} for side in ['north','east','south','west','up','down']}
  elements.append({'from':start,'to':end,'faces':faces})
  bb.append({'name':label,'type':'cube','box_uv':False,'rescale':False,'locked':False,'from':start,'to':end,'autouv':0,'color':0,'origin':[8,8,8],'faces':{side:{'uv':[0,0,64,64],'texture':0} for side in faces},'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'svextract:'+name+':'+label))})
 model={'parent':'minecraft:block/block','textures':{'0':'svextract:block/'+name,'particle':'svextract:block/'+name},'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[0.65,0.65,0.65]},'ground':{'translation':[0,3,0],'scale':[0.25,0.25,0.25]},'fixed':{'scale':[0.5,0.5,0.5]}}}
 write(ASSETS/'models/block'/f'{name}.json',model)
 opened=json.loads(json.dumps(model));opened['elements'][1]['rotation']={'origin':[8,11,15],'axis':'x','angle':-45,'rescale':False}
 write(ASSETS/'models/block'/f'{name}_open.json',opened)
 write(ASSETS/'blockstates'/f'{name}.json',{'variants':{'open=false':{'model':'svextract:block/'+name},'open=true':{'model':'svextract:block/'+name+'_open'}}})
 write(ASSETS/'models/item'/f'{name}.json',{'parent':'svextract:block/'+name})
 write(PROJECTS/f'{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,'model_identifier':'svextract:'+name,'visible_box':[1,1,0],'resolution':{'width':64,'height':64},'elements':bb,'outliner':[x['uuid'] for x in bb],'textures':[{'path':'','name':name+'.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'svextract:texture:'+name)),'width':64,'height':64,'uv_width':64,'uv_height':64,'particle':True,'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}],'display':model['display']})
for name,color in [('supply_crate','#45cebd'),('battleground_crate','#e6a23c'),('stash_terminal','#5c94d6')]:build(name,color)
