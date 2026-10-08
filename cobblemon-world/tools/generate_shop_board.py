"""Generate the original merchant-board pixel texture using only Python's standard library."""
from pathlib import Path
import struct, zlib, random
W,H=560,340
pixels=[[(0,0,0,0) for _ in range(W)] for _ in range(H)]
def rect(x,y,w,h,c):
 for yy in range(max(0,y),min(H,y+h)):
  for xx in range(max(0,x),min(W,x+w)):pixels[yy][xx]=(*c,255)
def panel(x,y,w,h):
 rect(x+2,y+3,w,h,(62,38,22));rect(x,y,w,h,(145,109,62));rect(x+2,y+2,w-4,h-4,(229,211,169));rect(x+3,y+3,w-6,2,(250,234,193))
rect(5,6,W-5,H-6,(29,18,12));rect(0,0,W-5,H-5,(56,34,23));rect(4,4,W-13,H-13,(110,66,38));rect(7,7,W-19,H-19,(186,138,68));rect(10,10,W-25,H-25,(62,38,26));rect(16,16,W-37,H-37,(224,204,161))
random.seed(9821)
for y in range(16,H-21):
 for x in range(16,W-21):
  if random.random()<.07:
   v=random.choice([-5,4]);pixels[y][x]=(224+v,204+v,161+v,255)
for y in [4,8,H-12,H-8]:
 for x in range(12,W-20,17):rect(x,y,random.randrange(3,12),1,(104,63,38))
rect(20,17,516,38,(113,36,42));rect(20,18,516,2,(203,155,80));rect(20,53,516,2,(175,117,48))
# Woven red/gold border and metal corner fittings.
for x in range(20,W-25,10):
 rect(x,9,5,2,(183,139,69));rect(x,H-19,5,2,(183,139,69))
for x,y in [(7,7),(W-22,7),(7,H-22),(W-22,H-22)]:
 rect(x,y,13,13,(71,43,25));rect(x+2,y+2,9,9,(218,169,86));rect(x+4,y+4,5,5,(130,85,40));rect(x+5,y+5,3,3,(252,219,136))
panel(20,105,338,196);panel(367,105,164,213)
# Original stamped coin ornament beneath the category divider.
for x in range(20,536):rect(x,73,1,1,(165,125,69))
for dx,dy in [(0,-3),(-2,-1),(2,-1),(0,1)]:rect(355+dx,73+dy,2,2,(135,47,48))
raw=b''.join(b'\0'+bytes(v for p in row for v in p) for row in pixels)
def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/cobblemonworld/textures/gui/shop_board.png'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
