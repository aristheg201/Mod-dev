#!/usr/bin/env python3
"""Validate complete server terrain parts and atomically install a new owned dimension while stopped.
Python 3 on Linux/macOS. No level.dat/player data/entities/datapacks are copied or overwritten.
"""
import argparse,contextlib,fcntl,json,os,re,shutil,zipfile
from pathlib import Path
from install_world_map import validate_region

PREFIX='dimensions/svextract/skyrim/region/'
def install(parts,save,max_bytes=1536*1024*1024):
 save=Path(save).resolve();target=save/'dimensions/svextract/skyrim';staging=save/'dimensions/svextract/skyrim.importing'
 if len(parts)<2 or len(parts)>32:raise ValueError('A complete set of 2–32 terrain parts is required')
 if not (save/'level.dat').is_file() or not (save/'session.lock').is_file():raise ValueError('Use an initialized Minecraft save')
 for parent in [save/'dimensions',target.parent,target,staging]:
  if parent.is_symlink():raise ValueError('Symlinked destination rejected')
 if target.exists() or staging.exists():raise ValueError('Destination/staging exists; back it up first. Nothing is overwritten')
 with (save/'session.lock').open('r+b') as lock,contextlib.ExitStack() as stack:
  try:fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:raise ValueError('Save is running/locked; stop its server first')
  archives=[stack.enter_context(zipfile.ZipFile(p)) for p in parts]
  manifest=None;indices=set();entries={};expanded=0
  for archive in archives:
   if archive.getinfo('svextract-terrain-part.json').file_size>65536:raise ValueError('Part metadata exceeds budget')
   data=json.loads(archive.read('svextract-terrain-part.json'))
   if data.get('schemaVersion')!=1 or data.get('parts')!=len(parts) or data.get('part') not in range(1,len(parts)+1) or data['part'] in indices:raise ValueError('Invalid, missing or duplicate part')
   indices.add(data['part'])
   if manifest is None:manifest=data['manifest']
   elif manifest!=data['manifest']:raise ValueError('Parts have different provenance')
   expected=set(data['regions']);actual=set();seen=set()
   for info in archive.infolist():
    if info.filename in seen:raise ValueError('Duplicate archive member')
    seen.add(info.filename)
    if (info.external_attr>>16)&0o170000==0o120000:raise ValueError('Symlink ZIP member rejected')
    if not info.filename.startswith(PREFIX):
     if info.filename not in ['svextract-terrain-part.json','READ-ME-SERVER-INSTALL.txt','dimensions/svextract/skyrim/svextract-map-source.json']:raise ValueError('Unexpected ZIP member')
     continue
    name=info.filename[len(PREFIX):]
    if not re.fullmatch(r'r\.-?\d+\.-?\d+\.mca',name) or name in entries or info.file_size<8192 or info.file_size>128*1024*1024:raise ValueError('Invalid or duplicate region')
    actual.add(info.filename);entries[name]=(archive,info);expanded+=info.file_size
    if expanded>max_bytes:raise ValueError('Expanded terrain exceeds budget')
   if actual!=expected:raise ValueError('Part is missing its declared regions')
  if indices!=set(range(1,len(parts)+1)) or manifest.get('dimension')!='svextract:skyrim' or len(entries)!=manifest.get('regionFiles') or expanded!=manifest.get('expandedBytes'):raise ValueError('Incomplete or mismatched region set')
  spawn=manifest.get('spawn')
  if not isinstance(spawn,list) or len(spawn)!=3 or not all(type(x) is int for x in spawn) or abs(spawn[0])>29000000 or abs(spawn[2])>29000000 or not -63<=spawn[1]<=317:raise ValueError('Invalid arrival coordinates')
  staging.mkdir(parents=True)
  try:
   (staging/'region').mkdir()
   for name,(archive,info) in entries.items():
    destination=staging/'region'/name
    with archive.open(info) as incoming,destination.open('xb') as outgoing:
     remaining=info.file_size
     while remaining:
      chunk=incoming.read(min(65536,remaining))
      if not chunk:raise ValueError('Truncated region')
      outgoing.write(chunk);remaining-=len(chunk)
     if incoming.read(1):raise ValueError('Oversized region')
     outgoing.flush();os.fsync(outgoing.fileno())
    validate_region(destination)
   manifest=dict(manifest,status='installed_offline_not_runtime_verified')
   with (staging/'svextract-map-source.json').open('x') as out:out.write(json.dumps(manifest,indent=2)+'\n');out.flush();os.fsync(out.fileno())
   for directory in [staging/'region',staging]:
    handle=os.open(directory,os.O_RDONLY)
    try:os.fsync(handle)
    finally:os.close(handle)
   if target.exists():raise ValueError('Destination appeared during import; not overwritten')
   os.rename(staging,target)
   handle=os.open(target.parent,os.O_RDONLY)
   try:os.fsync(handle)
   finally:os.close(handle)
   return manifest
  except BaseException:shutil.rmtree(staging,ignore_errors=True);raise

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--save',required=True,type=Path);parser.add_argument('--part',action='append',type=Path);parser.add_argument('--parts-directory',type=Path);args=parser.parse_args()
 parts=args.part or (sorted(args.parts_directory.glob('SVEXTRACT-Skyrim-server-regions-*-of-*-2026-10-15.zip')) if args.parts_directory else [])
 print(json.dumps(install(parts,args.save),indent=2))
