#!/usr/bin/env python3
"""Build pinned SVFrame source in a fresh private workspace. Never changes an existing checkout."""
import argparse, subprocess, shutil, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REPO='https://github.com/aristheg201/Cobblemon-MMO'
PINS={
 'SVFrameMMO':('svframemmo-fabric-1.21.1','6baffdb0bda1598b04e104d5d23c18217222ab78'),
 'SVFrameItems':('svframeitems-fabric-1.21.1','09ab5eceabe7bef92701227ec1206141fe5c23f7'),
 'SVFrameMobs':('svframemobs-fabric-1.21.1','76a2daa88b48bc13e56ed8d6da9befd01c30dabc'),
}
p=argparse.ArgumentParser();p.add_argument('--gradle',required=True,help='Path to Gradle 8.10.2 executable');p.add_argument('--beconomy',type=Path,required=True);p.add_argument('--source-dir',type=Path,default=ROOT/'deps/source-build');a=p.parse_args()
if not a.beconomy.is_file():p.error('Supply the BEconomy JAR from your own installation')
if a.source_dir.exists() and any(a.source_dir.iterdir()):p.error('source-dir must be new or empty; existing projects are never overwritten')
if not shutil.which(a.gradle):p.error('Gradle executable is unavailable')
a.source_dir.mkdir(parents=True,exist_ok=True);(ROOT/'deps').mkdir(exist_ok=True)
def run(args,cwd=None):subprocess.run([str(v) for v in args],cwd=cwd,check=True)
for name,(branch,revision) in PINS.items():
 dest=a.source_dir/name;run(['git','clone','--single-branch','--branch',branch,REPO,dest]);run(['git','checkout','-B','feature/svextract-private',revision],dest)
 if name in ('SVFrameMMO','SVFrameMobs'):
  patch=ROOT/'tools/patches'/('svframemmo-shutdown-save.patch' if name=='SVFrameMMO' else 'svframemobs-mission-spawn.patch');run(['git','apply','--check',patch],dest);run(['git','apply',patch],dest)
def build(folder,checks=()):run([a.gradle,'-Dorg.gradle.jvmargs=-Xmx2G','--max-workers=2','--console=plain','build',*checks],folder)
lib=a.source_dir/'SVFrameMMO/SVFrameLib';build(lib);libjar=lib/'build/libs/SVFrameLib-1.7.2-fabric.jar';shutil.copy2(libjar,ROOT/'deps'/libjar.name)
for name,version,checks in [('SVFrameMMO','1.13.2-fabric',('nativeCoreSmoke',)),('SVFrameItems','0.1.1-fabric',('nativeCoreSmoke',)),('SVFrameMobs','0.1.0-fabric',())]:
 checkout=a.source_dir/name
 if name!='SVFrameMMO':
  libs=checkout/'SVFrameLib/build/libs';libs.mkdir(parents=True,exist_ok=True);shutil.copy2(libjar,libs/libjar.name)
 build(checkout/name,checks);artifact=checkout/name/'build/libs'/f'{name}-{version}.jar';shutil.copy2(artifact,ROOT/'deps'/artifact.name)
shutil.copy2(a.beconomy,ROOT/'deps/BEconomy-1.5.jar')
(ROOT/'deps/source-pins.json').write_text(json.dumps(PINS,indent=2)+'\n')
print('Built private integration dependencies. Run prepare_personal_repository.py, then the SVEXTRACT personalBundle build.')
