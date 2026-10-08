#!/usr/bin/env python3
"""Package committed buildable source, reports and genuine runtime evidence."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
parser.add_argument('--evidence', type=Path, required=True)
args = parser.parse_args()
branch = subprocess.check_output(['git', 'branch', '--show-current'], cwd=root, text=True).strip()
if branch != 'feature/cobblemon-world-rpg-20261006':
    raise SystemExit('Refusing to package the wrong branch: ' + branch)
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
args.output.mkdir(parents=True, exist_ok=True)
version = next(line.split('=', 1)[1] for line in (root/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
stem = 'CobblemonWorld-' + version
jar = root/'build/libs'/(stem+'.jar')
if not jar.is_file(): raise SystemExit('Build the remapped JAR first')
shutil.copy2(jar, args.output/jar.name)

def archive(target, pairs):
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for source, name in sorted(pairs, key=lambda p:p[1]):
            info = zipfile.ZipInfo(name, (2026,10,8,0,0,0))
            info.external_attr = source.stat().st_mode << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, source.read_bytes())
repo = Path(subprocess.check_output(['git','rev-parse','--show-toplevel'],cwd=root,text=True).strip())
module = root.relative_to(repo)
names = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard',str(module)],cwd=repo,text=True).splitlines()
sources = [(repo/name,str(Path('cobblemon-world')/(repo/name).relative_to(root))) for name in names if (repo/name).is_file()]
archive(args.output/(stem+'-FULLSOURCE.zip'), sources)
reports = [root/'00_README_FIRST.md',root/'README.md',root/'docs/PRODUCTION_RUNTIME_QA.md',root/'docs/NPC_BINDING_HOTFIX_QA.md',root/'docs/PRODUCTION_CHANGELOG.md',root/'docs/PRODUCTION_BUILD.md',root/'docs/RUNTIME_DEPENDENCIES.json',*sorted((root/'docs/narrative').glob('*.md'))]
archive(args.output/(stem+'-REPORTS.zip'),[(p,str(p.relative_to(root))) for p in reports])
archive(args.output/(stem+'-VISUAL-QA.zip'),[(p,str(p.relative_to(args.evidence))) for p in args.evidence.rglob('*') if p.is_file()])
files = {p.name:{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size} for p in sorted(args.output.glob(stem+'*')) if p.suffix in {'.jar','.zip'}}
manifest = {'repository':'aristheg201/Mod-dev','branch':branch,'commit':commit,'module':'cobblemon-world','version':version,'files':files,'runtime_report':'docs/NPC_BINDING_HOTFIX_QA.md','campaign_baseline_report':'docs/PRODUCTION_RUNTIME_QA.md','tested_versions':{'minecraft':'1.21.1','fabric_loader':'0.18.4','fabric_api':'0.116.17+1.21.1','cobblemon':'1.8.1+1.21.1','java':'21.0.12.1+1'}}
(args.output/'RELEASE_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(args.output/'SHA256SUMS.txt').write_text(''.join(v['sha256']+'  '+k+'\n' for k,v in files.items()))
print(json.dumps(manifest,indent=2))
