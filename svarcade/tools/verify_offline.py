#!/usr/bin/env python3
"""Run dependency-free Java regression checks, NOT the complete release matrix."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'core/src/main/java'
TEST = ROOT / 'core/src/test/java'
OUT = ROOT / 'core/build/offline-verification'


def main():
    for executable in ('javac', 'java'):
        if not shutil.which(executable):
            raise RuntimeError(f'Java 21 {executable} is required')
    checks = sorted(TEST.glob('**/verification/*Checks.java'))
    checks = [p for p in checks if p.name != 'Checks.java']
    if not checks:
        raise RuntimeError('No regression checks found')
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    sourcepath = os.pathsep.join((str(MAIN), str(TEST)))
    subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                    '-sourcepath', sourcepath, '-d', str(OUT), *map(str, checks)], check=True, timeout=90)
    results = []
    for check in checks:
        name = '.'.join(check.relative_to(TEST).with_suffix('').parts)
        start = time.monotonic()
        run = subprocess.run(['java', '-ea', '-cp', str(OUT), name], text=True, capture_output=True, timeout=60)
        print(run.stdout, end='')
        if run.stderr:
            print(run.stderr, file=sys.stderr, end='')
        results.append({'class': name, 'passed': run.returncode == 0,
                        'seconds': round(time.monotonic() - start, 3), 'output': run.stdout + run.stderr})
    (OUT / 'report.json').write_text(json.dumps({'scope': 'targeted_offline_regressions_not_release',
                                               'results': results}, indent=2), encoding='utf-8')
    return 0 if all(r['passed'] for r in results) else 1


if __name__ == '__main__':
    sys.exit(main())
