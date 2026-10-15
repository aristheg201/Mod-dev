#!/usr/bin/env python3
"""Archive tracked project and exact local SVFrame sources; omit build caches and credentials."""
import argparse,json,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def git(path,*args):return subprocess.check_output(['git','-C',str(path),*args])
def tracked(path):return [Path(n.decode()) for n in git(path,'ls-files','-z').split(b'\0') if n]

def export(output,integrations):
 output.parent.mkdir(parents=True,exist_ok=True);manifest={'schemaVersion':1,'repositories':{},'exclusions':['.git','untracked files','build caches','downloaded third-party binaries','author world archives and installed save data']}
 with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
  manifest['repositories']['svextract']=git(ROOT,'rev-parse','HEAD').decode().strip()
  for relative in tracked(ROOT):
   source=ROOT/relative
   if source.is_file():archive.write(source,Path('svextract')/relative)
  for name,repository in [('SVFrameLib','SVFrameMMO'),('SVFrameMMO','SVFrameMMO'),('SVFrameItems','SVFrameItems'),('SVFrameMobs','SVFrameMobs')]:
   checkout=integrations/repository
   if not (checkout/name).is_dir():raise ValueError('Missing local integration checkout: '+str(checkout/name))
   manifest['repositories'][name]=git(checkout,'rev-parse','HEAD').decode().strip()
   for relative in tracked(checkout):
    if relative.parts[0]!=name:continue
    source=checkout/relative
    if source.is_file():archive.write(source,Path('integrations')/name/relative.relative_to(name))
  archive.writestr('SOURCE-MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
 return manifest

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--integrations',type=Path,default=ROOT.parent/'integrations');p.add_argument('--output',type=Path,default=ROOT/'build/deliverables/SVEXTRACT-private-source-and-QA.zip');args=p.parse_args();print(json.dumps(export(args.output,args.integrations),indent=2))
