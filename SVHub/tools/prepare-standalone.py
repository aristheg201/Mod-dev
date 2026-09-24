from pathlib import Path
import shutil,json
source=Path.cwd()
variant=source.parent/'internal-build'/'SVArcade'
variant.mkdir(parents=True,exist_ok=True)
for dirname in ['src','tools']:
 for p in (source/dirname).rglob('*'):
  if not p.is_file() or '__pycache__' in p.parts:continue
  rel=str(p.relative_to(source)).replace('SVHub','SVArcade').replace('svhub','svarcade')
  dest=variant/rel;dest.parent.mkdir(parents=True,exist_ok=True)
  if p.suffix in ['.kt','.java','.json','.py','.md','.txt','.properties','.csv','.mcmeta']:
   try:
    s=p.read_text(encoding='utf-8').replace('SVHub','SVArcade').replace('svhub','svarcade').replace('SVHUB','SVARCADE')
    for suffix in ['_dbz_','_naruto_','-dbz','-hunter']:
     s=s.replace('svarcade'+suffix,'svhub'+suffix)
    # The standalone hotkey routes to its own arcade service.
    if p.name=='NativePlatformNetwork.kt':s=s.replace('else SVArcadeNetwork.open(player,"home",false)','else NativePlatform.open(player,"arcade")')
    dest.write_text(s,encoding='utf-8')
   except UnicodeDecodeError:shutil.copy2(p,dest)
  else:shutil.copy2(p,dest)
for name in ['build.gradle.kts','settings.gradle','gradle.properties']:
 s=(source/name).read_text(encoding='utf-8').replace('SVHub','SVArcade').replace('svhub','svarcade').replace('SVHUB','SVARCADE')
 if name=='gradle.properties':s=s.replace('mod_version=0.4.5','mod_version=0.6.8')
 (variant/name).write_text(s,encoding='utf-8')
# Publish each as a self-contained distribution; avoid accidental coinstallation.
for folder,other in [(source,'svarcade'),(variant,'svhub')]:
 p=folder/'src/main/resources/fabric.mod.json';d=json.loads(p.read_text(encoding='utf-8'));d['breaks']={other:'*'}
 p.write_text(json.dumps(d,ensure_ascii=False,indent=2),encoding='utf-8')
print(variant)
