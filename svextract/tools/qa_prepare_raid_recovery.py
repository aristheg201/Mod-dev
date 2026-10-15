#!/usr/bin/env python3
"""Prepare one ACTIVE ownership intent in the stopped owned QA fixture for a late-handshake regression.
No production path argument exists. This is an injected journal-boundary test, not a power-cut claim.
"""
from pathlib import Path
import datetime,io,json,socket,sqlite3,uuid
import nbtlib
root=Path(__file__).resolve().parents[1];fixture=root/'run-modular-server'
try:
 with socket.create_connection(('127.0.0.1',25565),timeout=1):pass
except OSError:pass
else:raise SystemExit('Stop the isolated QA server first')
player='e7b80d8b-6780-3fd8-a5cd-57168cf9520d';saved=nbtlib.load(fixture/'world/playerdata'/f'{player}.dat');assert str(saved['Dimension'])=='svextract:haven9'
connection=sqlite3.connect(fixture/'world/svextract/journal.sqlite')
assert connection.execute("SELECT COUNT(*) FROM raids WHERE state<>'CLOSED'").fetchone()[0]==0
assert connection.execute('SELECT COUNT(*) FROM stash_transfers WHERE closed=0').fetchone()[0]==0
snapshot=nbtlib.Compound({'inventory':saved['Inventory'],'selected':nbtlib.Int(saved.get('SelectedItemSlot',0)),'gamemode':nbtlib.Int(saved['playerGameType'])})
encoded=io.BytesIO();nbtlib.File(snapshot).write(encoded);data=encoded.getvalue();transaction=str(uuid.uuid4());at=datetime.datetime.now(datetime.timezone.utc).isoformat()
with connection:
 connection.execute("INSERT INTO raids(id,player,state,reserved,world,x,y,z,yaw,pitch,outcome,created,updated,mode,checkpoint) VALUES(?,?,'ACTIVE',?,'svextract:haven9',0,65,4,180,0,NULL,?,?,'operations',?)",(transaction,player,data,at,at,data))
 for event in ['RESERVED','ACTIVE']:connection.execute('INSERT INTO audit(id,event,at) VALUES(?,?,?)',(transaction,event,at))
connection.close()
r={'transaction':transaction,'injectedFixture':True,'expectedOutcome':'RECOVERED','delayedHandshakeTicks':40,'purpose':'Durable recovery completes before client handshake; deployment must subsequently unlock','runtimeVerified':False}
(root/'qa/reports/raid-delayed-handshake-recovery-20261015.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r))
