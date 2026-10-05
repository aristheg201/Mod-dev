"""Controlled dedicated-server QA; loads terrain, never places structures or NPCs."""
import socket,struct,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def read_exact(sock,n):
 data=b''
 while len(data)<n:
  part=sock.recv(n-len(data))
  if not part:raise RuntimeError('RCON connection closed')
  data+=part
 return data
def read(sock):
 n=struct.unpack('<i',read_exact(sock,4))[0]
 data=read_exact(sock,n)
 return struct.unpack('<ii',data[:8]),data[8:-2].decode('utf-8',errors='replace')
def packet(sock,ident,kind,text):
 data=struct.pack('<ii',ident,kind)+text.encode()+b'\0\0';sock.sendall(struct.pack('<i',len(data))+data)
def command(text):
 with socket.create_connection(('127.0.0.1',25575),timeout=10) as sock:
  sock.settimeout(30);packet(sock,1,3,'wca-local-qa')
  while True:
   (ident,kind),response=read(sock)
   if kind==2:
    assert ident==1,'Authentication failed';break
  packet(sock,2,2,text)
  while True:
   (ident,kind),response=read(sock)
   if ident==2:return response
if __name__=='__main__':
 import sys
 if len(sys.argv)>1:print(command(' '.join(sys.argv[1:])));raise SystemExit
 output={'generation':'automatic chunk discovery','manualNpcPlacement':False}
 output['loadTerrain']=command('forceload add -552 -168')
 save=ROOT/'run-wca-server/wca-dedicated-qa/world-comes-alive/world-comes-alive.json'
 deadline=time.monotonic()+90
 while time.monotonic()<deadline:
  if save.exists():
   state=json.loads(save.read_text())
   if len(state['npcs'])>=20:break
  time.sleep(2)
 else:raise RuntimeError('No automatically populated dedicated-server settlement')
 output['before']=command('wca status');command('wca save');before=json.loads(save.read_text());start=time.monotonic()
 time.sleep(20)
 output['tickQuery']=command('tick query');output['after']=command('wca status');command('wca save');after=json.loads(save.read_text())
 output['elapsedSeconds']=time.monotonic()-start;output['observedTPS']=(after['clock']-before['clock'])/output['elapsedSeconds'];output['npcs']=len(after['npcs']);output['identities']=sorted(after['npcs']);output['abstractDecisionsAdvanced']=after['decisions']>before['decisions'];output['transactionsAdvanced']=after['transactions']>=before['transactions']
 assert output['abstractDecisionsAdvanced'],'Unseen NPC lives froze';assert output['observedTPS']>15,'Dedicated server TPS degraded'
 (ROOT/'qa/dedicated-server-runtime.json').write_text(json.dumps(output,indent=2)+'\n');print(json.dumps(output,indent=2))
 command('stop')
