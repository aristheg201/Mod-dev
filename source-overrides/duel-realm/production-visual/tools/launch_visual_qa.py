"""Launch the actual remapped mod in an isolated Fabric client for screenshot QA.

Environment overrides:
  CARDWORLDS_MINECRAFT_HOME  Minecraft directory containing versions/libraries/assets.
  CARDWORLDS_TEST_INSTANCE   Existing 1.21.1 instance used only as a dependency/world source.
  CARDWORLDS_JAVA            Java 21 executable.
  CARDWORLDS_HOST_JAR        Optional SVArcade host jar copied into the QA mods directory.
"""
from pathlib import Path
import json, os, shutil, subprocess, zipfile

root=Path(__file__).resolve().parents[1]
appdata=Path(os.environ.get('APPDATA', Path.home()))
mc=Path(os.environ.get('CARDWORLDS_MINECRAFT_HOME', appdata/'.minecraft'))
instance_env=os.environ.get('CARDWORLDS_TEST_INSTANCE')
instance=Path(instance_env) if instance_env else appdata/'curseforge/minecraft/Instances/test tft'
java=Path(os.environ.get('CARDWORLDS_JAVA', 'C:/Program Files/Java/jdk-21/bin/java.exe' if os.name=='nt' else 'java'))
runtime=root/'qa-runtime';runtime.mkdir(exist_ok=True)
mods=runtime/'mods';mods.mkdir(exist_ok=True)

def require(path:Path, label:str)->Path:
    if not path.exists(): raise RuntimeError(f'Missing {label}: {path}')
    return path

for name in ['Cobblemon-fabric-1.8.1+1.21.1.jar','fabric-api-0.116.17+1.21.1.jar',
             'mega_showdown-fabric-1.2.0+1.8.1+1.21.1-release.jar','architectury-13.0.11-fabric.jar',
             'accessories-fabric-1.1.0-beta.53+1.21.1.jar','owo-lib-0.12.15.4+1.21.jar']:
    target=mods/name
    if not target.exists(): shutil.copy2(require(instance/'mods'/name,name),target)

host=os.environ.get('CARDWORLDS_HOST_JAR')
if host: shutil.copy2(require(Path(host),'CARDWORLDS_HOST_JAR'),mods/Path(host).name)
elif (root/'libs/SVArcade-0.6.8.jar').exists(): shutil.copy2(root/'libs/SVArcade-0.6.8.jar',mods/'SVArcade-0.6.8.jar')

version=next(line.split('=',1)[1].strip() for line in (root/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
for source,destination in [(f'SVArcade-TCG-{version}.jar','CardWorlds.jar'),(f'SVArcade-TCG-{version}-qa-driver.jar','CardWorlds-QA.jar')]:
    shutil.copy2(require(root/'build/libs'/source,source),mods/destination)

world=runtime/'saves/cardworlds-qa';world.mkdir(parents=True,exist_ok=True)
if not (world/'level.dat').exists(): shutil.copy2(require(instance/'saves/New World/level.dat','QA seed level.dat'),world/'level.dat')
base=json.loads(require(mc/'versions/1.21.1/1.21.1.json','Minecraft 1.21.1 metadata').read_text())
fabric_meta=mc/'versions/fabric-loader-0.18.4-1.21.1/fabric-loader-0.18.4-1.21.1.json'
fabric=json.loads(require(fabric_meta,'Fabric 0.18.4 metadata').read_text())
paths=[];missing=[]
for lib in base['libraries']+fabric['libraries']:
    if lib.get('rules'):
        allowed=False
        for rule in lib['rules']:
            if rule.get('os',{}).get('name','windows')=='windows' and not rule.get('features'): allowed=rule['action']=='allow'
        if not allowed: continue
    artifact=lib.get('downloads',{}).get('artifact',{})
    parts=lib['name'].split(':')
    relative=artifact.get('path') or '/'.join([parts[0].replace('.','/'),parts[1],parts[2],parts[1]+'-'+parts[2]+('-'+parts[3] if len(parts)>3 else '')+'.jar'])
    path=mc/'libraries'/relative
    if path.exists(): paths.append(path)
    else: missing.append(str(path))
if missing: raise RuntimeError('Missing runtime libraries: '+str(missing))
paths.append(require(mc/'versions/1.21.1/1.21.1.jar','Minecraft 1.21.1 jar'))

natives=runtime/'natives';natives.mkdir(exist_ok=True)
for p in paths:
    if 'natives-windows' in p.name:
        with zipfile.ZipFile(p) as z:
            for n in z.namelist():
                if n.endswith('.dll'): (natives/Path(n).name).write_bytes(z.read(n))
(runtime/'options.txt').write_text('version:3955\nguiScale:3\nfullscreen:false\nmaxFps:60\nrenderDistance:5\nsimulationDistance:5\nparticles:1\nshowSubtitles:false\n',encoding='utf-8')
args=['-Xmx5G','-Dcardworlds.qa=true','-Djava.library.path='+str(natives),'-Dorg.lwjgl.librarypath='+str(natives),
      '-cp',os.pathsep.join(map(str,paths)),fabric['mainClass'],'--username','CardWorldsQA','--version','1.21.1',
      '--gameDir',str(runtime),'--assetsDir',str(mc/'assets'),'--assetIndex',base['assetIndex']['id'],
      '--uuid','19daa819-9ed8-472b-a589-182b7513ca5b','--accessToken','0','--userType','legacy','--versionType','release',
      '--width','1280','--height','720']
argfile=runtime/'launch.args';argfile.write_text('\n'.join('"'+arg.replace('\\','/').replace('"','\\"')+'"' for arg in args),encoding='utf-8')
creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
with (runtime/'console.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen([str(java),'@'+str(argfile)],cwd=runtime,stdout=log,stderr=subprocess.STDOUT,creationflags=creationflags)
(runtime/'client.pid').write_text(str(process.pid))
print('Started remapped Fabric client:',process.pid)
