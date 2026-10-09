#!/usr/bin/env python3
"""Merge only the RTP font/assets and one paper override; preserve every other entry byte-for-byte."""
import argparse,json,zipfile,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]/'resourcepack'
data={}
if a.base:
 with zipfile.ZipFile(a.base) as z:
  assert len(z.namelist())==len(set(z.namelist())), 'Duplicate entries in original pack'
  data={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
original=dict(data)
for f in root.rglob('*'):
 if f.is_file() and (not a.base or f.name!='pack.mcmeta'):data[f.relative_to(root).as_posix()]=f.read_bytes()
paper='assets/minecraft/models/item/paper.json'
model=json.loads(data.get(paper,b'{"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/paper"}}'))
overrides=model.setdefault('overrides',[])
assert not any(v.get('predicate',{}).get('custom_model_data')==7032100 for v in overrides), 'Custom model ID collision'
overrides.extend([{'predicate':{'custom_model_data':7032100},'model':'svrtp:item/invisible'},
                  {'predicate':{'custom_model_data':7032101},'model':'minecraft:item/paper'}])
data[paper]=json.dumps(model,ensure_ascii=False,separators=(',',':')).encode()
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
 for name,payload in sorted(data.items()):z.writestr(name,payload)
changed=[name for name,payload in original.items() if data[name]!=payload]
assert changed==([paper] if paper in original else []), changed
manifest={'originalSha256':hashlib.sha256(a.base.read_bytes()).hexdigest() if a.base else None,
          'sha256':hashlib.sha256(a.output.read_bytes()).hexdigest(),'preservedEntries':len(original)-len(changed),
          'changedEntries':changed,'addedEntries':sorted(set(data)-set(original))}
a.output.with_suffix('.manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest))
