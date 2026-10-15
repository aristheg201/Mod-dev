#!/usr/bin/env python3
"""Package the immutable imported author terrain as server dimension data, never as a client mod/resource pack."""
from pathlib import Path
import hashlib,json,re,zipfile
root=Path(__file__).resolve().parents[1]
source=root/'run-production/world/dimensions/svextract/skyrim'
manifest=json.loads((source/'svextract-map-source.json').read_text())
regions=sorted((source/'region').glob('*.mca'))
assert len(regions)==manifest['regionFiles']==164
assert all(not path.is_symlink() and re.fullmatch(r'r\.-?\d+\.-?\d+\.mca',path.name) for path in regions)
out=root/'build/deliverables/SVEXTRACT-Skyrim-server-terrain-2026-10-15.zip';out.parent.mkdir(parents=True,exist_ok=True)
temporary=out.with_suffix('.zip.part')
readme='''SVEXTRACT - Skyrim author-world server data
This is SERVER WORLD DATA. Do not put it in mods or resourcepacks and do not install it on clients.
Stop the destination server. Back up its complete save. Extract the dimensions/ folder into the server's selected world save directory.
If dimensions/svextract/skyrim already exists, preserve it as a backup before applying this pack. Do not overwrite unrelated dimensions, level.dat or playerdata.
Start the server with the current SVEXTRACT core and its external dependencies. Wait for the Skyrim hub preparation log, then use Worlds / Hubs or /svextract skyrim.
Author map: Skyrim by fonta59, 1.20 Blackreach update. Source archive SHA256 is in the supplied source manifest.
This installs protected author terrain and Mission Control; it does not turn the full Skyrim map into finished extraction missions.
The pack contains region terrain and provenance only. No player data, prior test entities, datapacks or command functions are included.
'''
with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as archive:
 archive.writestr('READ-ME-SERVER-INSTALL.txt',readme)
 archive.write(source/'svextract-map-source.json','dimensions/svextract/skyrim/svextract-map-source.json')
 for file in regions:archive.write(file,'dimensions/svextract/skyrim/region/'+file.name)
temporary.replace(out)
digest=hashlib.file_digest(out.open('rb'),'sha256').hexdigest()
record={'archive':out.name,'bytes':out.stat().st_size,'sha256':digest,'regionFiles':len(regions),'sourceArchiveSha256':manifest['sourceArchiveSha256'],'serverOnly':True}
out.with_suffix('.manifest.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record))
