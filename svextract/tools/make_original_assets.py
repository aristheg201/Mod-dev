#!/usr/bin/env python3
"""Deterministic original SVEXTRACT pixel sprite and evacuation chime. Not runtime QA images."""
from pathlib import Path
from PIL import Image, ImageDraw
import math, struct, wave, subprocess

root=Path(__file__).resolve().parents[1]/"src/main/resources/assets/svextract"
(root/"textures/item").mkdir(parents=True,exist_ok=True)
image=Image.new("RGBA",(16,16));draw=ImageDraw.Draw(image)
draw.rectangle((3,2,12,13),fill="#273544",outline="#98b5c5")
draw.rectangle((5,4,10,10),fill="#10222d",outline="#52d3d1")
draw.rectangle((6,5,9,8),fill="#8df5ee")
draw.rectangle((5,12,6,14),fill="#e8c66b");draw.rectangle((9,12,10,14),fill="#e8c66b")
draw.point((11,3),fill="#edfafe");image.save(root/"textures/item/data_core.png")
sound=root/"sounds";sound.mkdir(exist_ok=True)
temporary=sound/"evacuation.wav"
rate=22050
with wave.open(str(temporary),"wb") as audio:
 audio.setnchannels(1);audio.setsampwidth(2);audio.setframerate(rate)
 for i in range(int(rate*1.2)):
  t=i/rate;freq=660 if t<0.4 else (880 if t<0.8 else 990)
  envelope=min(1,(t%0.4)*40)*max(0,1-(t%0.4)/0.4)
  audio.writeframesraw(struct.pack("<h",int(math.sin(2*math.pi*freq*t)*envelope*7000)))
subprocess.run(["ffmpeg","-y","-loglevel","error","-i",str(temporary),"-c:a","libvorbis","-q:a","3",str(sound/"evacuation.ogg")],check=True)
temporary.unlink()
