"""Apply only physical-card hooks AFTER the existing production/YGO restoration. Preserve duel/effect source."""
from pathlib import Path
import json,subprocess
here=Path(__file__).resolve().parent
if 'PhysicalCards.initialize(this)' not in Path('src/main/java/vn/svarcade/tcg/fabric/TcgMod.java').read_text():
 subprocess.run(['patch','--batch','--forward','-p1','-i',str(here/'physical.patch')],check=True)
for name,path in [('en_us','src/main/resources/assets/svarcade_tcg/lang/en_us.json'),('vi_vn','src/main/resources/assets/svarcade_tcg/lang/vi_vn.json'),('messages','src/main/resources/data/svarcade_tcg/messages.json')]:
 target=Path(path);data=json.loads(target.read_text());data.update(json.loads((here/(name+'.json')).read_text()));target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print('CARDWORLDS_PHYSICAL_SOURCE_READY heldStackAuthority=true captureStorageRedirect=true')
