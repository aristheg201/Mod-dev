"""Deterministic original industrial-hub backdrop, generated from primitives, not third-party assets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math, random
random.seed(1937)
W,H=1024,512
im=Image.new('RGB',(W,H));px=im.load()
for y in range(H):
    for x in range(W):
        haze=max(0,1-(y/H)*1.2)
        n=random.randint(-3,3)
        px[x,y]=(max(0,int(17+25*haze+n)),max(0,int(32+35*haze+n)),max(0,int(45+36*haze+n)))
d=ImageDraw.Draw(im,'RGBA')
# Distant dimensional scar and smog layers
for radius in range(200,10,-8):
    opacity=round(2+(200-radius)/200*4)
    d.ellipse((690-radius,155-radius,690+radius,155+radius),fill=(82,179,175,opacity))
for i in range(19):
    x=random.randrange(W); y=random.randrange(95,270)
    d.rectangle((x,y,x+random.randrange(20,120),290),fill=(24,41,50,random.randrange(20,70)))
# Structure silhouettes and bridge deck
for i in range(14):
    x=i*84-30; roof=150+random.randrange(12,135)
    col=random.choice([(12,29,35,230),(14,35,41,240),(23,37,43,230)])
    d.rectangle((x,roof,x+random.randrange(42,78),H),fill=col)
    for j in range(3):
        yy=roof+18+j*31
        if yy<H-65: d.rectangle((x+5,yy,x+40,yy+3),fill=(70,107,114,95))
    d.rectangle((x-2,roof-10,x+62,roof),fill=(47,65,64,185))
# Far left warehouse
for x0,x1,y0 in [(15,175,235),(210,335,185),(794,999,220)]:
    d.polygon([(x0,y0+40),(x0+15,y0),(x1-25,y0),(x1,y0+38),(x1,H),(x0,H)],fill=(15,28,32,230))
    for x in range(x0+14,x1,24):
        d.line((x,y0+50,x,H),fill=(75,90,80,65),width=2)
    d.line((x0,y0+40,x0+15,y0,x1-25,y0,x1,y0+38),fill=(111,126,111,220),width=4)
# Cargo bridge framing
for y in [310,385,448]:
    d.line((0,y,W,y),fill=(76,97,95,120),width=7)
for x in range(0,W+30,85):
    d.line((x,300,x+30,H),fill=(65,82,78,125),width=6)
# Central gateway and suspended gantry behind player
for x in [390,624]:
    d.rectangle((x,90,x+14,420),fill=(13,29,31,235))
    for off in range(0,300,42): d.rectangle((x+3,115+off,x+11,121+off),fill=(150,166,130,110))
d.rectangle((383,89,640,108),fill=(28,57,55,242))
for x in range(400,630,24): d.line((x,89,x+10,105),fill=(104,155,145,160),width=2)
for y in range(155,380,24):
    d.line((408,y,609,y),fill=(38,66,62,70),width=2)
# Ambient lamp cones
for x,y in [(115,254),(330,213),(835,263),(960,280)]:
    d.ellipse((x-13,y-13,x+13,y+13),fill=(246,184,86,160))
    d.polygon([(x-5,y),(x+5,y),(x+72,y+115),(x-72,y+115)],fill=(230,162,67,15))
# Crates and rail elements
for x,y,w,h in [(295,370,85,70),(680,345,126,94),(810,395,78,44),(195,400,64,51)]:
    d.rectangle((x,y,x+w,y+h),fill=(43,59,57,236),outline=(126,145,129,220),width=2)
    d.line((x,y,x+w,y+h),fill=(108,127,107,145),width=2)
    d.line((x+w,y,x,y+h),fill=(108,127,107,125),width=2)
    d.rectangle((x+7,y+7,x+w-7,y+13),fill=(176,147,75,185))
d.rectangle((0,445,W,H),fill=(14,28,30,228))
for x in range(0,W,65):
    d.line((x,445,x-65,H),fill=(75,101,98,65),width=3)
# Futuristic HUD decorative diagonal caution strips near roof
for x in range(90,240,30):d.polygon([(x,26),(x+8,26),(x-4,51),(x-12,51)],fill=(219,171,75,105))
# Raster grain
im=im.filter(ImageFilter.GaussianBlur(.45))
path=Path(__file__).parent.parent/'src/main/resources/assets/svextract/textures/gui/operations_hub.png'
path.parent.mkdir(parents=True,exist_ok=True);im.save(path,optimize=True)
print(path, path.stat().st_size)
