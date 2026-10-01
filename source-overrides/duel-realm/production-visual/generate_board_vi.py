from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math
root=Path(__file__).parent/'resources/assets/svarcade_tcg/textures/duel'
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
  rect((i-2)*6,side*4,4.7,3.65,color,'QUÁI THÚ  '+str(i+1))
  rect((i-2)*6,side*9,4.2,3.55,tuple(int(v*.73) for v in color),'PHÉP / BẪY')
 rect(-side*18,side*10,2.8,3.3,(143,203,147),'KHU VỰC')
 rect(side*18,side*10,2.8,3.3,(232,191,120),'BỘ BÀI')
 rect(side*18,side*5,2.8,3.3,(148,180,203),'NGHĨA ĐỊA')
 rect(-side*18,side*5,2.8,3.3,(177,152,230),'BỘ BÀI PHỤ')
 d.text(xy(0,side*12.65),'ĐẤU THỦ  '+('01' if side==1 else '02'),font=large,fill=color,anchor='mm')
for x in (-3,3):rect(x,0,3.5,2.6,(224,199,144),'QUÁI THÚ PHỤ')
for x in (-18,18):rect(x,0,2.8,2.8,(174,138,198),'LOẠI BỎ')
# Center focus and restrained perimeter; no giant ellipse.
for x1,x2 in [(-15,-5.1),(5.1,15)]:d.line((*xy(x1,0),*xy(x2,0)),fill='#A7BAC4',width=3)
cx,cy=xy(0,0);d.polygon([(cx,cy-25),(cx+25,cy),(cx,cy+25),(cx-25,cy)],outline='#E5C78D',width=3)
d.rounded_rectangle((12,12,w-13,h-13),radius=24,outline='#DCC494',width=3)
d.rounded_rectangle((24,24,w-25,h-25),radius=20,outline='#3F6079',width=2)
im.save(root/'board_vi.png',optimize=True)
