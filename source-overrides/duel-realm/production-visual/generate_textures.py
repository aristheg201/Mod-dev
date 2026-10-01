"""Deterministic authored duel materials. Run only when changing these source assets."""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math
root=Path(__file__).parent/'resources/assets/svarcade_tcg/textures/duel'
root.mkdir(parents=True,exist_ok=True)
# Shared identity-free back: a luminous bronze vortex inside a restrained midnight rim.
w,h=700,1000
im=Image.new('RGB',(w,h));p=im.load()
for y in range(h):
 for x in range(w):
  nx=(x-w/2)/(w*.47);ny=(y-h/2)/(h*.47);r=math.hypot(nx,ny);a=math.atan2(ny,nx)
  spiral=(.5+.5*math.cos(a*3-r*28))**10*math.exp(-r*.9)
  halo=math.exp(-((r-.42)/.045)**2)*.35
  glow=spiral+halo
  p[x,y]=(int(min(255,18+181*glow)),int(min(255,17+111*glow)),int(min(255,28+37*glow)))
d=ImageDraw.Draw(im)
d.rounded_rectangle((5,5,w-6,h-6),22,outline='#080C16',width=18)
d.rounded_rectangle((25,25,w-26,h-26),15,outline='#E4BE74',width=5)
d.rounded_rectangle((37,37,w-38,h-38),10,outline='#725735',width=3)
for y in (87,913):
 d.line((100,y,600,y),fill='#BA955B',width=3)
 d.polygon([(350,y-13),(363,y),(350,y+13),(337,y)],fill='#EED496')
im.save(root/'card_back.png',optimize=True)
# Board: fine inlaid zones replace chunky stacks; positions map exactly to the existing server field.
scale=48;w=43*scale;h=29*scale
im=Image.new('RGB',(w,h));p=im.load()
for y in range(h):
 for x in range(w):
  r=math.hypot((x-w/2)/w,(y-h/2)/h)
  side=-1 if y<h/2 else 1
  p[x,y]=(int(14+14*(1-r)),int(26+16*(1-r)),int(41+19*(1-r))) if side==-1 else (int(27+14*(1-r)),int(23+15*(1-r)),int(38+17*(1-r)))
d=ImageDraw.Draw(im)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',19)
small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',15)
large=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',27)
def xy(x,z):return (int((x+21.5)*scale),int((z+14.5)*scale))
def rect(x,z,ww,hh,color,label):
 cx,cy=xy(x,z);box=(int(cx-ww*scale/2),int(cy-hh*scale/2),int(cx+ww*scale/2),int(cy+hh*scale/2))
 d.rounded_rectangle(box,radius=10,outline=color,width=3)
 d.rounded_rectangle((box[0]+8,box[1]+8,box[2]-8,box[3]-8),radius=6,outline=tuple(int(v*.45) for v in color),width=1)
 # Label lives beyond the card footprint.
 d.text((cx,box[3]+8),label,font=small,fill=color,anchor='mt')
for side in (-1,1):
 color=(101,202,219) if side==1 else (222,139,151)
 for i in range(5):
  rect((i-2)*6,side*4,4.7,3.65,color,'MONSTER  '+str(i+1))
  rect((i-2)*6,side*9,4.2,3.55,tuple(int(v*.73) for v in color),'SPELL / TRAP')
 rect(-side*18,side*10,2.8,3.3,(143,203,147),'FIELD')
 rect(side*18,side*10,2.8,3.3,(232,191,120),'DECK')
 rect(side*18,side*5,2.8,3.3,(148,180,203),'GRAVEYARD')
 rect(-side*18,side*5,2.8,3.3,(177,152,230),'EXTRA DECK')
 d.text(xy(0,side*12.65),'DUELIST  '+('01' if side==1 else '02'),font=large,fill=color,anchor='mm')
for x in (-3,3):rect(x,0,3.5,2.6,(224,199,144),'EXTRA MONSTER')
for x in (-18,18):rect(x,0,2.8,2.8,(174,138,198),'BANISHED')
# Center focus and restrained perimeter; no giant ellipse.
for x1,x2 in [(-15,-5.1),(5.1,15)]:d.line((*xy(x1,0),*xy(x2,0)),fill='#A7BAC4',width=3)
cx,cy=xy(0,0);d.polygon([(cx,cy-25),(cx+25,cy),(cx,cy+25),(cx-25,cy)],outline='#E5C78D',width=3)
d.rounded_rectangle((12,12,w-13,h-13),radius=24,outline='#DCC494',width=3)
d.rounded_rectangle((24,24,w-25,h-25),radius=20,outline='#3F6079',width=2)
im.save(root/'board.png',optimize=True)
# Transparent defensive ward: a flat cyan ring with four shield facets, never an upright block/slab.
im=Image.new('RGBA',(512,512),(0,0,0,0));d=ImageDraw.Draw(im)
d.ellipse((22,22,490,490),outline=(91,223,244,235),width=5)
d.ellipse((39,39,473,473),outline=(44,145,196,180),width=2)
for x,y in [(256,23),(489,256),(256,489),(23,256)]:
 d.polygon([(x-14,y-9),(x+14,y-9),(x+11,y+10),(x,y+18),(x-11,y+10)],fill=(89,227,247,255))
im.save(root/'defense.png',optimize=True)
