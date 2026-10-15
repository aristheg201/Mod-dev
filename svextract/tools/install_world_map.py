#!/usr/bin/env python3
"""Install a validated author world archive into a new SVEXTRACT-owned dimension, only while stopped.
Never copies level.dat, player data, entities, datapacks, functions, forced tickets or plugin data.
Uses native Anvil region data; generation/data fixers remain Minecraft's responsibility.
"""
import argparse,hashlib,json,os,re,shutil,zipfile,fcntl,gzip,io
from pathlib import Path,PurePosixPath

def validate_region(path):
 """Reject overlapping/truncated chunks and external chunks whose .mcc is not imported."""
 length=path.stat().st_size
 if length<8192 or length%4096:raise ValueError('Anvil region is not sector aligned: '+str(path))
 with path.open('rb') as file:
  header=file.read(4096);used={0,1}
  for index in range(1024):
   offset=int.from_bytes(header[index*4:index*4+3],'big');sectors=header[index*4+3]
   if not offset and not sectors:continue
   if offset<2 or not sectors or (offset+sectors)*4096>length:raise ValueError('Invalid Anvil sector table: '+str(path))
   allocation=set(range(offset,offset+sectors))
   if used.intersection(allocation):raise ValueError('Overlapping Anvil chunk sectors: '+str(path))
   used.update(allocation);file.seek(offset*4096)
   chunk_length=int.from_bytes(file.read(4),'big');compression=file.read(1)[0]
   if chunk_length<1 or chunk_length>sectors*4096-4:raise ValueError('Invalid Anvil chunk length: '+str(path))
   if compression&128:raise ValueError('External .mcc chunks are not supported by this importer: '+str(path))
   if compression not in (1,2,3,4):raise ValueError('Unsupported Anvil compression: '+str(path))

def author_metadata(z,root):
 import nbtlib
 info=z.getinfo(root+'/level.dat')
 if info.file_size>1048576:raise ValueError('Author level metadata exceeds compressed budget')
 with gzip.GzipFile(fileobj=io.BytesIO(z.read(info))) as stream:raw=stream.read(8*1024*1024+1)
 if len(raw)>8*1024*1024:raise ValueError('Author level metadata exceeds expanded budget')
 data=nbtlib.File.parse(io.BytesIO(raw))['Data']
 spawn=[int(data.get(k,65 if k=='SpawnY' else 0)) for k in ['SpawnX','SpawnY','SpawnZ']]
 if abs(spawn[0])>29000000 or abs(spawn[2])>29000000 or not -63<=spawn[1]<=317:raise ValueError('Author spawn lies outside supported world bounds')
 return data,spawn

def install(archive,save,dimension,root,max_bytes):
 if not re.fullmatch(r'[a-z0-9_]+',dimension):raise ValueError('Invalid dimension path')
 source_root=PurePosixPath(root.rstrip('/'))
 if source_root.is_absolute() or '..' in source_root.parts or '\\' in root or not source_root.parts:raise ValueError('Invalid source root')
 root=source_root.as_posix()
 save=save.resolve();target=save/'dimensions/svextract'/dimension
 for parent in (save/'dimensions',target.parent,target,target.parent/(dimension+'.importing')):
  if parent.is_symlink():raise ValueError('Dimension destination may not be a symlink')
 if target.exists():raise ValueError('Target dimension already exists; no existing world is overwritten')
 if not (save/'level.dat').is_file():raise ValueError('Save must already be an isolated initialized Minecraft save')
 save.mkdir(parents=True,exist_ok=True)
 with (save/'session.lock').open('r+b') as lock:
  try:fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:raise ValueError('Minecraft save is running/locked; stop the server before import')
  with zipfile.ZipFile(archive) as z:
   candidates=[];expanded=0;names=set()
   for info in z.infolist():
    p=PurePosixPath(info.filename)
    if p.is_absolute() or '..' in p.parts or '\\' in info.filename:raise ValueError('Unsafe ZIP path')
    if info.filename in names:raise ValueError('Duplicate ZIP entry: '+info.filename)
    names.add(info.filename)
    if (info.external_attr>>16)&0o170000==0o120000:raise ValueError('ZIP symlinks are rejected')
    if not info.filename.startswith(root.rstrip('/')+'/'):continue
    rel=p.relative_to(root)
    if len(rel.parts)!=2 or rel.parts[0] != 'region' or not re.fullmatch(r'r\.-?\d+\.-?\d+\.mca',rel.name):continue
    if info.file_size<8192 or info.file_size>64*1024*1024:raise ValueError('Invalid bounded Anvil region size: '+info.filename)
    expanded+=info.file_size;candidates.append((info,rel))
   data,spawn=author_metadata(z,root)
   if not candidates:raise ValueError('No native Anvil regions found under source root')
   if expanded>max_bytes:raise ValueError('Map exceeds configured expanded import budget')
   free=shutil.disk_usage(save).free
   if free<expanded+512*1024*1024:raise ValueError('Insufficient disk space for bounded staging plus safety reserve')
   staging=target.parent/(dimension+'.importing')
   if staging.exists():raise ValueError('Prior import staging exists; inspect it before retrying')
   staging.mkdir(parents=True)
   try:
    for info,rel in candidates:
     dest=staging/rel;dest.parent.mkdir(exist_ok=True)
     with z.open(info) as source,dest.open('xb') as out:
      shutil.copyfileobj(source,out,1024*1024);out.flush();os.fsync(out.fileno())
     validate_region(dest)
    with archive.open('rb') as source:archive_hash=hashlib.file_digest(source,'sha256').hexdigest()
    manifest={'schemaVersion':1,'dimension':'svextract:'+dimension,'sourceArchiveSha256':archive_hash,'sourceRoot':root,'spawn':spawn,'dataVersion':int(data.get('DataVersion',0)),'regionFiles':len(candidates),'expandedBytes':expanded,'excluded':['level.dat','playerdata','entities','datapacks','functions','forcedchunks.dat','poi (regenerated by Minecraft)'],'status':'installed_offline_not_runtime_verified'}
    with (staging/'svextract-map-source.json').open('x') as out:
     out.write(json.dumps(manifest,indent=2)+'\n');out.flush();os.fsync(out.fileno())
    for directory in (staging/'region',staging):
     fd=os.open(directory,os.O_RDONLY)
     try:os.fsync(fd)
     finally:os.close(fd)
    os.rename(staging,target)
    fd=os.open(target.parent,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
   except BaseException:
    shutil.rmtree(staging,ignore_errors=True);raise
   return manifest
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--archive',type=Path,required=True);p.add_argument('--save',type=Path,required=True);p.add_argument('--dimension',required=True);p.add_argument('--root',required=True);p.add_argument('--max-expanded-mib',type=int,default=1536);a=p.parse_args()
 print(json.dumps(install(a.archive,a.save,a.dimension,a.root,a.max_expanded_mib*1024*1024),indent=2))
