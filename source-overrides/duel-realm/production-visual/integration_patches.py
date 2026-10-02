from pathlib import Path
here=Path(__file__).resolve().parent
# This final overlay is deliberately applied after the working visual/deep-effect restore.
p=Path('src/main/java/vn/svarcade/tcg/data/EffectContent.java');s=p.read_text().replace('return new Catalog(base.rules(),Collections.unmodifiableMap(cards),Collections.unmodifiableMap(banners),base.rewards(),base.dealers(),base.starters());','return CardIdentities.apply(new Catalog(base.rules(),Collections.unmodifiableMap(cards),Collections.unmodifiableMap(banners),base.rewards(),base.dealers(),base.starters()));')
s=s.replace('return ACTIONS.get(actionKey(card.type()));','var base=ACTIONS.get(actionKey(card.type()));var spec=card.effect()==null?null:card.effect().spec();return spec==null||spec.vfx()==null?base:new EffectSpec.Presentation(base.mode(),base.profile(),base.duration(),base.animation(),base.particle(),base.fallback(),base.shape(),base.sound(),spec.vfx().stages());')
p.write_text(s)
p=Path('src/main/java/vn/svarcade/tcg/data/Catalog.java');s=p.read_text().replace('c.effect.spec().validate();','''{try{c.effect.spec().validate();}catch(RuntimeException validationError){throw new IllegalArgumentException(c.id+": effect: "+validationError.getMessage(),validationError);}}''');p.write_text(s)
p=Path('src/main/java/vn/svarcade/tcg/fabric/TcgMod.java');s=p.read_text()
s=s.replace('    @Override public void onInitialize() {','''    @Override public void onInitialize() {
        vn.svarcade.tcg.integration.CardWorldsIntegrations.initialize();
        vn.svarcade.tcg.integration.EconomyRewards.verifyCapabilities();
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success)vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();});''')
s=s.replace('FabricLoader.getInstance().isModLoaded("placeholder-api")','vn.svarcade.tcg.integration.CardWorldsIntegrations.capabilities().has("placeholder-api")')
s=s.replace('private final Map<UUID,Match> matches=', 'private final Map<UUID,Map<String,String>> placeholderCache=new java.util.concurrent.ConcurrentHashMap<>();\n    private final Map<UUID,String> selectedDecks=new HashMap<>();\n    private final Map<UUID,Match> matches=')
s=s.replace('        store.unlockDuel(m.id);UUID winner=', '        store.unlockDuel(m.id);UUID winner=')
s=s.replace('                case "open" ->', '''                case "qa_special" -> {
                    check(Boolean.getBoolean("cardworlds.qa")&&p.hasPermissionLevel(2),"QA is disabled");check(a.size()==2,"Choose a special QA card");
                    Match m=matches.get(player);check(m!=null,"No active duel");m.duel.qaSpecialScenario(m.seat(player),a.get(0),Boolean.parseBoolean(a.get(1)));broadcast(p.getServer(),m);
                    String lp=vn.svarcade.tcg.integration.PlaceholderBridge.resolve(p.getCommandSource(),"%svarcade_tcg:lp%");
                    if(vn.svarcade.tcg.integration.CardWorldsIntegrations.capabilities().has("placeholder-api"))check(lp.equals(Integer.toString(m.duel.view(m.seat(player)).life().get(m.seat(player)))),"Dynamic PB4 placeholder failed");
                    check(vn.svarcade.tcg.integration.PlaceholderBridge.resolve(p.getCommandSource(),"%svarcade_tcg:opponent_hand_contents%").equals("—"),"Private placeholder was exposed");
                    org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_PLACEHOLDER_PROOF lp={} unresolved=false private=false",lp);
                }
                case "open" ->''')
s=s.replace('        if(m.ranked)store.rankedResult' , '        store.recordDuelResult(m.a.toString(),m.id,winning==0);if(!m.npc)store.recordDuelResult(m.b.toString(),m.id,winning==1);\n        if(m.ranked)store.rankedResult')
s=s.replace('                    send(botWinner,payout.message(),0);', '                    store.lastReward(m.a.toString(),payout.message());send(botWinner,payout.message(),0);')
s=s.replace('ServerPlayNetworking.send(p,new TcgPackets.Snapshot(JSON.toJson(snap)));','''cachePlaceholders(p,snap);ServerPlayNetworking.send(p,new TcgPackets.Snapshot(JSON.toJson(snap)));''')
s=s.replace('notice="Deck saved.";', 'selectedDecks.put(player,a.getFirst());notice="Deck saved.";')
s=s.replace('store.lockDeck(m.a.toString(),c.deck,', 'store.lockDeck(m.a.toString(),c.deck,')
s=s.replace('store.lockDeck(', 'lockDeck(')
s=s.replace('private final Map<UUID,Match> matches=new HashMap<>();','private final Map<UUID,Match> matches=new java.util.concurrent.ConcurrentHashMap<>();')
s=s.replace('if(m.ranked)store.rankedResult(winner.toString(),loser.toString(),m.id);','if(m.ranked){store.rankedResult(winner.toString(),loser.toString(),m.id);placeholderCache.replaceAll((id,values)->{var update=new HashMap<>(values);update.put("rank",Integer.toString(1+store.playersAboveRating(Integer.parseInt(values.getOrDefault("rating","1000")))));return Map.copyOf(update);});}')
s=s.replace('matches.clear();spectating.clear();', 'placeholderCache.clear();selectedDecks.clear();matches.clear();spectating.clear();')
s=s.replace('        commandMessages(server).reload();','        vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();placeholderCache.clear();\n        commandMessages(server).reload();')
insert='''
    private List<List<String>> lockDeck(String owner,String name,boolean ranked,String match){var result=store.lockDeck(owner,name,ranked,match);selectedDecks.put(UUID.fromString(owner),name);return result;}
    private void cachePlaceholders(ServerPlayerEntity player,Snapshot snap){
        Map<String,String> values=new HashMap<>();values.put("player",player.getName().getString());
        values.put("coins",Long.toString(snap.profile().coins()));values.put("rating",Integer.toString(snap.profile().rating()));
        values.put("cards",Integer.toString(snap.profile().owned()));values.put("collection_count",values.get("cards"));values.put("decks",Integer.toString(snap.decks().size()));
        values.put("selected_deck",selectedDecks.getOrDefault(player.getUuid(),snap.decks().isEmpty()?"—":snap.decks().getFirst()));
        var stats=store.duelStatistics(player.getUuidAsString());values.put("wins",Long.toString(stats.wins()));values.put("losses",Long.toString(stats.losses()));values.put("last_reward",stats.lastReward().isBlank()?"—":stats.lastReward());
        values.put("rank",Integer.toString(1+store.playersAboveRating(snap.profile().rating())));placeholderCache.put(player.getUuid(),Map.copyOf(values));
    }
    String placeholderValue(ServerPlayerEntity player,String key){
        Match match=matches.get(player.getUuid());
        if(key.equals("duel_state"))return match==null?"idle":match.npc?"pve":"pvp";
        if(key.equals("bot_difficulty"))return match==null||!match.npc?"none":match.botDifficulty.toLowerCase(Locale.ROOT);
        if(match!=null){var view=match.duel.view(match.seat(player.getUuid()));switch(key){
            case "duel_turn":return Integer.toString(view.turn());case "duel_phase":return view.phase();
            case "lp":return Integer.toString(view.life().get(view.you()));case "opponent_lp":return Integer.toString(view.life().get(1-view.you()));
            case "hand_size":return Long.toString(view.cards().stream().filter(c->c.controller()==view.you()&&c.zone()==Duel.Zone.HAND).count());
            case "deck_size":return Integer.toString(view.deckCounts().get(view.you()));case "graveyard_size":return Long.toString(view.cards().stream().filter(c->c.controller()==view.you()&&c.zone()==Duel.Zone.DISCARD).count());
            case "banished_size":return Long.toString(view.cards().stream().filter(c->c.controller()==view.you()&&c.zone()==Duel.Zone.BANISHED).count());
        }}
        return placeholderCache.getOrDefault(player.getUuid(),Map.of()).getOrDefault(key,key.equals("player")?player.getName().getString():"—");
    }
'''
s=s.replace('    private static void check(boolean v,String m)',insert+'    private static void check(boolean v,String m)');p.write_text(s)
p=Path('src/main/java/vn/svarcade/tcg/economy/CardStore.java');s=p.read_text().replace('s.execute("CREATE TABLE IF NOT EXISTS profiles', 's.execute("CREATE TABLE IF NOT EXISTS duel_results(owner TEXT NOT NULL,match TEXT NOT NULL,won INTEGER NOT NULL,PRIMARY KEY(owner,match))");\n            s.execute("CREATE TABLE IF NOT EXISTS last_rewards(owner TEXT PRIMARY KEY,value TEXT NOT NULL)");\n            s.execute("CREATE TABLE IF NOT EXISTS profiles')
s=s.rstrip()[:-1]+'''
    public record DuelStatistics(long wins,long losses,String lastReward){}
    public synchronized void recordDuelResult(String owner,String match,boolean won){tx(()->{update("INSERT OR IGNORE INTO duel_results VALUES(?,?,?)",owner,match,won?1:0);return null;});}
    public synchronized void lastReward(String owner,String reward){tx(()->{update("INSERT INTO last_rewards VALUES(?,?) ON CONFLICT(owner) DO UPDATE SET value=excluded.value",owner,reward);return null;});}
    public synchronized DuelStatistics duelStatistics(String owner){try(var s=statement("SELECT COALESCE(SUM(won),0),COUNT(*)-COALESCE(SUM(won),0) FROM duel_results WHERE owner=?",owner);var r=s.executeQuery()){
        r.next();String reward="";try(var q=statement("SELECT value FROM last_rewards WHERE owner=?",owner);var v=q.executeQuery()){if(v.next())reward=v.getString(1);}return new DuelStatistics(r.getLong(1),r.getLong(2),reward);
    }catch(SQLException e){throw new IllegalStateException(e);}}
    public synchronized int playersAboveRating(int rating){try(var s=statement("SELECT COUNT(*) FROM profiles WHERE rating>?",rating);var r=s.executeQuery()){r.next();return r.getInt(1);}catch(SQLException e){throw new IllegalStateException(e);}}
}
''';p.write_text(s)
# Client cache invalidation is isolated from common/server code.
p=Path('src/main/java/vn/svarcade/tcg/fabric/TcgClient.java');s=p.read_text().replace('        PokemonModels.initialize();','''        PokemonModels.initialize();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener(){
            public net.minecraft.util.Identifier getFabricId(){return net.minecraft.util.Identifier.of("svarcade_tcg","presentation_caches");}
            public void reload(net.minecraft.resource.ResourceManager resources){
                vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();
                vn.svarcade.tcg.client.render.PokemonDuelAnimationResolver.invalidate();
                vn.svarcade.tcg.client.component.CardWorldsLanguage.invalidate();
                vn.svarcade.tcg.client.render.DuelWorldScene.invalidatePresentation();
            }
        });''');p.write_text(s)

# GUI renderables retain their requested aspects, but must use the same validated identity.
p=Path('src/main/java/vn/svarcade/tcg/client/render/PokemonModels.java');s=p.read_text().replace('PokemonProperties props = PokemonProperties.Companion.parse(species);\n                props.setAspects(new HashSet<>(aspects));','''var descriptor=vn.svarcade.tcg.integration.CobblemonBridge.resolve(species,"",aspects,"",false,"");
                if(!descriptor.available())return;
                PokemonProperties props=vn.svarcade.tcg.integration.CobblemonBridge.properties(descriptor);''');p.write_text(s)

# Upgrade legacy saved catalogs before strict level validation.
p=Path('src/main/java/vn/svarcade/tcg/data/Catalog.java');s=p.read_text()
start=s.index('    public static Catalog load(');end=s.index('    public Card card(',start)
s=s[:start]+'    public static Catalog load(Path override) throws IOException {\n        return CatalogMigration.load(override);\n    }\n'+s[end:]
p.write_text(s)


# Focused BEconomy reward + gacha overlay. Applied last so it owns all battle payouts and pack charges.
p=Path('src/main/java/vn/svarcade/tcg/economy/CardStore.java');s=p.read_text()
old="Catalog.Banner b=catalog.banners().get(bannerId);check(b!=null,\"Banner not found.\");check(now>=b.starts()&&(b.ends()==0||now<b.ends()),\"This banner is closed.\");debit(owner,b.price());Pity p=pity(owner,b.family());"
new="Catalog.Banner b=catalog.banners().get(bannerId);check(b!=null,\"Banner not found.\");check(now>=b.starts()&&(b.ends()==0||now<b.ends()),\"This banner is closed.\");Pity p=pity(owner,b.family());"
if old not in s: raise RuntimeError('CardStore pull debit anchor missing')
s=s.replace(old,new)
if 'public synchronized boolean hasReceipt(String owner,String request)' not in s:
    s=s.rstrip()[:-1]+'''\n    public synchronized boolean hasReceipt(String owner,String request){try{return scalar("SELECT COUNT(*) FROM receipts WHERE owner=? AND request=?",owner,request)>0;}catch(SQLException e){throw new IllegalStateException(e);}}\n}\n'''
p.write_text(s)

p=Path('src/main/java/vn/svarcade/tcg/fabric/TcgMod.java');s=p.read_text()
old='''                case "pull" -> {check(a.size()==2,"Choose a banner.");var results=store.pull(owner,a.get(0),a.get(1),Instant.now().getEpochSecond());reveals.put(player,results);}'''
new='''                case "pull" -> {
                    check(a.size()==2,"Choose a banner and payment currency.");
                    String request=a.get(1);boolean existing=store.hasReceipt(owner,request);
                    if(!existing)vn.svarcade.tcg.integration.BEconomyCardWorlds.chargePull(owner,request);
                    try{var results=store.pull(owner,a.get(0),request,Instant.now().getEpochSecond());reveals.put(player,results);}
                    catch(RuntimeException ex){if(!existing)vn.svarcade.tcg.integration.BEconomyCardWorlds.refundPull(owner,request);throw ex;}
                }'''
if old not in s: raise RuntimeError('TcgMod pull anchor missing')
s=s.replace(old,new)

finish='''        store.unlockDuel(m.id);UUID winner=winning==0?m.a:m.b,loser=winning==0?m.b:m.a;'''
if finish not in s: raise RuntimeError('TcgMod finish anchor missing')
s=s.replace(finish,finish+'''\n        vn.svarcade.tcg.integration.BEconomyCardWorlds.rewardMatch(m.id,m.a.toString(),m.b.toString(),m.npc,m.botDifficulty,m.ranked,winning);''')

# The legacy hard-PvE path mints an NPC card/internal coins and invokes EconomyRewards.
# Battle economy is now exclusively BEconomyCardWorlds, so keep that historical path unreachable.
if '        if(m.npc&&winning==0){' not in s: raise RuntimeError('legacy hard-PvE reward anchor missing')
s=s.replace('        if(m.npc&&winning==0){','        if(false&&m.npc&&winning==0){',1)

old='''for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,id.equals(winner)?"Victory!":"Duel complete.",0);}'''
new='''for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null){String base=id.equals(winner)?"Victory!":"Duel complete.";send(p,vn.svarcade.tcg.integration.BEconomyCardWorlds.resultMessage(base,m.id,id.toString()),0);}}'''
if old not in s: raise RuntimeError('battle result notice anchor missing')
s=s.replace(old,new)

old='''catalog=CobblemonCatalogHydrator.expand(Catalog.load(file));store=new CardStore(server.getSavePath(WorldSavePath.ROOT).resolve("svarcade-tcg/cards.db"),catalog,rng);duelRealm=new DuelRealmService(server);duelRealm.world();'''
new=old+'''if(server.isDedicated()&&Boolean.getBoolean("cardworlds.qa.focused")){vn.svarcade.tcg.integration.BEconomyCardWorlds.verifyRuntime();vn.svarcade.tcg.data.EffectEconomyQa.verify(catalog);}'''
if old not in s: raise RuntimeError('focused runtime QA startup anchor missing')
s=s.replace(old,new)

# Balance presentation crosses the snapshot boundary as shared data, never as a client API call.
old='Map<String,String> sellers){}'
if old not in s: raise RuntimeError('Snapshot currency balance component anchor missing')
s=s.replace(old,'Map<String,String> sellers,vn.svarcade.tcg.economy.CardWorldsCurrency.Balances currencyBalances){}')
old='var snap=new Snapshot('
if old not in s: raise RuntimeError('Snapshot currency balance construction anchor missing')
s=s.replace(old,'var currencyBalances=p.getServer().isDedicated()?vn.svarcade.tcg.integration.BEconomyCardWorlds.snapshotBalances(p):vn.svarcade.tcg.economy.CardWorldsCurrency.Balances.unavailable();\n        '+old)
old='orElse("Collector"))));'
if s.count(old)!=1: raise RuntimeError('Snapshot currency balance argument anchor missing')
s=s.replace(old,'orElse("Collector"))),currencyBalances);')
p.write_text(s)
