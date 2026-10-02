from pathlib import Path
import re
base=Path('src/main/java/vn/svarcade/tcg')
def end_brace(s,start):
 depth=1;i=start+1;state='code'
 while i<len(s):
  c=s[i];n=s[i:i+2]
  if state=='code':
   if n=='//':state='line';i+=2;continue
   if n=='/*':state='block';i+=2;continue
   if c=='"':state='string'
   elif c=="'":state='char'
   elif c=='{':depth+=1
   elif c=='}':
    depth-=1
    if depth==0:return i
  elif state=='line':
   if c=='\n':state='code'
  elif state=='block':
   if n=='*/':state='code';i+=2;continue
  elif state in ('string','char'):
   if c=='\\':i+=2;continue
   if c==('"' if state=='string' else "'"):state='code'
  i+=1
 raise AssertionError('Unmatched Java brace')
def instrument(path,methods):
 s=path.read_text()
 for name,metric in methods.items():
  pattern=re.compile(r'^[ \t]*(?:public|private|protected|void)[^\n;{}=]*\b'+name+r'\([^{};]*?\)\s*(?:throws [\w,. ]+)?\s*\{',re.M)
  matches=list(pattern.finditer(s))
  for m in reversed(matches):
   a=m.end()-1;b=end_brace(s,a)
   s=s[:a+1]+f'long cwPerfNanos=vn.svarcade.tcg.performance.CardWorldsPerfStats.start();try{{'+s[a+1:b]+f'}}finally{{vn.svarcade.tcg.performance.CardWorldsPerfStats.finish(vn.svarcade.tcg.performance.CardWorldsPerfStats.Path.{metric},cwPerfNanos);}}'+s[b:]
 path.write_text(s)
instrument(base/'duel/Duel.java',{'act':'ACTION_VALIDATION','collectTriggers':'TRIGGER_LOOKUP','collectCompositeTriggers':'TRIGGER_LOOKUP','select':'TARGET_SELECTION','activate':'CHAIN_BUILD','stageLink':'CHAIN_BUILD','resolve':'CHAIN_RESOLVE','syncContinuous':'CONTINUOUS','compositePower':'CONTINUOUS'})
instrument(base/'performance/AiPlanner.java',{'plan':'AI_PLAN'})
instrument(base/'fabric/TcgMod.java',{'buildSnapshot':'SNAPSHOT_BUILD','placeholderValue':'PLACEHOLDER','handle':'MATCHMAKING'})
instrument(base/'economy/CardStore.java',{'tx':'DATABASE','scalar':'DATABASE','update':'DATABASE'})
instrument(base/'integration/BEconomyCardWorlds.java',{'chargePull':'ECONOMY','refundPull':'ECONOMY','rewardOnce':'ECONOMY','snapshotBalances':'ECONOMY'})
instrument(base/'data/Catalog.java',{'card':'CATALOG_LOOKUP'})
instrument(base/'client/card/CardRenderer.java',{'draw':'CARD_PREPARE'})
instrument(base/'client/render/DuelVfxTimeline.java',{'observe':'VFX_UPDATE','tick':'VFX_UPDATE'})

p=base/'duel/Duel.java';s=p.read_text().replace('for(var op:operations) {','for(var op:operations) {vn.svarcade.tcg.performance.CardWorldsPerfStats.operation();');p.write_text(s)

p=base/'duel/Duel.java';s=p.read_text();m=re.search(r'public synchronized void act\([^)]*\)\s*\{',s);a=m.end()-1;b=end_brace(s,a);s=s[:a+1]+'long cwEpochBefore=mutationEpoch;try{'+s[a+1:b]+'}finally{if(revision==expectedRevision&&mutationEpoch!=cwEpochBefore)revision++;}'+s[b:];p.write_text(s)
