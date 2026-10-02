from pathlib import Path
import re
here=Path(__file__).resolve().parent; base=Path('src/main/java/vn/svarcade/tcg')
def rep(s,a,b):
 if a not in s:raise AssertionError('Platform anchor missing '+a[:100])
 return s.replace(a,b)
p=base/'economy/CardStore.java';s=p.read_text();s=rep(s,'    private final Connection db;', (here/'store-data.txt').read_text()+'\n    private final Connection db;')
s=rep(s,'db.commit();return result;','db.commit();dataRevision++;uiCache.clear();return result;')
s=rep(s,'return new Profile(scalar("SELECT coins FROM profiles WHERE owner=?",owner),(int)scalar("SELECT reputation FROM profiles WHERE owner=?",owner),(int)scalar("SELECT rating FROM profiles WHERE owner=?",owner),(int)scalar("SELECT COUNT(*) FROM cards WHERE owner=?",owner));','try(var statement=statement("SELECT coins,reputation,rating,(SELECT COUNT(*) FROM cards WHERE owner=?) AS owned FROM profiles WHERE owner=?",owner,owner);var row=statement.executeQuery()){return row.next()?new Profile(row.getLong(1),row.getInt(2),row.getInt(3),row.getInt(4)):new Profile(0,0,0,(int)scalar("SELECT COUNT(*) FROM cards WHERE owner=?",owner));}')
p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text();s=s.replace('import static net.minecraft.server.command.CommandManager.literal;','import static net.minecraft.server.command.CommandManager.literal;\nimport vn.svarcade.tcg.performance.*;')
s=s.replace('private int tick;','''private int tick;
    private CardWorldsExecutors workers;private PerformanceConfig performance;
    private CoalescingPipeline<UUID,SnapshotPipeline.Wire,SnapshotPipeline.Encoded> snapshotPipeline;
    private final Map<UUID,SendRequest> sendRequests=new LinkedHashMap<>();
    private final Map<UUID,ReadJob> storeReads=new HashMap<>();
    private final Map<UUID,Long> contentSent=new HashMap<>();private long snapshotSequence,contentGeneration=1;
    private record SendRequest(String notice,int page){}
    private record StoreInput(CardStore store,String owner,int page,String peer){}
    private record StoreResult(StoreInput input,CardStore.UiData data,List<CardStore.Owned> peerBinder){}
    private record ReadJob(StoreInput input,java.util.concurrent.CompletableFuture<StoreResult> future){}
''')
s=s.replace('long aDisconnectedAt=-1L,bDisconnectedAt=-1L;','long aDisconnectedAt=-1L,bDisconnectedAt=-1L;final AsyncDerivedTask<List<Duel.Action>> ai=new AsyncDerivedTask<>();long aiSeed,aiRevision=-1;')
# Generate record copy helpers, without reflecting or serializing the giant static data on main thread.
a=s.index('public record Snapshot(');b=s.index('){}',a);params=s[a+len('public record Snapshot('):b]
parts=[];buf='';depth=0
for c in params:
 if c in '<(':depth+=1
 if c in '>)':depth-=1
 if c==',' and depth==0:parts.append(buf.strip());buf=''
 else:buf+=c
parts.append(buf.strip());names=[v.split()[-1] for v in parts]
def construct(mapping):return 'new Snapshot('+','.join(mapping.get(n,n) for n in names)+')'
methods=''' ){
        public Snapshot withContent(SnapshotPipeline.Content c){return '''+construct({'definitions':'c==null?null:c.definitions()','deckTemplates':'c==null?null:c.deckTemplates()','rules':'c==null?null:c.rules()'})+''';}
        public Snapshot withCounts(Map<String,Integer> value){return '''+construct({'counts':'value'})+''';}
        public Snapshot mergePresentation(Snapshot previous){
            List<CardStore.Pull> combined=new ArrayList<>(previous.pulls());for(var p:pulls)if(combined.stream().noneMatch(q->q.serial().equals(p.serial())))combined.add(p);
            String messages=previous.notice().isBlank()?notice:notice.isBlank()?previous.notice():previous.notice().equals(notice)?notice:previous.notice()+"\\n"+notice;
            return '''+construct({'pulls':'List.copyOf(combined)','notice':'messages','open':'open||previous.open()','duel':'mergeCues(previous.duel(),duel)'})+''';
        }
        private static Duel.View mergeCues(Duel.View old,Duel.View next){
            if(old==null||next==null||next.revision()<old.revision())return next;
            Map<Long,Duel.Cue> all=new TreeMap<>();old.cues().forEach(c->all.put(c.sequence(),c));next.cues().forEach(c->all.put(c.sequence(),c));
            return new Duel.View(next.revision(),next.you(),next.turnPlayer(),next.priority(),next.turn(),next.phase(),next.open(),next.life(),next.deckCounts(),next.handCounts(),next.extraCounts(),next.cards(),next.chain(),next.winner(),next.log(),List.copyOf(all.values()));
        }
    }'''
s=s[:b]+methods+s[b+4:]
s=rep(s,'Path config=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg");Files.createDirectories(config);','''Path config=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg");Files.createDirectories(config);
            performance=PerformanceConfig.load(config.resolve("performance.json"));CardWorldsPerfStats.enable(performance.perfMetrics()||Boolean.getBoolean("cardworlds.perf"));workers=new CardWorldsExecutors(performance);snapshotPipeline=new CoalescingPipeline<>(workers,CardWorldsExecutors.Kind.SERIALIZATION,SnapshotPipeline::encode,SnapshotPipeline::merge);''')
s=rep(s,'ServerLifecycleEvents.SERVER_STOPPING.register(server->{try{if(store!=null)store.close();}', 'ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var m:new HashSet<>(matches.values()))m.ai.cancel();if(snapshotPipeline!=null)snapshotPipeline.clear();storeReads.values().forEach(j->j.future().cancel(false));storeReads.clear();sendRequests.clear();contentSent.clear();if(workers!=null)workers.close();try{if(store!=null)store.close();}')
s=rep(s,'UUID id=handler.player.getUuid();Match m=matches.get(id);','UUID id=handler.player.getUuid();contentSent.remove(id);Match m=matches.get(id);')
a=s.index('ServerPlayConnectionEvents.DISCONNECT');b=s.index('CommandRegistrationCallback',a);seg=s[a:b].replace('Match m=matches.get(id);','Match m=matches.get(id);if(snapshotPipeline!=null)snapshotPipeline.cancel(id);sendRequests.remove(id);ReadJob read=storeReads.remove(id);if(read!=null)read.future().cancel(false);placeholderCache.remove(id);if(m!=null)m.ai.cancel();');s=s[:a]+seg+s[b:]
s=rep(s,'ServerTickEvents.END_SERVER_TICK.register(server->{if(++tick%20==0)', 'ServerTickEvents.END_SERVER_TICK.register(server->{long workStarted=CardWorldsPerfStats.start();try{pollAsync(server);if(++tick%20==0)')
s=rep(s,'if(!m.humanDisconnected()&&m.npc&&m.duel.winner()<0&&m.duel.view(1).priority()==1){bot(m);broadcast(server,m);}', 'if(!m.humanDisconnected()&&m.npc&&m.duel.winner()<0&&m.duel.priority()==1)bot(m);')
s=rep(s,'        }});\n    }\n    private String message', '''        }}finally{CardWorldsPerfStats.finish(CardWorldsPerfStats.Path.DUEL_TICK,workStarted);}if(CardWorldsPerfStats.enabled()&&tick%1200==0)org.slf4j.LoggerFactory.getLogger("cardworlds-perf").info(commandPerf());});
    }
    private String message''')
a=s.index('    private void bot(Match m)');b=s.index('    private void broadcast',a)
s=s[:a]+'''    private void bot(Match m){
        if(m.ai.pending())return;var view=m.duel.view(1);if(m.aiRevision!=view.revision()){m.aiRevision=view.revision();m.aiSeed=rng.nextLong();}
        var input=new DuelReadSnapshot(m.id,view.revision(),view,catalog.cards());String difficulty=m.botDifficulty;long seed=m.aiSeed;
        m.ai.begin(view.revision(),workers.submit(CardWorldsExecutors.Kind.CPU,new CardWorldsExecutors.Context("ai",m.id,view.revision()),()->AiPlanner.plan(input,difficulty,seed)));
    }
    private void pollAsync(MinecraftServer server){
        if(workers==null)return;
        for(Match m:new HashSet<>(matches.values()))if(m.ai.pending()){
            boolean live=m.duel.winner()<0&&!m.humanDisconnected()&&m.duel.priority()==1;
            List<Duel.Action> actions=null;try{actions=m.ai.take(m.duel.revision(),live);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("AI preparation failed duel="+m.id+" revision="+m.duel.revision(),ex);if(live)actions=AiPlanner.plan(new DuelReadSnapshot(m.id,m.duel.revision(),m.duel.view(1),catalog.cards()),m.botDifficulty,m.aiSeed);}
            if(actions!=null){for(var action:actions)try{m.duel.act(1,action,m.duel.revision());broadcast(server,m);break;}catch(IllegalArgumentException ignored){} }
            else if(live&&!m.ai.pending())bot(m);
        }
        for(var it=sendRequests.entrySet().iterator();it.hasNext();){var entry=it.next();UUID id=entry.getKey();var request=entry.getValue();var player=server.getPlayerManager().getPlayer(id);if(player==null){it.remove();continue;}
            String peer=tradePeers.containsKey(id)?tradePeers.get(id).toString():"";var input=new StoreInput(store,id.toString(),Math.max(0,request.page()),peer);ReadJob job=storeReads.get(id);
            if(job!=null&&!job.input().equals(input)){job.future().cancel(false);storeReads.remove(id);job=null;}
            if(job==null){var captured=input;storeReads.put(id,new ReadJob(input,workers.submit(CardWorldsExecutors.Kind.IO,new CardWorldsExecutors.Context("store-read",id.toString(),store.dataRevision()),()->new StoreResult(captured,captured.store().uiData(captured.owner(),captured.page()),captured.peer().isBlank()?List.of():captured.store().binder(captured.peer(),captured.page())))));continue;}
            if(!job.future().isDone())continue;storeReads.remove(id);StoreResult result;
            try{result=job.future().join();}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("Store read failed owner="+id,ex);result=new StoreResult(input,store.uiData(input.owner(),input.page()),input.peer().isBlank()?List.of():store.binder(input.peer(),input.page()));}
            if(result.data().revision()!=store.dataRevision())continue;
            buildSnapshot(player,request.notice(),request.page(),result);it.remove();
        }
        snapshotPipeline.poll((id,encoded)->{var player=server.getPlayerManager().getPlayer(id);if(player==null)return;ServerPlayNetworking.send(player,new TcgPackets.Snapshot(encoded.bytes()));if(encoded.content())contentSent.put(id,encoded.generation());},(id,error)->org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("Snapshot preparation failed owner="+id,error));
    }
    String commandPerf(){return "active duels="+new HashSet<>(matches.values()).size()+" "+(workers==null?"workers=stopped":workers.summary())+" "+CardWorldsPerfStats.summary()+" AI="+matches.values().stream().distinct().filter(m->m.ai.pending()).count()+" database="+storeReads.size()+" store-cache="+(store==null?0:store.uiCacheSize())+" catalog-cache="+CatalogIndex.size();}
''' +s[b:]
# send() becomes a derived request coalesced within the same tick; no authoritative action is combined.
a=s.index('    private void send(');b=s.index('    MessageService commandMessages',a);seg=s[a:b]
seg=rep(seg,'private void send(ServerPlayerEntity p,String notice,int page) {','''private void send(ServerPlayerEntity p,String notice,int page) {
        var old=sendRequests.get(p.getUuid());String combined=old==null||old.notice().isBlank()?notice:notice.isBlank()?old.notice():old.notice().equals(notice)?notice:old.notice()+"\\n"+notice;
        sendRequests.put(p.getUuid(),new SendRequest(combined,page));
    }
    private void buildSnapshot(ServerPlayerEntity p,String notice,int page,StoreResult result) {
        var data=result.data();''')
a=seg.index('String owner=');b=seg.index('        Match m=',a)
seg=seg[:a]+'''String owner=p.getUuidAsString();List<BinderCard> binder=data.binder().stream().map(c->{var d=catalog.card(c.card());return new BinderCard(c.serial(),d.name(),d.category(),d.type(),d.power(),d.text(),c.finish(),c.origin(),c.lock().isEmpty());}).toList();
        List<BannerView> banners=data.banners();
'''+seg[b:]
for old,new in {'store.profile(owner)':'data.profile()','store.completion(owner)':'data.counts()','store.deckNames(owner).stream().map(n->store.deck(owner,n)).toList()':'data.decks()','store.deckNames(owner)':'data.deckNames()','store.market(page)':'data.market()','store.trades(owner)':'data.trades()','store.binder(tradePeers.get(p.getUuid()).toString(),page)':'result.peerBinder()','store.collection(owner)':'data.inventory()','store.formats(owner)':'data.formats()'}.items():seg=seg.replace(old,new)
seg=rep(seg,'cachePlaceholders(p,snap);ServerPlayNetworking.send(p,new TcgPackets.Snapshot(JSON.toJson(snap)));','''cachePlaceholders(p,snap,data);
        var content=new SnapshotPipeline.Content(catalog.cards(),catalog.starters(),catalog.rules());boolean include=contentSent.getOrDefault(p.getUuid(),-1L)!=contentGeneration;
        snapshotPipeline.offer(p.getUuid(),++snapshotSequence,new SnapshotPipeline.Wire(snapshotSequence,contentGeneration,include?content:null,snap.withContent(null)));''')
s=s[:s.index('    private void send(')]+seg+s[s.index('    MessageService commandMessages'):]
s=s.replace('private void cachePlaceholders(ServerPlayerEntity player,Snapshot snap){','private void cachePlaceholders(ServerPlayerEntity player,Snapshot snap,CardStore.UiData data){').replace('var stats=store.duelStatistics(player.getUuidAsString());','var stats=data.statistics();').replace('Integer.toString(1+store.playersAboveRating(snap.profile().rating()))','Integer.toString(data.rank())')
# Cheap live placeholders do not materialize a full Duel view.
a=s.index('        if(match!=null){var view=match.duel.view',s.index('String placeholderValue'));b=s.index('        return placeholderCache',a)
s=s[:a]+'''        if(match!=null){String live=match.duel.placeholder(match.seat(player.getUuid()),key);if(live!=null)return live;}
'''+s[b:]
s=rep(s,'if(previousStore!=null)previousStore.close();','if(snapshotPipeline!=null)snapshotPipeline.clear();storeReads.values().forEach(j->j.future().cancel(false));storeReads.clear();contentGeneration++;contentSent.clear();if(previousStore!=null)previousStore.close();')
p.write_text(s)
# Protocol does no compression / expansion on Minecraft or Netty codec threads.
p=base/'fabric/TcgPackets.java';s=p.read_text().replace('Identifier.of("svarcade_tcg","snapshot")','Identifier.of("svarcade_tcg","snapshot_v2")').replace('record Snapshot(String json)','record Snapshot(byte[] bytes)').replace('b.writeByteArray(SnapshotCompression.encode(v.json)),b->new Snapshot(SnapshotCompression.decode(b.readByteArray(900*1024)))','b.writeByteArray(v.bytes),b->new Snapshot(b.readByteArray(900*1024))');p.write_text(s)
# All command outputs and Minecraft access remain owner-thread.
p=base/'fabric/CardWorldsCommands.java';s=p.read_text();s=s.replace('.then(literal("open").executes(ctx -> open(ctx, mod)))','''.then(literal("open").executes(ctx -> open(ctx, mod)))
            .then(literal("perf").requires(source->source.hasPermissionLevel(3)).executes(ctx->{ctx.getSource().sendFeedback(()->net.minecraft.text.Text.literal(mod.commandPerf()),false);return 1;}).then(literal("reset").executes(ctx->{vn.svarcade.tcg.performance.CardWorldsPerfStats.reset();return 1;})))''');p.write_text(s)
# Compact placeholder reads access main-thread mutable state directly, no SQL or metadata scans.
p=base/'duel/Duel.java';s=p.read_text();i=s.index('    public synchronized long revision()');s=s[:i]+'''    public synchronized String placeholder(int actor,String key){return switch(key){case "duel_turn"->Integer.toString(turn);case "duel_phase"->phase.name();case "lp"->Integer.toString(life[actor]);case "opponent_lp"->Integer.toString(life[1-actor]);case "hand_size"->Integer.toString(count(actor,Zone.HAND));case "deck_size"->Integer.toString(count(actor,Zone.DECK));case "graveyard_size"->Integer.toString(count(actor,Zone.DISCARD));case "banished_size"->Integer.toString(count(actor,Zone.BANISHED));default->null;};}
'''+s[i:];p.write_text(s)
# Client decode is bounded and FIFO-applied even when workers finish out of order.
p=base/'fabric/TcgClient.java';s=p.read_text();s=s.replace('import java.util.List;','import java.util.List;\nimport vn.svarcade.tcg.performance.*;')
s=s.replace('private static TcgMod.Snapshot pendingRealmSnapshot;','''private static TcgMod.Snapshot pendingRealmSnapshot;
    private static CardWorldsExecutors decodeWorkers;
    private static final java.util.ArrayDeque<java.util.concurrent.CompletableFuture<SnapshotPipeline.Wire>> decoded=new java.util.ArrayDeque<>();
    private static SnapshotPipeline.Content content;private static long generation=-1,lastSequence=-1;
    private static void resetDecode(){decoded.forEach(f->f.cancel(false));decoded.clear();content=null;generation=-1;lastSequence=-1;pendingRealmSnapshot=null;if(decodeWorkers!=null){decodeWorkers.close();decodeWorkers=null;}}
    private static void acceptPacket(TcgPackets.Snapshot packet){
        if(decodeWorkers==null)decodeWorkers=new CardWorldsExecutors(PerformanceConfig.defaults());
        if(decoded.size()>=64)consumeDecode(MinecraftClient.getInstance(),true);
        byte[] captured=packet.bytes();decoded.add(decodeWorkers.submit(CardWorldsExecutors.Kind.SERIALIZATION,new CardWorldsExecutors.Context("client-decode","connection",lastSequence),()->SnapshotPipeline.decode(captured)));
    }
    private static void consumeDecode(MinecraftClient client,boolean wait){
        while(!decoded.isEmpty()&&(wait||decoded.peek().isDone())){var future=decoded.remove();try{applyWire(client,future.join());}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger("cardworlds-client").error("Card Worlds snapshot decode failed",ex);request("resync",List.of(),0);}if(wait)break;}
    }
    private static void applyWire(MinecraftClient client,SnapshotPipeline.Wire wire){
        if(wire.sequence()<=lastSequence)return;
        if(wire.content()!=null){content=wire.content();generation=wire.generation();}
        if(content==null||generation!=wire.generation()){request("resync",List.of(),0);return;}
        lastSequence=wire.sequence();TcgMod.Snapshot state=wire.state().withContent(content);
        applyState(client,state);
    }
''')
s=s.replace('PokemonModels.initialize();','''PokemonModels.initialize();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->resetDecode());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->resetDecode());''')
s=s.replace('ClientTickEvents.END_CLIENT_TICK.register(client->{','ClientTickEvents.END_CLIENT_TICK.register(client->{consumeDecode(client,false);')
a=s.index('        ClientPlayNetworking.registerGlobalReceiver(TcgPackets.Snapshot.ID');b=s.index('    public static void request',a)
old=s[a:b];start=old.index('                boolean realmFlow');end=old.index('            })');logic=old[start:end].replace('ctx.client()','client')
s=s[:a]+'''        ClientPlayNetworking.registerGlobalReceiver(TcgPackets.Snapshot.ID,(packet,ctx)->ctx.client().execute(()->acceptPacket(packet)));
    }
    private static void applyState(MinecraftClient client,TcgMod.Snapshot state){
'''+logic+'''    }

'''+s[b:];p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text();s=s.replace('case "open" -> {openRequests.add(player);}','case "resync" -> {contentSent.remove(player);}\n                case "open" -> {openRequests.add(player);}')
s=s.replace('long now=System.nanoTime();if(now-rateLimit.getOrDefault(player,0L)<80_000_000L)return;rateLimit.put(player,now);','long now=System.nanoTime();rateLimit.put(player,now);')
for name,key in [('placeholderCoins','coins'),('placeholderRating','rating'),('placeholderCards','cards'),('placeholderDecks','decks')]:
 a=s.index('    '+('long' if name=='placeholderCoins' else 'int')+' '+name+'(');b=s.index('\n    }',a)
 cast='Long' if name=='placeholderCoins' else 'Integer'
 prefix=s[a:s.index('{',a)+1]
 s=s[:a]+prefix+'\n        try{return '+cast+'.parse'+('Long' if cast=='Long' else 'Int')+'(placeholderCache.getOrDefault(player.getUuid(),Map.of()).getOrDefault("'+key+'","0"));}catch(NumberFormatException ignored){return 0;}'+s[b:]
s=s.replace('long aiSeed,aiRevision=-1;','long aiSeed,aiRevision=-1;Random aiRandom;').replace('m.npc=true;m.botDifficulty=difficulty;','m.npc=true;m.botDifficulty=difficulty;m.aiRandom=new Random(rng.nextLong());').replace('m.aiSeed=rng.nextLong();','m.aiSeed=m.aiRandom.nextLong();')
p.write_text(s)
# Freeze typed content at load. No per-action JSON map interpretation or half-reloaded generations.
p=base/'data/Catalog.java';s=p.read_text();s=s.replace('    public record Rules(','''    public Catalog {cards=ordered(cards);banners=ordered(banners);rewards=ordered(rewards);dealers=ordered(dealers);Map<String,List<String>> copy=new LinkedHashMap<>();starters.forEach((k,v)->copy.put(k,List.copyOf(v)));starters=java.util.Collections.unmodifiableMap(copy);}
    private static <K,V> Map<K,V> ordered(Map<K,V> value){return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));}
    public record Rules(''')
s=s.replace('Map<String,Integer> rankedLimits, Map<String,List<String>> effective) {}','Map<String,Integer> rankedLimits, Map<String,List<String>> effective) {public Rules {rankedLimits=ordered(rankedLimits);Map<String,List<String>> copy=new LinkedHashMap<>();effective.forEach((k,v)->copy.put(k,List.copyOf(v)));effective=java.util.Collections.unmodifiableMap(copy);}}')
s=s.replace('        public Effect(String operation,int amount','        public Effect {phases=List.copyOf(phases);}\n        public Effect(String operation,int amount')
s=s.replace('        public int tributeCount() {','        public Card {aspects=List.copyOf(aspects);sources=List.copyOf(sources);triggers=triggers==null?null:List.copyOf(triggers);modifiers=modifiers==null?null:List.copyOf(modifiers);}\n        public int tributeCount() {')
s=s.replace('List<Weighted> pool) {}','List<Weighted> pool) {public Banner {pool=List.copyOf(pool);}}')
p.write_text(s)
p=base/'data/EffectSpec.java';s=p.read_text();s=s.replace('    public EffectSpec(List<String> triggers,','    public EffectSpec {triggers=List.copyOf(list(triggers));conditions=List.copyOf(list(conditions));costs=List.copyOf(list(costs));operations=List.copyOf(list(operations));stages=List.copyOf(list(stages));resolutionConditions=List.copyOf(list(resolutionConditions));}\n    public EffectSpec(List<String> triggers,',1)
s=s.replace('boolean listenAny) {}','boolean listenAny) {public Stage {sourceZones=List.copyOf(list(sourceZones));}}').replace('List<Condition> children) {}','List<Condition> children) {public Condition {children=List.copyOf(list(children));}}').replace('Boolean faceUp) {}','Boolean faceUp) {public Filter {types=List.copyOf(list(types));tags=List.copyOf(list(tags));}}')
s=s.replace('        public Operation(String type,','        public Operation {flags=flags==null?Map.of():Map.copyOf(flags);children=List.copyOf(list(children));otherwise=List.copyOf(list(otherwise));}\n        public Operation(String type,',1)
s=s.replace('        public Presentation(String mode,','        public Presentation {stages=List.copyOf(list(stages));}\n        public Presentation(String mode,',1);p.write_text(s)
p=base/'duel/Duel.java';s=p.read_text();s=s.replace('    private final Catalog catalog;','    private final Thread ownerThread=Thread.currentThread();\n    private final Catalog catalog;').replace('if(qaMutable)refreshQaIndexes();\n        require(actor','if(Thread.currentThread()!=ownerThread)throw new IllegalStateException("Duel mutations require its owner thread");if(qaMutable)refreshQaIndexes();\n        require(actor');p.write_text(s)
# Durable reward journal replaces unbounded in-memory append-file receipts.
p=base/'integration/BEconomyCardWorlds.java';s=p.read_text()
s=s.replace('private static final Set<String> REWARD_RECEIPTS=ConcurrentHashMap.newKeySet();','private static vn.svarcade.tcg.performance.RewardReceiptJournal journal;')
s=s.replace('private static final Map<String,String> NOTICES=new ConcurrentHashMap<>();','private static final Map<String,String> NOTICES=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>4096;}});')
a=s.index('        loadReceipts();',s.index('private static void rewardOnce'));b=s.index('        NOTICES.put',a)
s=s[:a]+'''        String receipt=matchId+"|"+player+"|"+BEAST+"|"+amount;
        synchronized(LOCK) {
            ensureJournal();if(!journal.payOnce(receipt,()->add(player,amount,BEAST)))return;
        }
'''+s[b:]
a=s.index('    private void loadReceipts') if '    private void loadReceipts' in s else s.index('    private static void loadReceipts');s=s[:a]+'''    private static void ensureJournal(){
        if(journal!=null)return;
        try{Path legacy=receiptsFile();journal=new vn.svarcade.tcg.performance.RewardReceiptJournal(legacy.resolveSibling("beconomy-reward-receipts.db"),legacy);}catch(Exception e){throw new IllegalStateException("Cannot open durable BEconomy receipt journal",e);}
    }
    public static void shutdown(){synchronized(LOCK){try{if(journal!=null)journal.close();}catch(Exception e){throw new IllegalStateException("Cannot close reward receipts",e);}finally{journal=null;cachedApi=null;NOTICES.clear();}}}
}
''';p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text();s=s.replace('if(workers!=null)workers.close();try{if(store!=null)store.close();}', 'if(workers!=null)workers.close();if(server.isDedicated())vn.svarcade.tcg.integration.BEconomyCardWorlds.shutdown();try{if(store!=null)store.close();}')
a=s.index('boolean existing=store.hasReceipt');b=s.index('\n                }',a)
s=s[:a]+'''                    var results=EconomyTransactions.pull(owner,request,()->store.hasReceipt(owner,request),()->vn.svarcade.tcg.integration.BEconomyCardWorlds.chargePull(owner,request),()->store.pull(owner,a.get(0),request,Instant.now().getEpochSecond()),()->vn.svarcade.tcg.integration.BEconomyCardWorlds.refundPull(owner,request));reveals.put(player,results);'''+s[b:];p.write_text(s)
# Preference disk writes leave the rendering thread, then flush before owned workers shut down.
p=base/'fabric/TcgClient.java';s=p.read_text();s=s.replace('private static CardWorldsExecutors decodeWorkers;','''private static CardWorldsExecutors decodeWorkers;
    private static PersistenceQueue<java.nio.file.Path,List<String>> preferences;
    public static void saveFavorites(java.nio.file.Path path,List<String> values){ensureWorkers();preferences.put(path,List.copyOf(values));}
    private static void ensureWorkers(){if(decodeWorkers==null){decodeWorkers=new CardWorldsExecutors(PerformanceConfig.defaults());preferences=new PersistenceQueue<>(decodeWorkers,(path,values)->{try{java.nio.file.Files.write(path,values);}catch(java.io.IOException e){throw new IllegalStateException("Cannot save Card Worlds favorites",e);}});}}
''')
s=s.replace('if(decodeWorkers!=null){decodeWorkers.close();decodeWorkers=null;}','if(decodeWorkers!=null){if(preferences!=null)preferences.close();decodeWorkers.close();decodeWorkers=null;preferences=null;}')
s=s.replace('if(decodeWorkers==null)decodeWorkers=new CardWorldsExecutors(PerformanceConfig.defaults());','ensureWorkers();')
s=s.replace('client->{consumeDecode(client,false);','client->{consumeDecode(client,false);if(preferences!=null)try{preferences.poll();}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger("cardworlds-client").error("Card Worlds preference persistence failed",e);}')
p.write_text(s)
# Safe dynamic-state projection: unchanged database UI data is reused; generation/key mismatches request full recovery.
p=base/'fabric/TcgMod.java';s=p.read_text()
uiFields={'profile','binder','collected','decks','market','banners','guides','trades','tradeShelf','tradePeer','inventory','counts','savedDecks','formats','sellers'}
s=s.replace('        public Snapshot withCounts(','''        public Snapshot withUi(Snapshot cached){return '''+construct({n:('cached==null?-1:cached.'+n+'()' if n=='collected' else 'cached==null?null:cached.'+n+'()') for n in uiFields})+''';}
        public Snapshot withCounts(''')
s=s.replace('private final Map<UUID,Long> contentSent=new HashMap<>();','private final Map<UUID,Long> contentSent=new HashMap<>();private final Map<UUID,String> uiSent=new HashMap<>();')
s=s.replace('contentSent.remove(id);','contentSent.remove(id);uiSent.remove(id);').replace('contentSent.remove(player);','contentSent.remove(player);uiSent.remove(player);').replace('contentSent.clear();','contentSent.clear();uiSent.clear();')
s=s.replace('snapshotPipeline.offer(p.getUuid(),++snapshotSequence,new SnapshotPipeline.Wire(snapshotSequence,contentGeneration,include?content:null,snap.withContent(null)));','''String uiKey=data.revision()+":"+page+":"+result.input().peer();boolean fullUi=!uiKey.equals(uiSent.get(p.getUuid()));
        if(!performance.snapshotCoalesce())snapshotPipeline.flush(p.getUuid(),(id,encoded)->commitSnapshot(p.getServer(),id,encoded),(id,error)->org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("Snapshot flush failed owner="+id,error));
        snapshotPipeline.offer(p.getUuid(),++snapshotSequence,new SnapshotPipeline.Wire(snapshotSequence,contentGeneration,uiKey,fullUi,m==null?"":m.id,m==null?-1:m.duel.revision(),include?content:null,fullUi?snap.withContent(null):snap.withContent(null).withUi(null)),(id,encoded)->commitSnapshot(p.getServer(),id,encoded));''')
a=s.index('        snapshotPipeline.poll((id,encoded)->');b=s.index('\n    String commandPerf',a)
s=s[:a]+'''        snapshotPipeline.poll((id,encoded)->commitSnapshot(server,id,encoded),(id,error)->org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("Snapshot preparation failed owner="+id,error));
    }
    private void commitSnapshot(MinecraftServer server,UUID id,SnapshotPipeline.Encoded encoded){
        var player=server.getPlayerManager().getPlayer(id);if(player==null)return;Match current=spectating.get(id);if(current==null)current=matches.get(id);
        String match=current==null?"":current.id;long revision=current==null?-1:current.duel.revision();
        if(!match.equals(encoded.duelId())||revision!=encoded.duelRevision()||store.dataRevision()!=encoded.storeRevision()){send(player,"",0);return;}
        ServerPlayNetworking.send(player,new TcgPackets.Snapshot(encoded.bytes()));if(encoded.content())contentSent.put(id,encoded.generation());if(encoded.fullUi())uiSent.put(id,encoded.uiKey());
    }
'''+s[b:]
s=s.replace('CardWorldsPerfStats.summary()+" AI="','"metrics="+CardWorldsPerfStats.enabled()+" "+CardWorldsPerfStats.summary()+" AI="')
p.write_text(s)
p=base/'performance/SnapshotPipeline.java';s=p.read_text().replace('record Wire(long sequence,long generation,Content content,TcgMod.Snapshot state)','record Wire(long sequence,long generation,String uiKey,boolean fullUi,String duelId,long duelRevision,Content content,TcgMod.Snapshot state)').replace('record Encoded(long sequence,long generation,boolean content,byte[] bytes)','record Encoded(long sequence,long generation,String uiKey,boolean fullUi,String duelId,long duelRevision,long storeRevision,boolean content,byte[] bytes)')
s=s.replace('new Encoded(input.sequence(),input.generation(),input.content()!=null,SnapshotCompression.encode(json))','new Encoded(input.sequence(),input.generation(),input.uiKey(),input.fullUi(),input.duelId(),input.duelRevision(),Long.parseLong(input.uiKey().split(":",2)[0]),input.content()!=null,SnapshotCompression.encode(json))')
s=s.replace('return new Wire(next.sequence(),next.generation(),next.content()!=null?next.content():old.generation()==next.generation()?old.content():null,next.state().mergePresentation(old.state()));','''var state=next.state().mergePresentation(old.state());boolean full=next.fullUi();if(!full&&old.fullUi()&&old.uiKey().equals(next.uiKey())){state=state.withUi(old.state());full=true;}return new Wire(next.sequence(),next.generation(),next.uiKey(),full,next.duelId(),next.duelRevision(),next.content()!=null?next.content():old.generation()==next.generation()?old.content():null,state);''')
p.write_text(s)
p=base/'fabric/TcgClient.java';s=p.read_text().replace('private static SnapshotPipeline.Content content;', 'private static TcgMod.Snapshot cachedUi;private static String uiKey="";\n    private static SnapshotPipeline.Content content;').replace('content=null;generation=-1;','content=null;cachedUi=null;uiKey="";generation=-1;')
s=s.replace('lastSequence=wire.sequence();TcgMod.Snapshot state=wire.state().withContent(content);','''TcgMod.Snapshot state=wire.state();if(wire.fullUi()){cachedUi=state;uiKey=wire.uiKey();}else{if(cachedUi==null||!uiKey.equals(wire.uiKey())){request("resync",List.of(),0);return;}state=state.withUi(cachedUi);}lastSequence=wire.sequence();state=state.withContent(content);''')
p.write_text(s)
# Retain every event/log line while bounding active Duel history buffers; no mechanic depends on a file scan.
p=base/'duel/Duel.java';s=p.read_text();s=s.replace('private final List<Event> history=new ArrayList<>();','private final vn.svarcade.tcg.performance.BoundedHistory<Event> history=new vn.svarcade.tcg.performance.BoundedHistory<>(Event.class,"events");').replace('private final List<String> log=new ArrayList<>();','private final vn.svarcade.tcg.performance.BoundedHistory<String> log=new vn.svarcade.tcg.performance.BoundedHistory<>(String.class,"log");')
s=s.replace('    public synchronized long revision()', '''    public synchronized void historyWorkers(CardWorldsExecutors workers,Path path){history.configure(workers,path);log.configure(workers,path);}
    public synchronized void flushHistory(){history.flush();log.flush();}
    public synchronized long revision()''').replace('package vn.svarcade.tcg.duel;','package vn.svarcade.tcg.duel;\nimport java.nio.file.Path;\nimport vn.svarcade.tcg.performance.CardWorldsExecutors;')
s=s.replace('private final Set<Piece> dirtyLive=', 'private final Map<String,Long> pileArrival=new HashMap<>();private final Map<String,EnumSet<Cause>> destroyedCauses=new HashMap<>();\n    private final Set<Piece> dirtyLive=')
s=s.replace('private void clearIndexes(){','private void clearIndexes(){pileArrival.clear();destroyedCauses.clear();')
s=s.replace('history.add(event);pendingEvents.add(event);','history.add(event);if(to==Zone.DISCARD||to==Zone.BANISHED)pileArrival.put(p.token,event.sequence());if(cause==Cause.BATTLE||cause==Cause.DESTROY)destroyedCauses.computeIfAbsent(p.token,k->EnumSet.noneOf(Cause.class)).add(cause);pendingEvents.add(event);')
a=s.index('        Map<String,Long> arrival = new HashMap<>();',s.index('private List<VisibleCard> orderPublicPiles'));b=s.index('        return cards.stream()',a);s=s[:a]+'        Map<String,Long> arrival=pileArrival;\n'+s[b:]
s=s.replace('history.stream().anyMatch(e->source!=null&&e.card().equals(source.token)&&e.cause()==(c.type().endsWith("BATTLE")?Cause.BATTLE:Cause.DESTROY))','source!=null&&destroyedCauses.getOrDefault(source.token,EnumSet.noneOf(Cause.class)).contains(c.type().endsWith("BATTLE")?Cause.BATTLE:Cause.DESTROY)')
p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text();s=s.replace('private void prepareRealm(MinecraftServer server,Match m){','private void prepareRealm(MinecraftServer server,Match m){m.duel.historyWorkers(workers,server.getSavePath(WorldSavePath.ROOT).resolve("svarcade-tcg/duel-history").resolve(m.id));')
s=s.replace('if(workers!=null)workers.close();','for(var m:new HashSet<>(matches.values()))m.duel.flushHistory();if(workers!=null)workers.close();')
s=s.replace('matches.remove(m.a);matches.remove(m.b);','m.ai.cancel();m.duel.flushHistory();matches.remove(m.a);matches.remove(m.b);')
p.write_text(s)
# Read-only WAL connections remove DB snapshot work from the authoritative writer monitor.
p=base/'economy/CardStore.java';s=p.read_text()
s=s.replace('private volatile long dataRevision;','private volatile long dataRevision;private final boolean uiReadOnly;private final vn.svarcade.tcg.performance.ReadConnectionPool<CardStore> uiReaders;')
s=s.replace('public synchronized UiData uiData(String owner,int page){','''public UiData uiData(String owner,int page){
        if(uiReadOnly)return uiDataLocal(owner,page);
        long revision=dataRevision;UiKey key=new UiKey(owner,page);synchronized(uiCache){var cached=uiCache.get(key);if(cached!=null&&cached.revision()==revision)return cached;}
        UiData data=uiReaders.read(reader->{reader.dataRevision=revision;reader.uiCache.clear();try{return reader.uiDataLocal(owner,page);}finally{reader.uiCache.clear();}});
        synchronized(uiCache){if(data.revision()==dataRevision){if(uiCache.size()>=256)uiCache.remove(uiCache.keySet().iterator().next());uiCache.put(key,data);}}return data;
    }
    private synchronized UiData uiDataLocal(String owner,int page){''')
s=s.replace('public synchronized int uiCacheSize(){return uiCache.size();}','public int uiCacheSize(){synchronized(uiCache){return uiCache.size();}}')
s=s.replace('public CardStore(Path file,Catalog catalog,Random rng)throws Exception {','''public CardStore(Path file,Catalog catalog,Random rng)throws Exception {this(file,catalog,rng,false);}
    private CardStore(Path file,Catalog catalog,Random rng,boolean readOnly)throws Exception {
        uiReadOnly=readOnly;uiReaders=readOnly?null:new vn.svarcade.tcg.performance.ReadConnectionPool<>(()->{try{return new CardStore(file,catalog,new Random(0),true);}catch(Exception e){throw new IllegalStateException("Cannot open Card Worlds read-only database",e);}});''')
s=s.replace('try(Statement s=db.createStatement()) {','try(Statement s=db.createStatement()) {\n            if(readOnly){s.execute("PRAGMA query_only=ON");s.execute("PRAGMA busy_timeout=5000");return;}',1)
s=s.replace('private synchronized <T>T tx(Work<T> work) {','private synchronized <T>T tx(Work<T> work) {\n        if(uiReadOnly)throw new IllegalStateException("Read-only UI data cannot mutate CardStore");')
s=s.replace('dataRevision++;uiCache.clear();','dataRevision++;synchronized(uiCache){uiCache.clear();}')
s=s.replace('db.close();','if(uiReaders!=null)uiReaders.close();db.close();')
# Parent cache entries are never changed from a reader's mutation path; SQL reads see one SQLite snapshot.
s=s.replace('private synchronized UiData uiDataLocal(String owner,int page){','private synchronized UiData uiDataLocal(String owner,int page){\n        try{db.setAutoCommit(false);}catch(SQLException e){throw new IllegalStateException(e);}')
s=s.replace('Path.CARDSTORE,started);','Path.CARDSTORE,started);try{db.rollback();db.setAutoCommit(true);}catch(SQLException e){throw new IllegalStateException("Cannot close consistent CardStore read snapshot",e);}')
p.write_text(s)
# Obsolete AI occupies its existing single slot until computation actually finishes; it never fans out.
p=base/'performance/AsyncDerivedTask.java';s=p.read_text();s=s.replace('private long revision=-1;','private boolean obsolete;private long revision=-1;')
s=s.replace('cancel();revision=source;future=task;','if(future!=null)throw new IllegalStateException("One AI computation per duel");obsolete=false;revision=source;future=task;')
s=s.replace('if(!live||current!=revision){cancel();return null;}','if(!live||current!=revision||obsolete){obsolete=true;if(future.isDone()){future=null;revision=-1;}return null;}')
s=s.replace('if(future!=null)future.cancel(false);future=null;revision=-1;','obsolete=true;if(future!=null&&future.isDone()){future=null;revision=-1;}')
p.write_text(s)
p=base/'performance/CardWorldsExecutors.java';s=p.read_text().replace('result.completeExceptionally(new TaskFailure(context,e));','org.slf4j.LoggerFactory.getLogger("cardworlds-perf").error("Card Worlds async task failed type="+context.type()+" duel="+context.duelId()+" revision="+context.revision(),e);result.completeExceptionally(new TaskFailure(context,e));');p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('if(job!=null&&!job.input().equals(input)){job.future().cancel(false);storeReads.remove(id);job=null;}','if(job!=null&&!job.input().equals(input)){if(!job.future().isDone())continue;storeReads.remove(id);job=null;}');p.write_text(s)
p=base/'fabric/TcgClient.java';s=p.read_text().replace('ctx.client().execute(()->acceptPacket(packet))','ctx.client().execute(()->{if(ctx.player()!=null&&ctx.client().getNetworkHandler()==ctx.player().networkHandler)acceptPacket(packet);})');p.write_text(s)
p=base/'duel/Duel.java';s=p.read_text();s=s.replace('private final Catalog catalog;','private final Catalog catalog;private final vn.svarcade.tcg.performance.CompiledEffects compiled;').replace('this.catalog=catalog; life=','this.catalog=catalog;compiled=vn.svarcade.tcg.performance.CatalogIndex.of(catalog.cards()).effects; life=')
s=s.replace('Integer.parseInt(flag(op,"count","1"))','compiled.operation(op).count()').replace('Integer.parseInt(flag(op,"max","99"))','compiled.operation(op).maxCounter()').replace('Boolean.parseBoolean(flag(op,"reveal","true"))','compiled.operation(op).reveal()').replace('Zone zone=Zone.valueOf(op.zone());','Zone zone=compiled.operation(op).zone();')
s=s.replace('BattlePosition.valueOf(flag(op,"position",p.position==BattlePosition.ATTACK?"DEFENSE":"ATTACK"))','compiled.operation(op).position()!=null?compiled.operation(op).position():p.position==BattlePosition.ATTACK?BattlePosition.DEFENSE:BattlePosition.ATTACK')
a=s.index('    private int expiryTurn(String duration) {');b=s.index('    private int compositePower',a);s=s[:a]+'''    private int expiryTurn(String duration){return turn+compiled.durationOffset(duration);}
'''+s[b:]
s=s.replace('l.target.matches("[0-9]+")','isDecimal(l.target)');s=s.replace('    private String flag(','''    private static boolean isDecimal(String value){if(value.isEmpty())return false;for(int i=0;i<value.length();i++)if(value.charAt(i)<'0'||value.charAt(i)>'9')return false;return true;}
    private String flag(''');p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('catalog=CobblemonCatalogHydrator.expand(Catalog.load(file));store=','catalog=CobblemonCatalogHydrator.expand(Catalog.load(file));CatalogIndex.of(catalog.cards());store=').replace('catalog=next;','catalog=next;CatalogIndex.of(catalog.cards());');p.write_text(s)
# Intern only validated enum-like tokens, not arbitrary card text or player data.
p=base/'data/EffectSpec.java';s=p.read_text().replace('triggers=List.copyOf(list(triggers));','triggers=list(triggers).stream().map(String::intern).toList();').replace('public Operation {flags=','public Operation {type=type==null?null:type.intern();target=target==null?null:target.intern();zone=zone==null?null:zone.intern();flags=');p.write_text(s)
# A terminal Duel view must survive asynchronous UI construction after finish() removes the live Match.
p=base/'fabric/TcgMod.java';s=p.read_text()
s=s.replace('private record SendRequest(String notice,int page){}','''private record Terminal(Duel.View view,String matchId,String left,String right,boolean spectator){}
    private record SendRequest(String notice,int page,Terminal terminal){}
    private final Map<UUID,Terminal> terminalResults=new HashMap<>();''')
s=s.replace('sendRequests.clear();','sendRequests.clear();terminalResults.clear();').replace('sendRequests.remove(id);','sendRequests.remove(id);terminalResults.remove(id);')
s=s.replace('sendRequests.put(p.getUuid(),new SendRequest(combined,page));','''Match ended=spectating.get(p.getUuid());boolean watching=ended!=null;if(ended==null)ended=matches.get(p.getUuid());Terminal terminal=old==null?null:old.terminal();
        if(ended!=null&&ended.duel.winner()>=0){var a=p.getServer().getPlayerManager().getPlayer(ended.a);var b=p.getServer().getPlayerManager().getPlayer(ended.b);String left=watching?(a==null?"Duelist A":a.getName().getString()):p.getName().getString();String right=ended.npc?"Pewter Challenger":watching?(b==null?"Duelist B":b.getName().getString()):Optional.ofNullable(p.getServer().getPlayerManager().getPlayer(ended.opponent(p.getUuid()))).map(x->x.getName().getString()).orElse("Opponent");terminal=new Terminal(watching?ended.duel.spectatorView():ended.duel.view(ended.seat(p.getUuid())),ended.id,left,right,watching);terminalResults.put(p.getUuid(),terminal);}
        sendRequests.put(p.getUuid(),new SendRequest(combined,page,terminal));''')
s=s.replace('buildSnapshot(player,request.notice(),request.page(),result);','buildSnapshot(player,request.notice(),request.page(),result,request.terminal());').replace('int page,StoreResult result) {','int page,StoreResult result,Terminal terminal) {')
s=s.replace('boolean spectatorView=m!=null;if(m==null)m=matches.get(p.getUuid());Challenge challenge=', 'boolean spectatorView=m!=null;if(m==null)m=matches.get(p.getUuid());if(m!=null)terminal=null;else if(terminal!=null)spectatorView=terminal.spectator();Challenge challenge=')
s=s.replace('m==null?null:(spectatorView?', 'm==null?(terminal==null?null:terminal.view()):(spectatorView?')
s=s.replace('String leftName=spectatorView?', 'String leftName=terminal!=null?terminal.left():spectatorView?').replace('String rightName=m==null?"":', 'String rightName=terminal!=null?terminal.right():m==null?"":')
s=s.replace('fullUi,m==null?"":m.id,m==null?-1:m.duel.revision(),include?content:null,','fullUi,terminal!=null,terminal!=null?terminal.matchId():m==null?"":m.id,terminal!=null?terminal.view().revision():m==null?-1:m.duel.revision(),include?content:null,')
s=s.replace('if(!match.equals(encoded.duelId())||revision!=encoded.duelRevision()||store.dataRevision()!=encoded.storeRevision())','Terminal ended=terminalResults.get(id);boolean terminalValid=encoded.terminal()&&current==null&&ended!=null&&ended.matchId().equals(encoded.duelId())&&ended.view().revision()==encoded.duelRevision();\n        if(!terminalValid&&(!match.equals(encoded.duelId())||revision!=encoded.duelRevision())||store.dataRevision()!=encoded.storeRevision())')
s=s.replace('if(encoded.fullUi())uiSent.put(id,encoded.uiKey());','if(encoded.fullUi())uiSent.put(id,encoded.uiKey());if(encoded.terminal())terminalResults.remove(id);')
s=s.replace('Catalog next=CobblemonCatalogHydrator.expand(Catalog.load(file));','Catalog next=CobblemonCatalogHydrator.expand(Catalog.load(file));CatalogIndex.of(next.cards());')
s=s.replace('mergePresentation(Snapshot previous){','mergePresentation(Snapshot previous,boolean sameDuel){').replace('mergeCues(previous.duel(),duel)','sameDuel?mergeCues(previous.duel(),duel):duel')
p.write_text(s)
p=base/'performance/SnapshotPipeline.java';s=p.read_text().replace('boolean fullUi,String duelId','boolean fullUi,boolean terminal,String duelId').replace('input.fullUi(),input.duelId()','input.fullUi(),input.terminal(),input.duelId()').replace('next.state().mergePresentation(old.state())','next.state().mergePresentation(old.state(),old.duelId().equals(next.duelId()))').replace('full,next.duelId()','full,next.terminal(),next.duelId()');p.write_text(s)
# Snapshot workers record their actual duel ID/revision in errors, not a generic common-pool task.
p=base/'performance/CoalescingPipeline.java';s=p.read_text().replace('private final Function<I,O> compute;', 'private final Function<I,CardWorldsExecutors.Context> context;private final Function<I,O> compute;')
s=s.replace('this.workers=workers;this.kind=kind;this.compute=compute;this.merge=merge;','this(workers,kind,compute,merge,null);}\n public CoalescingPipeline(CardWorldsExecutors workers,CardWorldsExecutors.Kind kind,Function<I,O> compute,BinaryOperator<I> merge,Function<I,CardWorldsExecutors.Context> context){this.context=context;this.workers=workers;this.kind=kind;this.compute=compute;this.merge=merge;')
s=s.replace('new CardWorldsExecutors.Context("derived",e.getKey().toString(),s.active)','context==null?new CardWorldsExecutors.Context("derived",e.getKey().toString(),s.active):context.apply(captured)');p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('SnapshotPipeline::encode,SnapshotPipeline::merge);','SnapshotPipeline::encode,SnapshotPipeline::merge,input->new CardWorldsExecutors.Context("snapshot",input.duelId(),input.duelRevision()));');p.write_text(s)
p=base/'fabric/TcgClient.java';s=p.read_text();s=s.replace('private static TcgMod.Snapshot cachedUi;','private static final SnapshotReceiver snapshotReceiver=new SnapshotReceiver();\n    private static TcgMod.Snapshot cachedUi;')
s=s.replace('content=null;cachedUi=null;uiKey="";generation=-1;lastSequence=-1;','snapshotReceiver.reset();content=null;cachedUi=null;uiKey="";generation=-1;lastSequence=-1;')
a=s.index('    private static void applyWire(');b=s.index('    @Override',a)
s=s[:a]+'''    private static void applyWire(MinecraftClient client,SnapshotPipeline.Wire wire){
        SnapshotReceiver.Result result=snapshotReceiver.accept(wire);lastSequence=snapshotReceiver.sequence();if(result.resync()){request("resync",List.of(),0);return;}if(result.state()!=null)applyState(client,result.state());
    }
'''+s[b:]
p.write_text(s)
# Content is copied/frozen once per generation, not per packet.
p=base/'performance/SnapshotPipeline.java';s=p.read_text().replace('Catalog.Rules rules){}','Catalog.Rules rules){public Content {definitions=Collections.unmodifiableMap(new LinkedHashMap<>(definitions));Map<String,List<String>> copy=new LinkedHashMap<>();deckTemplates.forEach((k,v)->copy.put(k,List.copyOf(v)));deckTemplates=Collections.unmodifiableMap(copy);}}');p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('private CoalescingPipeline<UUID,SnapshotPipeline.Wire,SnapshotPipeline.Encoded> snapshotPipeline;','private CoalescingPipeline<UUID,SnapshotPipeline.Wire,SnapshotPipeline.Encoded> snapshotPipeline;private SnapshotPipeline.Content snapshotContent;').replace('CatalogIndex.of(catalog.cards());store=','CatalogIndex.of(catalog.cards());snapshotContent=new SnapshotPipeline.Content(catalog.cards(),catalog.starters(),catalog.rules());store=').replace('catalog=next;CatalogIndex.of(catalog.cards());','catalog=next;CatalogIndex.of(catalog.cards());snapshotContent=new SnapshotPipeline.Content(catalog.cards(),catalog.starters(),catalog.rules());').replace('var content=new SnapshotPipeline.Content(catalog.cards(),catalog.starters(),catalog.rules());','var content=snapshotContent;');p.write_text(s)

# The protocol receiver owns content/UI recovery; do not retain duplicate obsolete client caches.
p=base/'fabric/TcgClient.java';s=p.read_text().replace('private static TcgMod.Snapshot cachedUi;private static String uiKey="";', '').replace('private static SnapshotPipeline.Content content;private static long generation=-1,lastSequence=-1;', 'private static long lastSequence=-1;').replace('snapshotReceiver.reset();content=null;cachedUi=null;uiKey="";generation=-1;lastSequence=-1;', 'snapshotReceiver.reset();lastSequence=-1;');p.write_text(s)
p=base/'integration/BEconomyCardWorlds.java';s=p.read_text().replace('    private static volatile boolean receiptsLoaded;\n','');p.write_text(s)
# Peer collection rendering uses independent read leases as well.
p=base/'economy/CardStore.java';s=p.read_text().replace('    public UiData uiData(String owner,int page){', '    public List<Owned> readBinder(String owner,int page){return uiReadOnly?binder(owner,page):uiReaders.read(reader->reader.binder(owner,page));}\n    public UiData uiData(String owner,int page){').replace('var key=new UiKey(owner,page);var cached=uiCache.get(key);if(cached!=null&&cached.revision()==dataRevision)return cached;', 'var key=new UiKey(owner,page);');p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('captured.store().binder(captured.peer(),captured.page())','captured.store().readBinder(captured.peer(),captured.page())').replace('store.binder(input.peer(),input.page())','store.readBinder(input.peer(),input.page())').replace('placeholderCache.clear();selectedDecks.clear();', 'org.slf4j.LoggerFactory.getLogger("cardworlds-perf").info("CARDWORLDS_PERF_SHUTDOWN pools=closed history=flushed receipts=closed");placeholderCache.clear();selectedDecks.clear();');p.write_text(s)
# Apply explicit backpressure to unsent notices; do not lose their actions or notices.
p=base/'fabric/TcgMod.java';s=p.read_text().replace('private final Map<UUID,SendRequest> sendRequests=', 'private final Map<UUID,Integer> sendCounts=new HashMap<>();\n    private final Map<UUID,SendRequest> sendRequests=').replace('sendRequests.clear();','sendRequests.clear();sendCounts.clear();').replace('sendRequests.remove(id);','sendRequests.remove(id);sendCounts.remove(id);').replace('buildSnapshot(player,request.notice(),request.page(),result,request.terminal());it.remove();','buildSnapshot(player,request.notice(),request.page(),result,request.terminal());it.remove();sendCounts.remove(id);').replace('sendRequests.put(p.getUuid(),new SendRequest(combined,page,terminal));', '''sendRequests.put(p.getUuid(),new SendRequest(combined,page,terminal));
        if(sendCounts.merge(p.getUuid(),1,Integer::sum)>=64){while(sendRequests.containsKey(p.getUuid())){var pending=storeReads.get(p.getUuid());if(pending!=null)try{pending.future().join();}catch(RuntimeException ignored){}pollAsync(p.getServer());}}
''');p.write_text(s)
p=base/'performance/CoalescingPipeline.java';s=p.read_text().replace('if(s.future!=null)s.future.cancel(false);try{commit.accept', 'if(s.future!=null)try{s.future.join();}catch(RuntimeException ex){error.accept(key,ex);}try{commit.accept');p.write_text(s)
# Backpressure can run the polling path synchronously; prevent reentrant polling from its commits.
p=base/'fabric/TcgMod.java';s=p.read_text().replace('>=64){while(sendRequests', '>=64&&!pollingAsync){while(sendRequests');a=s.index('    private void pollAsync(MinecraftServer server){');b=s.index('    private void commitSnapshot',a);body=s[a:b].replace('private void pollAsync(MinecraftServer server){','private boolean pollingAsync;\n    private void pollAsync(MinecraftServer server){pollingAsync=true;try{',1);end=body.rfind('    }');body=body[:end]+'    }finally{pollingAsync=false;}}'+body[end+5:];s=s[:a]+body+s[b:];p.write_text(s)
# Active duels retain their immutable definitions across a catalog generation change.
p=base/'duel/Duel.java';s=p.read_text().replace('public synchronized long revision()', 'public Map<String,Catalog.Card> definitions(){return catalog.cards();}\n    public synchronized long revision()',1);p.write_text(s)
p=base/'fabric/TcgMod.java';s=p.read_text().replace('view,catalog.cards());String difficulty=m.botDifficulty','view,m.duel.definitions());String difficulty=m.botDifficulty').replace('m.duel.view(1),catalog.cards()),m.botDifficulty','m.duel.view(1),m.duel.definitions()),m.botDifficulty');p.write_text(s)

p=base/'duel/Duel.java';s=p.read_text().replace('// Everything above is validation. Costs below are committed even if resolution later fails.', '// Everything above is validation. Costs below are committed even if resolution later fails.\n        mutationEpoch++;').replace('private void resolve() {', 'private void resolve() {mutationEpoch++;');p.write_text(s)
# Only the focused dedicated QA task asks the server to stop after its assertions.
p=base/'fabric/TcgMod.java';s=p.read_text().replace('vn.svarcade.tcg.data.EffectEconomyQa.verify(catalog);}', 'vn.svarcade.tcg.data.EffectEconomyQa.verify(catalog);if(Boolean.getBoolean("cardworlds.qa.stopAfterVerify"))server.execute(()->server.stop(false));}');p.write_text(s)
# Immutable presentation projection for targeted QA; this does not modify server content or pack transactions.
p=base/'fabric/TcgMod.java';s=p.read_text();m=re.search(r'public Snapshot withCounts\(Map<String,Integer> value\)\{return new Snapshot\(([^;]+)\);\}',s);assert m;args=m.group(1).split(',');args=['counts' if a=='value' else 'List.copyOf(value)' if a=='banners' else a for a in args];s=s.replace(m.group(0),m.group(0)+'\n        public Snapshot withBanners(List<BannerView> value){return new Snapshot('+','.join(args)+');}');p.write_text(s)
