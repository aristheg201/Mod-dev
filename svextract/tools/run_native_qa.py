#!/usr/bin/env python3
"""Launch a real production Fabric client from Loom-downloaded Minecraft files in an isolated directory.
Requires build remapQaJar exportQaClasspath -PcompatQa -PqaScript and a local QA server.
Uses an offline test username only; never connects to a production host.
"""
import argparse,json,os,shutil,subprocess,socket
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--java',default='java')
parser.add_argument('--display',default=':99')
parser.add_argument('--delay-handshake',action='store_true',help='Delay the QA client response by 40 ticks to test recovery-before-handshake ordering')
parser.add_argument('--stash',choices=['deposit','withdraw'],default='',help='Exercise native stash ownership transfers using a prepared isolated firearm fixture')
parser.add_argument('--companion',action='store_true',help='Exercise companion assignment with a previously prepared one-Pokemon loadout')
parser.add_argument('--gunfire',action='store_true',help='Exercise normal Point Blank attack/reload key input inside the native raid')
parser.add_argument('--manual',action='store_true',help='Leave the real client under ordinary keyboard/mouse control')
parser.add_argument('--mode',choices=['training','operations','versus'],default='training')
parser.add_argument('--scavenge-extract',action='store_true',help='Physically open a supply cache and use free evacuation without completing optional research')
parser.add_argument('--without-cobblemon',action='store_true',help='Map inspection only: omit the optional Pokemon integration; this is not full-pack QA')
args=parser.parse_args()
try:
 with socket.create_connection(('127.0.0.1',25565),timeout=2):pass
except OSError:raise SystemExit('Start the isolated local QA server before launching this client; port 25565 is not listening.')
game=root/'run-modular-client';(game/'mods').mkdir(parents=True,exist_ok=True)
for jar in (game/'mods').glob('*.jar'):jar.unlink()
core=root/'build/libs/svextract-0.1.0-alpha.1.jar'
shutil.copyfile(core,game/'mods'/core.name)
for dependency in (root/'deps').glob('*.jar'):
 if args.without_cobblemon and 'cobblemon' in dependency.name.lower():continue
 shutil.copyfile(dependency,game/'mods'/dependency.name)
api=[p for p in (Path.home()/'.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.17+1.21.1').glob('*/*.jar') if not p.name.endswith('-sources.jar')]
if len(api)!=1:raise SystemExit('Expected the pinned Fabric API JAR in the Gradle cache')
shutil.copyfile(api[0],game/'mods'/api[0].name)
qa=root/'build/qa-remapped/svextract-native-qa-1.0.0.jar'
shutil.copyfile(qa,game/'mods'/qa.name)
options=game/'options.txt'
if not options.exists() and (root/'run-client/options.txt').exists():shutil.copyfile(root/'run-client/options.txt',options)
if options.exists():
 text=options.read_text()
 for key,value in {'maxFps':'30','renderDistance':'5','simulationDistance':'5','graphicsMode':'0','tutorialStep':'none','key_key.cobblemon.throwpartypokemon':'key.keyboard.h','enableVsync':'false'}.items():
  lines=text.splitlines();found=False
  for i,line in enumerate(lines):
   if line.startswith(key+':'):lines[i]=key+':'+value;found=True
  if not found:lines.append(key+':'+value)
  text='\n'.join(lines)+'\n'
 options.write_text(text)
loom=Path.home()/'.gradle/caches/fabric-loom'
classpath=[str(loom/'1.21.1/minecraft-client.jar')]
intermediary=root/'run-production/libraries/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.jar'
if not intermediary.exists():raise SystemExit('Run the production Fabric QA server first to install official intermediary mappings.')
classpath.append(str(intermediary))
for entry in (root/'build/qa-classpath.txt').read_text().split(os.pathsep):
 if '/modules-2/files-2.1/' in entry and '/dev.svextract.private/' not in entry and '/net.fabricmc.fabric-api/' not in entry and 'fabric-dev-launch-injector' not in entry:
  if Path(entry).exists():classpath.append(entry)
info=json.loads((loom/'1.21.1/mojang_minecraft_info.json').read_text())
indexes=list((loom/'assets/indexes').glob('*'+info['assetIndex']['id']+'*.json'))
if not indexes:raise SystemExit('Run Gradle downloadAssets first.')
asset_index=indexes[0].stem
command=[args.java,'-Dsvextract.qa.delayHandshake='+str(args.delay_handshake).lower(),'-Dsvextract.qa.stash='+args.stash,'-Xmx3G','-XX:ActiveProcessorCount=2','-Dsvextract.qa.scavenge='+str(args.scavenge_extract).lower(),'-Dsvextract.qa.mode='+args.mode,'-Dsvextract.qa.companion='+str(args.companion).lower(),'-Dsvextract.qa.manual='+str(args.manual).lower(),'-Dsvextract.qa.gunfire='+str(args.gunfire).lower(),'-cp',os.pathsep.join(classpath),
 'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','SVExtractQA','--version','1.21.1',
 '--gameDir',str(game),'--assetsDir',str(loom/'assets'),'--assetIndex',asset_index,
 '--accessToken','0','--userType','legacy','--quickPlayMultiplayer','127.0.0.1:25565','--width','1280','--height','720']
env=os.environ.copy();env.update(DISPLAY=args.display,LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='2')
raise SystemExit(subprocess.call(command,cwd=game,env=env))
