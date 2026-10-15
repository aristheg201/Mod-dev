#!/usr/bin/env python3
"""Inject a prepared-but-undelivered stash intent ONLY in this repository's stopped local QA fixture.
This models the durable boundary before inventory application; it does not simulate a power cut.
Requires the completed native deposit/withdraw fixture and nbtlib.
"""
from pathlib import Path
import copy,datetime,io,json,socket,sqlite3,uuid
import nbtlib

root=Path(__file__).resolve().parents[1]
fixture=root/'run-modular-server'
try:
 with socket.create_connection(('127.0.0.1',25565),timeout=1):pass
except OSError:pass
else:raise SystemExit('Stop the isolated QA server first')
player='e7b80d8b-6780-3fd8-a5cd-57168cf9520d'
path=fixture/'world/playerdata'/f'{player}.dat'
saved=nbtlib.load(path)
assert str(saved['Dimension'])=='svextract:haven9'
connection=sqlite3.connect(fixture/'world/svextract/journal.sqlite')
assert connection.execute("SELECT COUNT(*) FROM raids WHERE state<>'CLOSED'").fetchone()[0]==0
assert connection.execute('SELECT COUNT(*) FROM stash_transfers WHERE closed=0').fetchone()[0]==0
revision,stored=connection.execute('SELECT revision,items FROM stashes WHERE player=?',(player,)).fetchone()
stash=nbtlib.File.parse(io.BytesIO(stored));assert len(stash['inventory'])==0
before=nbtlib.Compound({'inventory':copy.deepcopy(saved['Inventory']),'selected':nbtlib.Int(saved.get('SelectedItemSlot',0)),'gamemode':nbtlib.Int(saved['playerGameType'])})
after=copy.deepcopy(before)
index=next(i for i,item in enumerate(after['inventory']) if str(item['id'])=='pointblank:glock17')
gun=after['inventory'].pop(index);gun['Slot']=nbtlib.Byte(0);stash['inventory']=nbtlib.List[nbtlib.Compound]([gun])
def encode(compound):
 buffer=io.BytesIO();nbtlib.File(compound).write(buffer);return buffer.getvalue()
transaction=str(uuid.uuid4())
with connection:
 connection.execute('INSERT INTO stash_transfers VALUES(?,?,?,?,?,?,?,0)',(transaction,player,revision,encode(before),encode(after),encode(stash),str(saved['Dimension'])))
 connection.execute('INSERT INTO audit(id,event,at) VALUES(?,?,?)',(transaction,'STASH_PREPARED',datetime.datetime.now(datetime.timezone.utc).isoformat()))
connection.close()
report={'transaction':transaction,'priorRevision':revision,'expectedRevision':revision+1,'failureBoundary':'Injected durable prepared intent before player inventory application; no power cut performed','expectedPlayerGunCount':0,'expectedStashGunCount':1}
p=root/'qa/reports/stash-injected-recovery-20261015.json';p.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
