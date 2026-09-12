#!/usr/bin/env python3
"""Fail closed unless the single production scope and fresh evidence are complete."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def evaluate(manifest, root=ROOT):
    errors = []
    if manifest.get('schema') != 1 or manifest.get('release_scope') != 'single_complete_production_release':
        errors.append('Unknown manifest schema or release scope')
    requirements = manifest.get('requirements', [])
    if len(requirements) != 52 or {r.get('id') for r in requirements} != set(range(1, 53)):
        errors.append('The complete set of 52 unique requirements is mandatory')
    for row in requirements:
        if row.get('status') != 'complete':
            errors.append(f"R{row.get('id'):02}: {row.get('status', 'missing')}")
        elif not row.get('evidence'):
            errors.append(f"R{row['id']:02}: missing evidence")
        for file in row.get('evidence', []):
            path = (root / file).resolve()
            if not path.is_relative_to(root.resolve()) or not path.is_file():
                errors.append(f"Invalid evidence path: {file}")
    if manifest.get('known_blockers') or manifest.get('known_critical_defects'):
        errors.append('Known blockers or critical defects remain')
    suites = {'core', 'chess', 'chess_bots', 'td', 'td_bots', 'editor', 'compatibility', 'load_performance'}
    if set(manifest.get('required_suites', [])) != suites:
        errors.append('Required test matrix was changed or is incomplete')
    evidence_name = manifest.get('release_evidence')
    if not evidence_name:
        errors.append('Fresh release evidence has not been recorded')
        return errors
    path = (root / evidence_name).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        errors.append('Release evidence missing or outside project')
        return errors
    evidence = json.loads(path.read_text())
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if evidence.get('commit') != commit:
        errors.append('Release evidence is not for the checked-out commit')
    for suite in suites:
        result = evidence.get('suites', {}).get(suite, {})
        if result.get('status') != 'passed' or not result.get('report') or result.get('tests', 0) <= 0:
            errors.append(f'Missing successful evidence for {suite}')
        else:
            report = (root / result['report']).resolve()
            if not report.is_relative_to(root.resolve()) or not report.is_file():
                errors.append(f'Missing report file for {suite}')
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report-only', action='store_true', help='Print status during development; does not approve release')
    args = parser.parse_args()
    try:
        errors = evaluate(json.loads((ROOT / 'docs/release-requirements.json').read_text()))
    except (ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError) as exc:
        errors = [f'Invalid release evidence: {exc}']
    print('PRODUCTION RELEASE BLOCKED' if errors else 'PRODUCTION RELEASE GATE PASSED')
    for error in errors:
        print(f'- {error}')
    return 0 if args.report_only or not errors else 1


if __name__ == '__main__':
    sys.exit(main())
