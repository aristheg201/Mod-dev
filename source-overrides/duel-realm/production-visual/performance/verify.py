from pathlib import Path
import re
base=Path('src/main/java/vn/svarcade/tcg')
for f in base.rglob('*.java'):
 s=f.read_text()
 assert not re.search(r'CompletableFuture\.(?:supplyAsync|runAsync)\s*\(',s),f
 assert 'ForkJoinPool.commonPool' not in s,f
for name in ['CardWorldsExecutors','CardWorldsPerfStats','DuelReadSnapshot','CompiledEffects','SnapshotPipeline','PersistenceQueue','RewardReceiptJournal','ReadConnectionPool']:
 assert (base/'performance'/f'{name}.java').is_file(),name
s=(base/'duel/Duel.java').read_text()
assert 'liveTriggerCandidates(events)' in s and 'legacyCandidates(events)' in s
assert 'for(Piece p:pieces.values())' not in s[s.index('private void syncContinuous()'):s.index('private boolean filtered')]
assert 'maxTargetsForPerformance' not in s
assert 'private final Map<String,List<Delayed>> delayed' in s and 'delayed.iterator()' not in s
assert 'if(delayed.size()>=64)return' not in s
assert 'history=new vn.svarcade.tcg.performance.BoundedHistory' in s
assert 'if(Thread.currentThread()!=ownerThread)' in s
assert 'compiled.operation(op)' in s
for f in ['CollectionScreen','DeckBuilderScreen']:
 s=(base/'client/screens'/f'{f}.java').read_text()
 assert 'CollectionCache.first(' in s and 'CollectionCache.last(' in s,f
s=(base/'client/CardWorldsScreen.java').read_text();assert 'collectionCache.get(' in s
s=(base/'economy/CardStore.java').read_text();assert 'PRAGMA query_only=ON' in s and 'uiReaders.read(' in s
s=(base/'fabric/TcgMod.java').read_text();assert 'revision!=encoded.duelRevision()' in s and 'EconomyTransactions.pull(' in s
s=(base/'fabric/TcgPackets.java').read_text();assert 'SnapshotCompression.encode' not in s and 'SnapshotCompression.decode' not in s
s=(base/'fabric/TcgMod.java').read_text();assert 'request.terminal()' in s and 'terminalValid' in s

s=(base/'client/screens/PacksScreen.java').read_text();assert 'CollectionCache.first(' in s and 'CollectionCache.last(' in s
runner = Path('src/focusedQa/java/vn/svarcade/tcg/qa/FocusedEconomyEffectVisualRun.java').read_text()
assert 'withBanners(' not in runner and 'packIds.size() != 14' in runner
print('CARDWORLDS_PERFORMANCE_PREFLIGHT_OK pools=bounded gameplay_owner=main effects=indexed catalog=complete')
