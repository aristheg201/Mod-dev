#!/usr/bin/env python3
"""Store already-compressed nested JARs without changing any entry's content or signature bytes."""
import argparse,copy,hashlib,json,os,shutil,tempfile,time,zipfile
from pathlib import Path

def optimize(path):
 started=time.perf_counter();before=path.stat().st_size;digests={};nested=0
 fd,name=tempfile.mkstemp(prefix=path.name+'.',suffix='.staging',dir=path.parent);os.close(fd);staged=Path(name)
 try:
  with zipfile.ZipFile(path) as source,zipfile.ZipFile(staged,'w',allowZip64=True) as target:
   for original in source.infolist():
    info=copy.copy(original)
    if info.filename.startswith('META-INF/jars/') and info.filename.endswith('.jar'):
     info.compress_type=zipfile.ZIP_STORED;nested+=1
    digest=hashlib.sha256()
    with source.open(original) as reader,target.open(info,'w') as writer:
     while data:=reader.read(1024*1024):writer.write(data);digest.update(data)
    digests[info.filename]=digest.hexdigest()
  # Verify complete entry content, including nested publisher signatures, before replacing the JAR.
  with zipfile.ZipFile(staged) as archive:
   if len(archive.infolist())!=len(digests):raise ValueError('Duplicate or missing JAR entries')
   for entry in archive.infolist():
    with archive.open(entry) as reader:actual=hashlib.file_digest(reader,'sha256').hexdigest()
    if actual!=digests[entry.filename]:raise ValueError('Repack changed entry bytes: '+entry.filename)
  with staged.open('rb') as file:os.fsync(file.fileno())
  os.replace(staged,path)
  return {'nestedJarsStored':nested,'entriesVerified':len(digests),'beforeBytes':before,'afterBytes':path.stat().st_size,'elapsedSeconds':time.perf_counter()-started,'entryContentUnchanged':True}
 finally:staged.unlink(missing_ok=True)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('jar',type=Path);args=parser.parse_args();print(json.dumps(optimize(args.jar),indent=2))
