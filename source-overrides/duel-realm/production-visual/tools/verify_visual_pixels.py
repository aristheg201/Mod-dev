"""Pixel sanity checks complement (never replace) review of the rendered proof frames."""
import struct,zlib
from pathlib import Path

def pixels(path):
 data=path.read_bytes();pos=8;compressed=b''
 while pos<len(data):
  n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];body=data[pos+8:pos+8+n];pos+=n+12
  if kind==b'IHDR':width,height,depth,mode,_,_,interlace=struct.unpack('>IIBBBBB',body)
  if kind==b'IDAT':compressed+=body
 assert depth==8 and mode in (2,6) and not interlace,(path,'unsupported PNG format')
 bpp=3 if mode==2 else 4;stride=width*bpp;raw=zlib.decompress(compressed);previous=bytearray(stride);result=[]
 for y in range(height):
  start=y*(stride+1);f=raw[start];row=bytearray(raw[start+1:start+1+stride])
  for i in range(stride):
   a=row[i-bpp] if i>=bpp else 0;b=previous[i];c=previous[i-bpp] if i>=bpp else 0
   if f==1:delta=a
   elif f==2:delta=b
   elif f==3:delta=(a+b)//2
   elif f==4:
    p=a+b-c;pa,pb,pc=abs(p-a),abs(p-b),abs(p-c);delta=a if pa<=pb and pa<=pc else b if pb<=pc else c
   else:assert f==0;delta=0
   row[i]=(row[i]+delta)&255
  if y%8==0:
   for x in range(0,width,8):result.append(tuple(row[x*bpp:x*bpp+3]))
  previous=row
 return width,height,result

for path in sorted(Path('run/screenshots').glob('[0-9][0-9]-*.png')):
 width,height,rgb=pixels(path)
 assert width>=1280 and height>=720,(path,width,height)
 assert sum(max(c)>30 for c in rgb)>len(rgb)*.12,(path,'black/near-black frame')
 scene=int(path.name[:2]) in {12,13,14,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34}
 assert len(set(rgb))>(400 if scene else 40),(path,'missing rendered detail')
 print(path.name,'pixel evidence:',width,height,len(set(rgb)),'distinct sampled colors')
