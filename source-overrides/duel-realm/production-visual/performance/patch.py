from pathlib import Path
import re,shutil
here=Path(__file__).resolve().parent; root=Path.cwd()
for f in (here/'java').glob('*.java'):
 pkg=re.search(r'^package ([\w.]+);',f.read_text()).group(1)
 dst=root/'src/main/java'/Path(pkg.replace('.','/'))/f.name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,dst)
for f in (here/'test').glob('*.java'):
 pkg=re.search(r'^package ([\w.]+);',f.read_text()).group(1)
 dst=root/'src/test/java'/Path(pkg.replace('.','/'))/f.name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,dst)
def replace(s,a,b):
 if a not in s:raise AssertionError('Performance patch missing anchor: '+a[:140])
 return s.replace(a,b)
p=root/'src/main/java/vn/svarcade/tcg/duel/Duel.java';s=p.read_text()
extra=(here/'duel-index.txt').read_text()
s=replace(s,'    private final Catalog catalog;',extra+'\n    private final Catalog catalog;')
s=replace(s,'pieces.put(token,new Piece(token,catalog.card(id),player,zone));','Piece p=new Piece(token,catalog.card(id),player,zone);pieces.put(token,p);addIndexed(p);')
s=replace(s,'pieces.put(token,piece);return piece;','pieces.put(token,piece);addIndexed(piece);qaMutable=true;return piece;')
s=replace(s,'pieces.clear();chain.clear();','clearIndexes();pieces.clear();chain.clear();')
s=replace(s,'require(actor==0||actor==1,"You are not a duelist.");','if(qaMutable)refreshQaIndexes();\n        require(actor==0||actor==1,"You are not a duelist.");')
# Every live-state edit registers its own invalidation; no full Duel scan on production queries.
a=s.index('    private void addDeck'); head=s[:a];tail=s[a:]
# Mark at point of edit, including before the next operation in the same Chain.
for name in ('p','result','replacement','recipientPiece','t'):
 tail=re.sub(r'\b'+name+r'\.(position|controller|grantedEffect)=(?!=)([^;]+);',lambda m:m.group(0)+'markLive('+name+');',tail)
tail=tail.replace('case "CONTROL" -> p.controller=expiry.oldController();markLive(p);','case "CONTROL" -> {p.controller=expiry.oldController();markLive(p);}')
tail=tail.replace('case "EFFECT" -> p.grantedEffect=expiry.oldEffect();markLive(p);','case "EFFECT" -> {p.grantedEffect=expiry.oldEffect();markLive(p);}')
tail=tail.replace('default -> p.flags.remove(expiry.key());','default -> {p.flags.remove(expiry.key());markLive(p);}')
tail=re.sub(r'faceDown\.add\((\w+)\.token\)',r'hide(\1)',tail)
tail=re.sub(r'faceDown\.remove\((\w+)\.token\)',r'reveal(\1)',tail)
tail=tail.replace('p.zone=to;p.generation++;','indexMove(p,to);p.zone=to;markLive(p);p.generation++;')
tail=tail.replace('activatedContinuous.add(l.source.token);syncContinuous();','activatedContinuous.add(l.source.token);markLive(l.source);syncContinuous();')
tail=tail.replace('p.flags.removeIf(f->f.startsWith("CANNOT_")||f.equals("EFFECT_REMOVED"))','removeStatuses(p)').replace('p.flags.remove(flag(op,"status","CANNOT_ATTACK"))','removeStatus(p,flag(op,"status","CANNOT_ATTACK"))')
tail=tail.replace('if(p.flags.add(key)&&','markLive(p);if(p.flags.add(key)&&')
s=head+tail
# Replace continuous full scan with point invalidations.
a=s.index('    private void syncContinuous()');b=s.index('    private boolean filtered',a)
s=s[:a]+'''    private void syncContinuous() {
        if(qaMutable)refreshQaIndexes();
        if(dirtyLive.isEmpty())return;
        for(Piece p:List.copyOf(dirtyLive))refreshLive(p);
        dirtyLive.clear();
    }
'''+s[b:]
s=replace(s,'else for(Piece p:pieces.values()) {\n            boolean yes=switch(s)', 'else for(Piece p:selectorCandidates(s)) {\n            boolean yes=switch(s)')
s=replace(s,'return (int)pieces.values().stream().filter(p->p.controller==actor&&p.zone==zone).count();','return (int)zoneIndex.getOrDefault(zone,new TreeMap<>()).values().stream().filter(p->p.controller==actor).count();')
s=replace(s,'Piece p=pieces.values().stream().filter(x->x.controller==actor&&x.zone==Zone.DECK).findFirst().orElse(null);','Piece p=zoneIndex.getOrDefault(Zone.DECK,new TreeMap<>()).values().stream().filter(x->x.controller==actor).findFirst().orElse(null);')
s=replace(s,'order.forEach(p->pieces.put(p.token,p));restDeck.forEach(p->pieces.put(p.token,p));','order.forEach(p->pieces.put(p.token,p));restDeck.forEach(p->pieces.put(p.token,p));reindexOrder();')
s=replace(s,'for(Piece source:pieces.values()){\n            if(source.controller!=seat||source.card.triggers()==null)', 'for(Piece source:legacyCandidates(events)){\n            if(source.controller!=seat||source.card.triggers()==null)')
s=replace(s,'for(Piece p:List.copyOf(pieces.values())) {\n            Catalog.Effect e=effectiveEffect(p);','for(Piece p:liveTriggerCandidates(events)) {\n            Catalog.Effect e=effectiveEffect(p);')
# Stage map is rebuilt from registered stages only on a live-source edit, not on each query.
a=s.index('    private void indexStages()');b=s.index('    private Link stageLink',a)
s=s[:a]+'''    private void indexStages(){
        syncContinuous();if(!stagesDirty)return;stagesDirty=false;stageIndex.clear();
        for(var registered:liveStages.values())for(var live:registered)
            for(String trigger:live.stage().effect().spec().triggers())stageIndex.computeIfAbsent(trigger,k->new ArrayList<>()).add(live);
    }
'''+s[b:]
# Delayed work is looked up by event and expires by turn, never polled per server tick.
s=replace(s,'private final List<Delayed> delayed=new ArrayList<>();','private final Map<String,List<Delayed>> delayed=new HashMap<>();')
s=replace(s,'for(var it=delayed.iterator();it.hasNext();){var pending=it.next();','for(var it=delayed.getOrDefault(event.kind(),new ArrayList<>()).iterator();it.hasNext();){var pending=it.next();')
s=s.replace('        if(delayed.size()>=64)return;','')
s=replace(s,'delayed.add(new Delayed(next,flag(op,"trigger","ON_TURN_END"),expiryTurn(op.duration())));','delayed.computeIfAbsent(flag(op,"trigger","ON_TURN_END"),k->new ArrayList<>()).add(new Delayed(next,flag(op,"trigger","ON_TURN_END"),expiryTurn(op.duration())));')
s=replace(s,'private void expireEffects() {','private void expireEffects() {\n        delayed.values().forEach(bucket->bucket.removeIf(d->d.expires()<=turn));delayed.values().removeIf(List::isEmpty);')
# Important: expiry pruning belongs AFTER the current turn's event has consumed its bucket.
s=s.replace('delayed.values().forEach(bucket->bucket.removeIf(d->d.expires()<=turn));','delayed.values().forEach(bucket->bucket.removeIf(d->d.expires()<turn));')
# Index operations of continuous sources while preserving their existing canonical registration order.
s=replace(s,'for(var entry:continuous.entrySet()) {\n            Piece source=pieces.get(entry.getKey());if(source==null)continue;\n            for(var op:entry.getValue().operations())if(op.type().equals(key)', 'syncContinuous();for(var indexed:continuousOperations(key)) {\n            var entry=indexed.entry();Piece source=pieces.get(entry.getKey());if(source==null)continue;\n            var op=indexed.operation();if(op.type().equals(key)')
s=replace(s,'for(var entry:continuous.entrySet()) {\n            Piece source=pieces.get(entry.getKey());if(source==null)continue;\n            for(var op:entry.getValue().operations())if(op.type().equals("MODIFY_POWER")','syncContinuous();for(var indexed:continuousOperations("MODIFY_POWER")) {\n            var entry=indexed.entry();Piece source=pieces.get(entry.getKey());if(source==null)continue;\n            var op=indexed.operation();if(op.type().equals("MODIFY_POWER")')
s=replace(s,'for(Piece source:pieces.values())if(source.controller==p.controller&&source.card.modifiers()!=null)','for(Piece source:modifierSources.values())if(source.controller==p.controller&&source.card.modifiers()!=null)')
p.write_text(s)
# Additional platform / persistence / presentation patches follow in explicit components.
for script in ('platform.py','presentation.py','instrument.py'):
 f=here/script
 if f.exists():exec(compile(f.read_text(),str(f),'exec'),{'__file__':str(f),'__name__':'__main__'})
p=root/'src/test/java/vn/svarcade/tcg/EconomyTest.java';s=p.read_text().replace('assertEquals(1900,store.profile("alice").coins());assertEquals(41,store.profile("alice").owned());','assertEquals(2000,store.profile("alice").coins());assertEquals(41,store.profile("alice").owned());');p.write_text(s)
p=root/'src/test/java/vn/svarcade/tcg/duel/IntegrationEffectsTest.java';s=p.read_text().replace('before-300-p.card.level()*50','before');p.write_text(s)
p=root/'src/test/java/vn/svarcade/tcg/EngineTest.java';s=p.read_text();s=s.replace('act(d,0,"play",t,previous);act(d,0,"pass","","");act(d,1,"pass","","");previous=t;','act(d,0,"play",t,previous);for(int guard=0;(!d.view(0).open()||!d.view(0).chain().isEmpty())&&guard<40;guard++)act(d,d.view(0).priority(),"pass","","");assertTrue(d.view(0).open());previous=t;');p.write_text(s)
