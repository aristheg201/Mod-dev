#!/usr/bin/env python3
"""Reject blank, missing-texture, or obviously corrupt SVHub visual-smoke captures."""
from __future__ import annotations
import collections,math,pathlib,struct,sys,zlib
PNG=b"\x89PNG\r\n\x1a\n"
def paeth(a:int,b:int,c:int)->int:
    p=a+b-c;pa,pb,pc=abs(p-a),abs(p-b),abs(p-c)
    if pa<=pb and pa<=pc:return a
    if pb<=pc:return b
    return c
def pixels(path:pathlib.Path):
    data=path.read_bytes()
    if not data.startswith(PNG):raise ValueError(f"{path}: not a PNG")
    pos=len(PNG);width=height=bit_depth=color_type=interlace=None;payload=bytearray()
    while pos+12<=len(data):
        size=struct.unpack(">I",data[pos:pos+4])[0];kind=data[pos+4:pos+8];body=data[pos+8:pos+8+size];pos+=12+size
        if kind==b"IHDR":width,height,bit_depth,color_type,_compression,_filter,interlace=struct.unpack(">IIBBBBB",body)
        elif kind==b"IDAT":payload.extend(body)
        elif kind==b"IEND":break
    if width is None or height is None:raise ValueError(f"{path}: missing IHDR")
    if bit_depth!=8 or color_type not in (2,6) or interlace!=0:raise ValueError(f"{path}: unsupported PNG format bit_depth={bit_depth} color_type={color_type} interlace={interlace}")
    bpp=3 if color_type==2 else 4;stride=width*bpp;raw=zlib.decompress(bytes(payload));expected=height*(stride+1)
    if len(raw)!=expected:raise ValueError(f"{path}: decoded byte count {len(raw)} != {expected}")
    rows=[];prev=bytearray(stride);cursor=0
    for _y in range(height):
        filter_type=raw[cursor];cursor+=1;scan=bytearray(raw[cursor:cursor+stride]);cursor+=stride;recon=bytearray(stride)
        for x,value in enumerate(scan):
            left=recon[x-bpp] if x>=bpp else 0;up=prev[x];upper_left=prev[x-bpp] if x>=bpp else 0
            if filter_type==0:out=value
            elif filter_type==1:out=value+left
            elif filter_type==2:out=value+up
            elif filter_type==3:out=value+((left+up)//2)
            elif filter_type==4:out=value+paeth(left,up,upper_left)
            else:raise ValueError(f"{path}: unknown PNG filter {filter_type}")
            recon[x]=out&0xFF
        rows.append(recon);prev=recon
    step_x=max(1,width//320);step_y=max(1,height//180);colors=collections.Counter();luminance=[];magenta=dark=sampled=0
    for y in range(0,height,step_y):
        row=rows[y]
        for x in range(0,width,step_x):
            offset=x*bpp;r,g,b=row[offset],row[offset+1],row[offset+2];colors[(r,g,b)]+=1;sampled+=1
            luminance.append((.2126*r+.7152*g+.0722*b)/255.0)
            if r>=220 and g<=45 and b>=180:magenta+=1
            if r<=28 and g<=28 and b<=28:dark+=1
    mean=sum(luminance)/len(luminance);variance=sum((value-mean)**2 for value in luminance)/len(luminance)
    return width,height,mean,math.sqrt(variance),colors.most_common(1)[0][1]/sampled,len(colors),magenta/sampled,dark/sampled
def main(argv:list[str])->int:
    if len(argv)<2:raise SystemExit("usage: verify-visual-smoke.py <png> [<png> ...]")
    failures=[]
    for raw in argv[1:]:
        path=pathlib.Path(raw);width,height,mean,stdev,dominant,unique,magenta,dark=pixels(path)
        print(f"{path.name}: {width}x{height} mean={mean:.4f} stdev={stdev:.4f} dominant={dominant:.4f} unique_sampled={unique} missing_magenta={magenta:.4f}")
        if width<640 or height<360:failures.append(f"{path.name}: unexpectedly small framebuffer")
        if mean<=.04:failures.append(f"{path.name}: framebuffer is effectively black")
        if stdev<=.035:failures.append(f"{path.name}: framebuffer lacks visible scene contrast")
        if dominant>=.85:failures.append(f"{path.name}: one flat color dominates the framebuffer")
        if unique<=64:failures.append(f"{path.name}: too few sampled colors for a rendered scene")
        if magenta>=.012 and dark>=.04:failures.append(f"{path.name}: missing-texture magenta/black checker signature detected")
    if failures:
        for failure in failures:print("ERROR: "+failure,file=sys.stderr)
        return 1
    return 0
if __name__=="__main__":
    raise SystemExit(main(sys.argv))
