#!/usr/bin/env python3
"""Validate the evidence ledger and block production designation unless every required row is verified."""
import argparse,csv,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
STATUSES={'verified','implemented','unverified','blocked','failed','missing'}
def inspect(ledger,root=ROOT):
 errors=[];pending=[];ids=set()
 for row in ledger.get('requirements',[]):
  key=row.get('id','')
  if not key or key in ids:errors.append('Missing/duplicate requirement id: '+key)
  ids.add(key)
  if row.get('status') not in STATUSES:errors.append(key+': invalid status')
  if row.get('status')=='verified' and not row.get('evidence'):errors.append(key+': verified requires actual evidence')
  for ref in row.get('evidence',[]):
   path=(root/ref).resolve()
   if not path.is_relative_to(root.resolve()) or not path.is_file():errors.append(key+': missing or nonlocal evidence '+ref)
  if row.get('requiredForProduction') and row.get('status')!='verified':pending.append(key)
 if not ids:errors.append('Empty acceptance ledger cannot pass')
 return errors,pending
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--production',action='store_true');p.add_argument('--csv',type=Path);a=p.parse_args()
 data=json.loads((ROOT/'qa/acceptance/requirements.json').read_text());errors,pending=inspect(data)
 if a.csv:
  a.csv.parent.mkdir(parents=True,exist_ok=True)
  with a.csv.open('w',newline='') as f:
   w=csv.writer(f,lineterminator="\n");w.writerow(['id','requirement','status','requiredForProduction','evidence','note'])
   for r in data['requirements']:w.writerow([r['id'],r['requirement'],r['status'],r['requiredForProduction'],'; '.join(r['evidence']),r['note']])
 print(json.dumps({'requirements':len(data['requirements']),'ledgerErrors':errors,'productionBlockers':len(pending),'productionReady':not errors and not pending}))
 sys.exit(1 if errors or (a.production and pending) else 0)
