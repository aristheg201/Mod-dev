#!/usr/bin/env python3
import fcntl,json,subprocess,sys,tempfile,unittest,zipfile
from pathlib import Path
from install_terrain_parts import install,PREFIX

class TerrainPartsTest(unittest.TestCase):
 def setUp(self):
  self.temporary=tempfile.TemporaryDirectory();self.root=Path(self.temporary.name);self.save=self.root/'world';self.save.mkdir();(self.save/'level.dat').write_bytes(b'keep-level');(self.save/'session.lock').write_bytes(b'lock');(self.save/'playerdata').mkdir();(self.save/'playerdata/test.dat').write_bytes(b'keep-player')
 def tearDown(self):self.temporary.cleanup()
 def parts(self,malformed=False,unsafe=False):
  manifest={'dimension':'svextract:skyrim','regionFiles':2,'expandedBytes':16384,'spawn':[0,65,0]};paths=[]
  for part in (1,2):
   path=self.root/f'part{part}.zip';name=PREFIX+f'r.{part}.0.mca';data=bytearray(8192)
   if malformed and part==1:data[:4]=bytes([0,0,2,1])
   with zipfile.ZipFile(path,'w') as archive:
    archive.writestr('svextract-terrain-part.json',json.dumps({'schemaVersion':1,'part':part,'parts':2,'manifest':manifest,'regions':[name]}));archive.writestr(name,data)
    if unsafe and part==1:archive.writestr('../level.dat',b'bad')
   paths.append(path)
  return paths
 def test_complete_parts_preserve_player_and_survival_data(self):
  install(self.parts(),self.save);self.assertEqual((self.save/'level.dat').read_bytes(),b'keep-level');self.assertEqual((self.save/'playerdata/test.dat').read_bytes(),b'keep-player');self.assertEqual(len(list((self.save/'dimensions/svextract/skyrim/region').glob('*.mca'))),2)
 def test_missing_or_duplicate_parts_cannot_install(self):
  parts=self.parts()
  for selection in [parts[:1],[parts[0],parts[0]]]:
   with self.assertRaises(ValueError):install(selection,self.save)
  self.assertFalse((self.save/'dimensions/svextract/skyrim').exists())
 def test_failed_region_validation_removes_partial_staging(self):
  with self.assertRaises(ValueError):install(self.parts(malformed=True),self.save)
  self.assertFalse((self.save/'dimensions/svextract/skyrim.importing').exists());self.assertFalse((self.save/'dimensions/svextract/skyrim').exists())
 def test_unsafe_paths_cannot_modify_survival_metadata(self):
  with self.assertRaises(ValueError):install(self.parts(unsafe=True),self.save)
  self.assertEqual((self.save/'level.dat').read_bytes(),b'keep-level')
 def test_existing_dimension_is_never_overwritten(self):
  target=self.save/'dimensions/svextract/skyrim';target.mkdir(parents=True);(target/'owned.txt').write_text('keep')
  with self.assertRaises(ValueError):install(self.parts(),self.save)
  self.assertEqual((target/'owned.txt').read_text(),'keep')
 def test_running_save_lock_blocks_import(self):
  parts=self.parts();code='import fcntl,sys; f=open(sys.argv[1],"r+b"); fcntl.lockf(f,fcntl.LOCK_EX); print("locked",flush=True); sys.stdin.read(1)'
  child=subprocess.Popen([sys.executable,'-c',code,str(self.save/'session.lock')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   self.assertEqual(child.stdout.readline().strip(),'locked')
   with self.assertRaisesRegex(ValueError,'running/locked'):install(parts,self.save)
  finally:child.communicate('\n',timeout=5)

if __name__=='__main__':unittest.main()
