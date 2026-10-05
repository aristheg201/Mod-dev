"""Original pixel-painted medieval character skins for the articulated 3D meshes.
These are game textures, never QA screenshots. Deterministic palettes allow review and recreation.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import random
out=Path('src/main/resources/assets/worldcomesalive/textures/entity/citizen');out.mkdir(parents=True,exist_ok=True)
for female in [False,True]:
 for variant in range(4):
  r=random.Random(variant+int(female)*18)
  skin=[(219,169,127),(177,117,77),(117,75,52),(234,193,155)][variant]
  hair=[(70,40,22),(162,96,34),(36,26,23),(181,158,101)][variant]
  cloth=([(67,102,128),(118,64,82),(65,104,78),(126,88,51)] if female else [(70,94,68),(80,88,123),(135,85,40),(97,71,112)])[variant]
  im=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(im)
  def panel(box,base):
   for y in range(box[1],box[3]):
    for x in range(box[0],box[2]):
     shade=r.choice([-8,-4,0,0,0,4,8]);d.point((x,y),tuple(max(0,min(255,v+shade)) for v in base)+(255,))
  panel((0,0,32,16),skin);panel((32,0,64,16),hair)
  # Front face on standard skin UVs: brows, eyes and a restrained mouth; no villager nose.
  d.rectangle((8,8,15,10),fill=hair);d.point((9,11),fill=(245,235,217));d.point((14,11),fill=(245,235,217));d.point((10,11),fill=(54,76,67));d.point((13,11),fill=(54,76,67));d.point((11,13),fill=tuple(max(0,v-18) for v in skin));d.line((10,14,13,14),fill=(134,75,63) if female else (122,83,61))
  panel((16,16,40,32),cloth);panel((40,16,56,32),cloth);panel((32,48,48,64),cloth)
  # Laced tunic with a light linen collar, cuffs and wrist skin.
  d.rectangle((20,20,27,22),fill=(205,190,153));d.line((23,22,23,28),fill=(46,37,29));d.point((24,24),fill=(189,160,104));d.point((24,26),fill=(189,160,104))
  panel((44,28,48,32),skin);panel((36,60,40,64),skin)
  panel((0,16,16,32),(73,58,44));panel((16,48,32,64),(73,58,44))
  panel((16,32,40,36),(67,43,26));d.rectangle((22,33,25,34),fill=(194,160,79))
  panel((16,36,42,44),cloth);panel((0,32,20,44),(58,38,24))
  im.save(out/f'{"female" if female else "male"}_{variant}.png')
