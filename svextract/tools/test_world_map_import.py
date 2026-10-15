"""Offline import safety regressions. Run after installing requirements-map-tools.txt."""
import gzip,importlib.util,io,tempfile,unittest,zipfile,fcntl,subprocess,sys
from pathlib import Path
import nbtlib

spec=importlib.util.spec_from_file_location('importer',Path(__file__).with_name('install_world_map.py'))
importer=importlib.util.module_from_spec(spec);spec.loader.exec_module(importer)

def region(overlap=False,external=False):
 data=bytearray(12288);data[:4]=b'\x00\x00\x02\x01'
 if overlap:data[4:8]=data[:4]
 data[8192:8196]=(2).to_bytes(4,'big');data[8196]=130 if external else 2;data[8197]=0
 return data

class MapImportSafety(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
  self.save=self.root/'save';self.save.mkdir();(self.save/'level.dat').write_bytes(b'untouched')
  (self.save/'session.lock').touch();self.archive=self.root/'map.zip'
 def tearDown(self):self.tmp.cleanup()
 def make_zip(self,chunk=None,extra=None,metadata=None):
  stream=io.BytesIO();nbtlib.File({'Data':nbtlib.Compound({'SpawnX':nbtlib.Int(4),'SpawnY':nbtlib.Int(70),'SpawnZ':nbtlib.Int(5),'DataVersion':nbtlib.Int(3465)})}).write(stream)
  with zipfile.ZipFile(self.archive,'w') as z:
   z.writestr('Author/level.dat',metadata if metadata is not None else gzip.compress(stream.getvalue()))
   z.writestr('Author/region/r.0.0.mca',region() if chunk is None else chunk)
   z.writestr('Author/playerdata/private.dat',b'not imported')
   if extra:z.writestr(*extra)
 def install(self):return importer.install(self.archive,self.save,'skyrim','Author',1024*1024)
 def test_import_and_refuse_overwrite(self):
  self.make_zip();manifest=self.install();target=self.save/'dimensions/svextract/skyrim'
  self.assertEqual(manifest['spawn'],[4,70,5]);self.assertEqual(manifest['regionFiles'],1)
  self.assertFalse((target/'playerdata').exists());self.assertEqual((self.save/'level.dat').read_bytes(),b'untouched')
  with self.assertRaisesRegex(ValueError,'already exists'):self.install()
 def test_running_save_lock(self):
  self.make_zip()
  with (self.save/'session.lock').open('r+b') as lock:
   fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
   result=subprocess.run([sys.executable,str(Path(importer.__file__)),'--archive',str(self.archive),'--save',str(self.save),'--dimension','skyrim','--root','Author'],capture_output=True,text=True)
   self.assertNotEqual(result.returncode,0);self.assertIn('running/locked',result.stderr)
  self.assertFalse((self.save/'dimensions/svextract/skyrim').exists())
 def test_zip_traversal(self):
  self.make_zip(extra=('../escape',b'x'))
  with self.assertRaisesRegex(ValueError,'Unsafe ZIP'):self.install()
 def test_parent_symlink(self):
  self.make_zip();elsewhere=self.root/'elsewhere';elsewhere.mkdir();(self.save/'dimensions').symlink_to(elsewhere,target_is_directory=True)
  with self.assertRaisesRegex(ValueError,'symlink'):self.install()
  self.assertEqual(list(elsewhere.iterdir()),[])
 def test_invalid_regions_remove_staging(self):
  for chunk,message in [(region(overlap=True),'Overlapping'),(region(external=True),'External'),(region()[:-1],'aligned')]:
   self.make_zip(chunk=chunk)
   with self.assertRaisesRegex(ValueError,message):self.install()
   self.assertFalse((self.save/'dimensions/svextract/skyrim.importing').exists())
   self.assertFalse((self.save/'dimensions/svextract/skyrim').exists())
 def test_bounded_expanded_metadata(self):
  self.make_zip(metadata=gzip.compress(bytes(8*1024*1024+1)))
  with self.assertRaisesRegex(ValueError,'expanded budget'):self.install()
 def test_duplicate_entry(self):
  self.make_zip(extra=('Author/region/r.0.0.mca',region()))
  with self.assertRaisesRegex(ValueError,'Duplicate ZIP'):self.install()

if __name__=='__main__':unittest.main()
