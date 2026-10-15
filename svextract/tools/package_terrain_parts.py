#!/usr/bin/env python3
"""Package complete Anvil files into ordinary ZIPs under the Drive connector's per-file limit."""
from pathlib import Path
import hashlib,json,shutil,zipfile,math
root=Path(__file__).resolve().parents[1];directory=root/'build/deliverables'
source=directory/'SVEXTRACT-Skyrim-server-terrain-2026-10-15.zip'
directory=directory/'skyrim-regions';directory.mkdir(exist_ok=True)
with zipfile.ZipFile(source) as original:
 manifest=json.loads(original.read('dimensions/svextract/skyrim/svextract-map-source.json'))
 regions=[i for i in original.infolist() if i.filename.endswith('.mca')];assert len(regions)==164
 count=math.ceil(sum(i.compress_size for i in regions)/(90*1024*1024))
 groups=[[] for _ in range(count)];weights=[0]*count
 for info in sorted(regions,key=lambda i:i.compress_size,reverse=True):
  index=min(range(count),key=lambda i:weights[i]);groups[index].append(info);weights[index]+=info.compress_size
 manifest['expandedBytes']=sum(i.file_size for i in regions)
 reports=[]
 for index,group in enumerate(groups):
  out=directory/f'SVEXTRACT-Skyrim-server-regions-{index+1}-of-{count}-2026-10-15.zip';temporary=out.with_suffix('.zip.part')
  descriptor={'schemaVersion':1,'part':index+1,'parts':count,'manifest':manifest,'regions':[i.filename for i in group]}
  with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as target:
   target.writestr('svextract-terrain-part.json',json.dumps(descriptor,indent=2))
   target.writestr('dimensions/svextract/skyrim/svextract-map-source.json',json.dumps(manifest,indent=2))
   target.writestr('READ-ME-SERVER-INSTALL.txt','SERVER DATA ONLY. All region archives are required. Stop the server and back up its save. Use tools/install_terrain_parts.py from the source delivery with --save WORLD --parts-directory DIRECTORY_WITH_ALL_PARTS. It validates the complete region set and refuses running saves or existing Skyrim worlds. Clients need no terrain archive.\n')
   for info in group:
    with original.open(info) as incoming,target.open(info.filename,'w') as outgoing:shutil.copyfileobj(incoming,outgoing,64*1024)
  temporary.replace(out);assert out.stat().st_size<100*1024*1024
  with out.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
  reports.append({'file':out.name,'bytes':out.stat().st_size,'sha256':digest,'regions':len(group)})
record={'schemaVersion':1,'parts':reports,'totalRegions':164,'installTogether':True}
(directory/'SVEXTRACT-Skyrim-terrain-parts.manifest.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))
