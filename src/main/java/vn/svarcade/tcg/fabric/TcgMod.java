package vn.svarcade.tcg.fabric;

import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.duel.Duel;
import vn.svarcade.tcg.economy.CardStore;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Thin platform adapter. A future SVArcade host can call the same independent services. */
public final class TcgMod implements ModInitializer {
    private static final Gson JSON=new Gson();
    private static TcgMod livingInstance;
    private static final long RECONNECT_GRACE_MS=120_000L;
    private Catalog catalog;private CardStore store;
    private vn.svarcade.tcg.integration.EconomyRewards economyRewards;
    private MessageService messages;
    private DuelRealmService duelRealm;
    private final Map<UUID,Match> spectating=new HashMap<>();
    private final SecureRandom rng=new SecureRandom();
    private final Map<UUID,Map<String,String>> placeholderCache=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,String> selectedDecks=new HashMap<>();
    private final Map<UUID,Match> matches=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Challenge> challenges=new HashMap<>();
    private final Map<UUID,Long> rateLimit=new HashMap<>();
    private final Map<UUID,UUID> tradePeers=new HashMap<>();
    private int tick;
    private final Map<UUID,List<CardStore.Pull>> reveals=new HashMap<>();
    private final Set<UUID> openRequests=new HashSet<>();
    private record Challenge(UUID challenger,String deck,boolean ranked){}
    private static final class Match {
        String id=UUID.randomUUID().toString(); UUID a,b; Duel duel;boolean ranked,npc;String botDifficulty="NONE";DuelRealmService.Arena arena;final LinkedHashSet<UUID> spectators=new LinkedHashSet<>();long activity=System.currentTimeMillis();
        String livingName=""; java.util.function.Consumer<Boolean> livingResult; java.util.Random aiRandom;
        long aDisconnectedAt=-1L,bDisconnectedAt=-1L;
        int seat(UUID id){return id.equals(a)?0:1;}
        UUID opponent(UUID id){return id.equals(a)?b:a;}
        void disconnected(UUID id,long at){if(id.equals(a))aDisconnectedAt=at;else if(id.equals(b))bDisconnectedAt=at;}
        void connected(UUID id){if(id.equals(a))aDisconnectedAt=-1L;else if(id.equals(b))bDisconnectedAt=-1L;}
        boolean humanDisconnected(){return aDisconnectedAt>=0||(!npc&&bDisconnectedAt>=0);}
        int expiredSeat(long now){
            if(aDisconnectedAt>=0&&now-aDisconnectedAt>=RECONNECT_GRACE_MS)return 0;
            if(!npc&&bDisconnectedAt>=0&&now-bDisconnectedAt>=RECONNECT_GRACE_MS)return 1;
            return -1;
        }
    }
    public record BinderCard(String serial,String name,String category,String type,int power,String text,String finish,String source,boolean available){}
    private Catalog previewCatalog;private Map<String,String> packPreviews=Map.of();
    public record BannerView(String id,String name,int price,int slots,int pity,int hardPity,boolean guaranteed,List<String> rates,String previewCard){}
    public record TradeView(String id,String offered,String requested,long coins,boolean incoming){}
    public record Snapshot(String notice,CardStore.Profile profile,List<BinderCard> binder,int page,int collected,int total,
                           List<String> decks,List<CardStore.Listing> market,List<BannerView> banners,List<String> players,
                           String challenge,Duel.View duel,List<String> guides,List<TradeView> trades,List<BinderCard> tradeShelf,String tradePeer,Map<String,Catalog.Card> definitions,Map<String,List<String>> deckTemplates,List<CardStore.Owned> inventory,Map<String,Integer> counts,List<CardStore.Deck> savedDecks,Map<String,String> formats,List<CardStore.Pull> pulls,String playerName,String opponentName,boolean spectator,boolean open,Catalog.Rules rules,Map<String,String> sellers){}
    @Override public void onInitialize() {
        livingInstance=this;
        vn.svarcade.tcg.integration.CardWorldsIntegrations.initialize();
        vn.svarcade.tcg.physical.PhysicalCards.initialize(this);
        vn.svarcade.tcg.integration.EconomyRewards.verifyCapabilities();
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success)vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();});
        PayloadTypeRegistry.playC2S().register(TcgPackets.Input.ID,TcgPackets.Input.CODEC);
        if(vn.svarcade.tcg.integration.CardWorldsIntegrations.capabilities().has("placeholder-api"))PlaceholderSupport.register(this);
        PayloadTypeRegistry.playS2C().register(TcgPackets.Snapshot.ID,TcgPackets.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TcgPackets.Input.ID,(payload,ctx)->ctx.server().execute(()->{
            try {TcgPackets.Request r=JSON.fromJson(payload.json(),TcgPackets.Request.class);if(r==null||r.action()==null||r.args()==null||r.args().size()>100)throw new IllegalArgumentException("Invalid request.");handle(ctx.player(),r);}
            catch(Exception ex){send(ctx.player(),message(ex),0);}
        }));
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try {
            Path config=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg");Files.createDirectories(config);
            Path file=config.resolve("catalog.json");if(!Files.exists(file))try(var in=Catalog.class.getResourceAsStream("/data/svarcade_tcg/catalog.json")){Files.copy(Objects.requireNonNull(in),file);}
            catalog=CobblemonCatalogHydrator.expand(Catalog.load(file));vn.svarcade.tcg.physical.PhysicalCards.bind(catalog);store=new CardStore(server.getSavePath(WorldSavePath.ROOT).resolve("svarcade-tcg/cards.db"),catalog,rng);duelRealm=new DuelRealmService(server);duelRealm.world();
        }catch(Exception ex){throw new IllegalStateException("TCG could not start safely",ex);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{vn.svarcade.tcg.physical.PhysicalCards.clear();try{if(store!=null)store.close();}catch(Exception ex){org.slf4j.LoggerFactory.getLogger("svarcade_tcg").error("Closing card store",ex);}placeholderCache.clear();selectedDecks.clear();matches.clear();spectating.clear();challenges.clear();rateLimit.clear();messages=null;duelRealm=null;});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            UUID id=handler.player.getUuid();Match m=matches.get(id);
            if(m!=null&&m.duel.winner()<0){
                m.connected(id);m.activity=System.currentTimeMillis();openRequests.add(id);if(duelRealm!=null&&m.arena!=null)duelRealm.enterDuelist(handler.player,m.arena,m.seat(id));
                send(handler.player,"Reconnected. Your duel is still active.",0);
                ServerPlayerEntity other=server.getPlayerManager().getPlayer(m.opponent(id));
                if(other!=null)send(other,handler.player.getName().getString()+" reconnected. Duel resumed.",0);
            } else {
                Match watch=spectating.get(id);if(watch!=null&&watch.duel.winner()<0&&duelRealm!=null&&watch.arena!=null){duelRealm.enterSpectator(handler.player,watch.arena,new ArrayList<>(watch.spectators).indexOf(id));openRequests.add(id);send(handler.player,"Spectator mode resumed.",0);}
            }
            send(handler.player,"",0);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            UUID id=handler.player.getUuid();Match m=matches.get(id);
            if(m!=null&&m.duel.winner()<0){
                long now=System.currentTimeMillis();m.disconnected(id,now);m.activity=now;
                ServerPlayerEntity other=server.getPlayerManager().getPlayer(m.opponent(id));
                if(other!=null)send(other,handler.player.getName().getString()+" disconnected. Duel paused for 120 seconds awaiting reconnect.",0);
            }
            challenges.remove(id);challenges.entrySet().removeIf(e->e.getValue().challenger.equals(id));rateLimit.remove(id);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->CardWorldsCommands.register(dispatcher,this));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!world.isClient&&player instanceof ServerPlayerEntity p&&world.getBlockState(hit.getBlockPos()).isOf(Blocks.CHISELED_SANDSTONE)) {
                String biome=world.getBiome(hit.getBlockPos()).getKey().map(k->k.getValue().toString()).orElse("");
                try{if(store.progress(p.getUuidAsString(),"desert_discovery",world.getRegistryKey().getValue().toString(),biome))send(p,"Discovery complete: Ancient Mew joins your collection.",0);}catch(Exception ex){org.slf4j.LoggerFactory.getLogger("svarcade_tcg").error("Discovery transaction",ex);}
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{if(++tick%20==0)for(Match m:new HashSet<>(matches.values())){
            long now=System.currentTimeMillis();
            int expired=m.expiredSeat(now);
            if(expired>=0&&m.duel.winner()<0){
                m.duel.act(expired,new Duel.Action("concede","",""),m.duel.revision());finish(server,m);continue;
            }
            if(!m.humanDisconnected()&&now-m.activity>600_000){m.duel.act(m.duel.view(0).priority(),new Duel.Action("concede","",""),m.duel.revision());finish(server,m);continue;}
            if(!m.humanDisconnected()&&m.npc&&m.duel.winner()<0&&m.duel.view(1).priority()==1){bot(m);broadcast(server,m);}
        }});
    }
    private String message(Exception ex){if(ex instanceof IllegalArgumentException)return ex.getMessage();org.slf4j.LoggerFactory.getLogger("svarcade_tcg").error("TCG request failed",ex);return "That action could not be completed. Please try again.";}
    private void handle(ServerPlayerEntity p,TcgPackets.Request r) {
        if(store==null)return;
        UUID player=p.getUuid();String owner=p.getUuidAsString();List<String>a=r.args();String notice="";int page=0;
        long now=System.nanoTime();if(now-rateLimit.getOrDefault(player,0L)<80_000_000L)return;rateLimit.put(player,now);
        try {
            switch(r.action()) {
                case "qa_special" -> {
                    check(Boolean.getBoolean("cardworlds.qa")&&p.hasPermissionLevel(2),"QA is disabled");check(a.size()==2,"Choose a special QA card");
                    Match m=matches.get(player);check(m!=null,"No active duel");m.duel.qaSpecialScenario(m.seat(player),a.get(0),Boolean.parseBoolean(a.get(1)));broadcast(p.getServer(),m);
                    String lp=vn.svarcade.tcg.integration.PlaceholderBridge.resolve(p.getCommandSource(),"%svarcade_tcg:lp%");
                    if(vn.svarcade.tcg.integration.CardWorldsIntegrations.capabilities().has("placeholder-api"))check(lp.equals(Integer.toString(m.duel.view(m.seat(player)).life().get(m.seat(player)))),"Dynamic PB4 placeholder failed");
                    check(vn.svarcade.tcg.integration.PlaceholderBridge.resolve(p.getCommandSource(),"%svarcade_tcg:opponent_hand_contents%").equals("—"),"Private placeholder was exposed");
                    org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_PLACEHOLDER_PROOF lp={} unresolved=false private=false",lp);
                }
                case "open" -> {openRequests.add(player);}
                case "refresh" -> {if(!a.isEmpty())page=Integer.parseInt(a.getFirst());}
                case "starter" -> {store.createProfile(owner,"crossroads");notice="Your starter collection and first deck are ready.";}
                case "save_deck" -> {check(a.size()>=3,"Choose a deck name and cards.");int split=Integer.parseInt(a.get(1));var format=CardStore.Format.valueOf(a.get(2));check(split>=0&&split<=a.size()-3,"Invalid deck.");store.saveDeck(owner,a.getFirst(),a.subList(3,3+split),a.subList(3+split,a.size()),format);selectedDecks.put(player,a.getFirst());notice="Deck saved.";}
                case "pull" -> {check(a.size()==2,"Choose a banner.");var results=store.pull(owner,a.get(0),a.get(1),Instant.now().getEpochSecond());reveals.put(player,results);}
                case "list" -> {check(a.size()==2,"Choose a card and price.");store.list(owner,a.get(0),Long.parseLong(a.get(1)));notice="Your card is now on the Exchange.";}
                case "buy" -> {store.buy(owner,a.getFirst());notice="Purchase complete.";}
                case "cancel_listing" -> {store.cancelListing(owner,a.getFirst());notice="Listing withdrawn.";}
                case "browse_trade" -> {var peer=p.getServer().getPlayerManager().getPlayer(a.getFirst());check(peer!=null&&!peer.getUuid().equals(player),"Collector unavailable.");tradePeers.put(player,peer.getUuid());notice="Choose a card to request from "+peer.getName().getString()+".";}
                case "offer" -> {check(a.size()==3&&tradePeers.containsKey(player),"Choose a collector and both cards.");UUID peer=tradePeers.get(player);store.offer(owner,peer.toString(),a.get(0),a.get(1),Long.parseLong(a.get(2)));notice="Trade offer sent. Your card and coins are held safely.";var other=p.getServer().getPlayerManager().getPlayer(peer);if(other!=null)send(other,p.getName().getString()+" sent a trade offer. Review it in the Exchange.",0);}
                case "accept_offer" -> {store.accept(owner,a.getFirst());notice="Trade complete.";}
                case "cancel_offer" -> {store.cancelOffer(owner,a.getFirst());notice="Trade cancelled and escrow returned.";}
                case "dealer" -> {store.dealer(owner,a.getFirst(),p.getWorld().getRegistryKey().getValue().toString(),(int)((p.getWorld().getTimeOfDay()/1000+6)%24));notice="The Midnight Collector accepts your exchange.";}
                case "challenge" -> {
                    check(a.size()==3,"Choose an opponent and a deck.");check(!matches.containsKey(player),"Finish your current duel first.");
                    ServerPlayerEntity other=p.getServer().getPlayerManager().getPlayer(a.get(0));check(other!=null&&!other.getUuid().equals(player),"Opponent unavailable.");check(!matches.containsKey(other.getUuid()),"That collector is already dueling.");
                    challenges.put(other.getUuid(),new Challenge(player,a.get(1),Boolean.parseBoolean(a.get(2))));send(other,p.getName().getString()+" challenges you to a duel.",0);notice="Challenge sent.";
                }
                case "accept" -> {
                    Challenge c=challenges.get(player);check(c!=null,"No pending challenge.");check(!matches.containsKey(player)&&!matches.containsKey(c.challenger),"A player is already dueling.");
                    check(p.getServer().getPlayerManager().getPlayer(c.challenger)!=null,"Challenger disconnected.");
                    Match m=new Match();m.a=c.challenger;m.b=player;m.ranked=c.ranked;
                    try{var da=lockDeck(m.a.toString(),c.deck,m.ranked,m.id);var db=lockDeck(owner,a.getFirst(),m.ranked,m.id);m.duel=new Duel(catalog,da.get(0),da.get(1),db.get(0),db.get(1),rng);}catch(Exception ex){store.unlockDuel(m.id);throw ex;}
                    matches.put(m.a,m);matches.put(m.b,m);challenges.remove(player);prepareRealm(p.getServer(),m);openRequests.add(m.a);openRequests.add(m.b);broadcast(p.getServer(),m);return;
                }
                case "npc","pve" -> {
                    check(!matches.containsKey(player),"Finish your current duel first.");
                    String difficulty=r.action().equals("npc")?"NORMAL":(a.size()>=2?a.get(1):"NORMAL").toUpperCase(Locale.ROOT);
                    check(Set.of("EASY","NORMAL","HARD").contains(difficulty),"Bot difficulty must be EASY, NORMAL or HARD.");
                    Match m=new Match();m.a=player;m.b=UUID.randomUUID();m.npc=true;m.botDifficulty=difficulty;
                    String deckName=a.isEmpty()||a.getFirst().isBlank()?"Starter":a.getFirst();
                    try{var da=lockDeck(owner,deckName,false,m.id);m.duel=new Duel(catalog,da.get(0),da.get(1),catalog.starters().get("crossroads"),List.of(),rng);}catch(Exception ex){store.unlockDuel(m.id);throw ex;}
                    matches.put(player,m);prepareRealm(p.getServer(),m);openRequests.add(player);notice=difficulty;
                }
                case "duel" -> {
                    check(a.size()==3,"Choose a duel action.");check(!spectating.containsKey(player),"Spectators cannot submit duel actions.");Match m=matches.get(player);check(m!=null,"You are not in a duel.");
                    check(!m.humanDisconnected(),"Duel paused while a player reconnects.");
                    m.duel.act(m.seat(player),new Duel.Action(a.get(0),a.get(1),a.get(2)),r.revision());m.activity=System.currentTimeMillis();broadcast(p.getServer(),m);return;
                }
                default -> throw new IllegalArgumentException("Unknown action.");
            }
        }catch(Exception ex){notice=message(ex);}
        send(p,notice,page);
    }
    /** World Comes Alive adapter: identities/decks remain owned by the living-world store. */
    public static List<String> livingWorldDeck(){return livingInstance==null||livingInstance.catalog==null?List.of():List.copyOf(livingInstance.catalog.starters().get("crossroads"));}
    public static boolean livingNpcBusy(UUID npc){TcgMod self=livingInstance;return self!=null&&self.matches.values().stream().anyMatch(m->m.npc&&m.b.equals(npc));}
    public static void challengeLivingNpc(ServerPlayerEntity player,UUID npc,String name,List<String> deck,double skill,java.util.function.Consumer<Boolean> result){
        TcgMod self=livingInstance;check(self!=null&&self.store!=null,"Card Worlds is not ready.");check(!self.matches.containsKey(player.getUuid()),"Finish your current duel first.");
        check(self.matches.values().stream().noneMatch(active->active.npc&&active.b.equals(npc)),"This citizen is already in a Card Worlds duel.");
        check(deck!=null&&!deck.isEmpty(),"This citizen has no deck.");String owner=player.getUuidAsString();if(!self.store.hasProfile(owner))self.store.createProfile(owner,"crossroads");
        String selected=self.selectedDecks.getOrDefault(player.getUuid(),self.store.deckNames(owner).getFirst());
        Match m=new Match();m.a=player.getUuid();m.b=npc;m.npc=true;m.livingName=name;m.livingResult=result;m.botDifficulty=skill>.65?"HARD":skill<.25?"EASY":"NORMAL";
        try{var main=self.lockDeck(owner,selected,false,m.id);m.duel=new Duel(self.catalog,main.get(0),main.get(1),List.copyOf(deck),List.of(),self.rng);}catch(Exception ex){self.store.unlockDuel(m.id);throw ex;}
        self.matches.put(m.a,m);self.prepareRealm(player.getServer(),m);self.openRequests.add(m.a);self.broadcast(player.getServer(),m);
    }
    /** Pure engine evaluation. Caller owns worker scheduling and applies the result on the server thread. */
    public static int simulateLivingDuel(List<String> a,List<String> b,long seed,double skillA,double skillB){
        TcgMod self=livingInstance;if(self==null||self.catalog==null)return -1;Match m=new Match();m.aiRandom=new Random(seed);m.duel=new Duel(self.catalog,List.copyOf(a),List.of(),List.copyOf(b),List.of(),new Random(seed));
        for(int i=0;i<1200&&m.duel.winner()<0;i++){int seat=m.duel.view(0).priority();double skill=seat==0?skillA:skillB;m.botDifficulty=skill>.65?"HARD":skill<.25?"EASY":"NORMAL";self.bot(m,seat);}return m.duel.winner();
    }

    private void bot(Match m){bot(m,1);}
    private void bot(Match m,int seat){
        java.util.Random random=m.aiRandom==null?rng:m.aiRandom;
        Duel.View v=m.duel.view(seat);
        List<Duel.Action> actions=new ArrayList<>();
        List<Duel.VisibleCard> mine=v.cards().stream().filter(c->c.controller()==seat).toList();
        List<Duel.VisibleCard> field=mine.stream().filter(c->c.zone()==Duel.Zone.FIELD).toList();
        List<Duel.VisibleCard> enemy=v.cards().stream().filter(c->c.controller()!=seat&&c.zone()==Duel.Zone.FIELD).toList();

        if(v.open()&&v.turnPlayer()==seat&&(v.phase().equals("MAIN1")||v.phase().equals("MAIN2"))){
            for(var c:mine)if((c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.EXTRA)&&c.category().equals("pokemon")){
                actions.add(new Duel.Action("play",c.token(),""));
                for(var material:field)actions.add(new Duel.Action("play",c.token(),material.token()));
                for(int i=0;i<field.size();i++)for(int j=i+1;j<field.size();j++)
                    actions.add(new Duel.Action("play",c.token(),field.get(i).token()+","+field.get(j).token()));
            }
        }

        if(v.open()&&v.turnPlayer()==seat&&v.phase().equals("BATTLE")){
            for(var attacker:field){
                actions.add(new Duel.Action("attack",attacker.token(),""));
                for(var target:enemy)actions.add(new Duel.Action("attack",attacker.token(),target.token()));
            }
        }

        for(var c:mine)if(c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.FIELD){
            Catalog.Card d=botDefinition(c);
            if(d==null||d.effect()==null)continue;
            String target=d.effect().target();
            if(target.equals("none"))actions.add(new Duel.Action("activate",c.token(),""));
            else if(target.equals("chain")&&!v.chain().isEmpty())actions.add(new Duel.Action("activate",c.token(),Integer.toString(v.chain().size())));
            else if(target.equals("enemy"))for(var t:enemy)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("ally"))for(var t:field)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("grave"))for(var t:mine)if(t.zone()==Duel.Zone.DISCARD)actions.add(new Duel.Action("activate",c.token(),t.token()));
        }

        if(v.open()&&v.turnPlayer()==seat)actions.add(new Duel.Action("next","",""));
        actions.add(new Duel.Action("pass","",""));

        if("EASY".equals(m.botDifficulty))Collections.shuffle(actions,random);
        else{
            boolean hard="HARD".equals(m.botDifficulty);
            actions.sort(Comparator.comparingInt((Duel.Action action)->botScore(v,action,hard)).reversed());
            if(!hard&&actions.size()>2&&random.nextInt(100)<22)Collections.swap(actions,0,1+random.nextInt(Math.min(3,actions.size()-1)));
        }

        for(var action:actions)try{m.duel.act(seat,action,m.duel.revision());return;}catch(IllegalArgumentException ignored){}
    }

    private Catalog.Card botDefinition(Duel.VisibleCard card){
        return catalog.cards().values().stream().filter(d->d.name().equals(card.name())).findFirst().orElse(null);
    }

    private int botScore(Duel.View v,Duel.Action action,boolean hard){
        if(action.kind().equals("pass"))return -100000;
        if(action.kind().equals("next"))return -50000;
        Duel.VisibleCard source=v.cards().stream().filter(c->c.token().equals(action.card())).findFirst().orElse(null);
        if(source==null)return -90000;
        Catalog.Card def=botDefinition(source);
        if(action.kind().equals("play")){
            int score=1200+source.power()+(def==null?0:def.level()*90);
            if(!action.target().isBlank())for(String token:action.target().split(",")){
                Duel.VisibleCard tribute=v.cards().stream().filter(c->c.token().equals(token)).findFirst().orElse(null);
                if(tribute!=null)score-=hard?tribute.power()/2:tribute.power()/3;
            }
            return score;
        }
        if(action.kind().equals("attack")){
            if(action.target().isBlank())return 2600+source.power();
            Duel.VisibleCard target=v.cards().stream().filter(c->c.token().equals(action.target())).findFirst().orElse(null);
            if(target==null)return 500;
            int trade=source.power()-target.power();
            return hard?((trade>=0?3600:-1800)+target.power()+trade):((trade>=0?2500:-500)+target.power()/2);
        }
        if(action.kind().equals("activate")&&def!=null&&def.effect()!=null){
            int base=switch(def.effect().operation()){
                case "negate" -> 5200;
                case "banish" -> 4900;
                case "destroy" -> 4700;
                case "return" -> 4300;
                case "damage" -> 3500+def.effect().amount();
                case "draw" -> 3300+def.effect().amount()*300;
                case "heal" -> 2100+def.effect().amount()/2;
                case "boost" -> 2300+def.effect().amount();
                case "shield" -> 2400;
                default -> 1600;
            };
            if(hard)base-=def.effect().lifeCost();
            if(!action.target().isBlank()){
                Duel.VisibleCard target=v.cards().stream().filter(c->c.token().equals(action.target())).findFirst().orElse(null);
                if(target!=null&&target.controller()==0)base+=hard?target.power()/2:target.power()/4;
            }
            return base;
        }
        return 0;
    }

    private void broadcast(MinecraftServer server,Match m){if(m.duel.winner()>=0){finish(server,m);return;}LinkedHashSet<UUID> viewers=new LinkedHashSet<>();viewers.add(m.a);if(!m.npc)viewers.add(m.b);viewers.addAll(m.spectators);for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,"",0);}}
    private void finish(MinecraftServer server,Match m){
        int winning=m.duel.winner();if(winning<0)return;
        store.unlockDuel(m.id);UUID winner=winning==0?m.a:m.b,loser=winning==0?m.b:m.a;
        if(m.livingResult!=null)m.livingResult.accept(winning==0);
        store.recordDuelResult(m.a.toString(),m.id,winning==0);if(!m.npc)store.recordDuelResult(m.b.toString(),m.id,winning==1);
        if(m.ranked){store.rankedResult(winner.toString(),loser.toString(),m.id);placeholderCache.replaceAll((id,values)->{var update=new HashMap<>(values);update.put("rank",Integer.toString(1+store.playersAboveRating(Integer.parseInt(values.getOrDefault("rating","1000")))));return Map.copyOf(update);});}
        if(m.npc&&winning==0){
            ServerPlayerEntity botWinner=server.getPlayerManager().getPlayer(m.a);
            if("HARD".equals(m.botDifficulty)){
                store.npcVictory(m.a.toString(),"pewter_victory",m.id,Instant.now().getEpochSecond());
                if(botWinner!=null){
                    if(economyRewards==null)economyRewards=new vn.svarcade.tcg.integration.EconomyRewards();
                    var payout=economyRewards.payHardWin(botWinner,m.id);
                    store.lastReward(m.a.toString(),payout.message());send(botWinner,payout.message(),0);
                }
            }
        }
        LinkedHashSet<UUID> viewers=new LinkedHashSet<>();viewers.add(m.a);if(!m.npc)viewers.add(m.b);viewers.addAll(m.spectators);
        teardownRealm(server,m);
        openRequests.addAll(viewers);
        for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,id.equals(winner)?"Victory!":"Duel complete.",0);}
        for(UUID id:new LinkedHashSet<>(m.spectators))spectating.remove(id);
        m.spectators.clear();
        matches.remove(m.a);matches.remove(m.b);
    }
    private void send(ServerPlayerEntity p,String notice,int page) {
        if(store==null)return;
        if(!ServerPlayNetworking.canSend(p,TcgPackets.Snapshot.ID)){p.sendMessage(Text.literal(notice+" Install SVArcade-TCG on your client and use /tcg."),false);return;}
        String owner=p.getUuidAsString();List<BinderCard> binder=store.binder(owner,Math.max(0,page)).stream().map(c->{var d=catalog.card(c.card());return new BinderCard(c.serial(),d.name(),d.category(),d.type(),d.power(),d.text(),c.finish(),c.origin(),c.lock().isEmpty());}).toList();
        if(previewCatalog!=catalog){packPreviews=vn.svarcade.tcg.data.PackPreviews.select(catalog);previewCatalog=catalog;}
        List<BannerView> banners=new ArrayList<>();catalog.banners().forEach((id,b)->{
            var pity=store.pity(owner,b.family());List<String> rates=store.probabilities(owner,id).entrySet().stream().map(e->e.getKey()+": "+String.format(java.util.Locale.ROOT,"%.2f%%",e.getValue())).toList();
            banners.add(new BannerView(id,b.name(),b.price(),b.slots(),pity.pulls(),b.hardPity(),pity.guaranteed(),rates,packPreviews.getOrDefault(id,"")));
        });
        Match m=spectating.get(p.getUuid());boolean spectatorView=m!=null;if(m==null)m=matches.get(p.getUuid());Challenge challenge=challenges.get(p.getUuid());
        ServerPlayerEntity duelA=m==null?null:p.getServer().getPlayerManager().getPlayer(m.a);ServerPlayerEntity duelB=m==null||m.npc?null:p.getServer().getPlayerManager().getPlayer(m.b);
        String leftName=spectatorView?(duelA==null?"Duelist A":duelA.getName().getString()):p.getName().getString();
        String rightName=m==null?"":m.npc?(m.livingName.isBlank()?"Pewter Challenger":m.livingName):spectatorView?(duelB==null?"Duelist B":duelB.getName().getString()):Optional.ofNullable(p.getServer().getPlayerManager().getPlayer(m.seat(p.getUuid())==0?m.b:m.a)).map(x->x.getName().getString()).orElse("Opponent");
        var snap=new Snapshot(notice,store.profile(owner),binder,page,store.completion(owner).size(),catalog.cards().size(),store.deckNames(owner),store.market(page),banners,
            p.getServer().getPlayerManager().getPlayerList().stream().filter(x->!x.getUuid().equals(p.getUuid())).map(x->x.getName().getString()).toList(),challenge==null?"":"A collector is waiting. Select Accept challenge.",m==null?null:(spectatorView?m.duel.spectatorView():m.duel.view(m.seat(p.getUuid()))),
            List.of("Ancient Sands: inspect chiseled sandstone in a desert to discover an archaeology edition.","Pewter Challenger: win a practice duel to earn Onix.","Midnight Collector: in the Nether, 23:00–02:00, exchange three Gastly after one discovery.","Ranked bans Shadow Lugia. All finishes share the same gameplay.","Use Pass to offer a response; both players must pass before a chain resolves."),
            store.trades(owner).stream().map(t->new TradeView(t.id(),t.offered(),t.requested(),t.coins(),t.recipient().equals(owner))).toList(),
            tradePeers.containsKey(p.getUuid())?store.binder(tradePeers.get(p.getUuid()).toString(),page).stream().filter(c->c.lock().isEmpty()).map(c->{var d=catalog.card(c.card());return new BinderCard(c.serial(),d.name(),d.category(),d.type(),d.power(),d.text(),c.finish(),c.origin(),true);}).toList():List.of(),
            tradePeers.containsKey(p.getUuid())?Optional.ofNullable(p.getServer().getPlayerManager().getPlayer(tradePeers.get(p.getUuid()))).map(x->x.getName().getString()).orElse("Collector"):"",catalog.cards(),catalog.starters(),store.collection(owner),store.completion(owner),store.deckNames(owner).stream().map(n->store.deck(owner,n)).toList(),store.formats(owner),reveals.getOrDefault(p.getUuid(),List.of()),leftName,rightName,spectatorView,openRequests.remove(p.getUuid()),catalog.rules(),store.market(page).stream().collect(java.util.stream.Collectors.toMap(CardStore.Listing::id,l->Optional.ofNullable(p.getServer().getUserCache()).flatMap(cache->cache.getByUuid(UUID.fromString(l.seller()))).map(com.mojang.authlib.GameProfile::getName).orElse("Collector"))));
        reveals.remove(p.getUuid());
        cachePlaceholders(p,snap);ServerPlayNetworking.send(p,new TcgPackets.Snapshot(JSON.toJson(snap)));
    }
    MessageService commandMessages(MinecraftServer server){
        if(messages==null)messages=new MessageService(server);
        return messages;
    }
    public CardStore physicalStore(){return store;}
    public void physicalSync(ServerPlayerEntity player){send(player,"",0);}
    Catalog commandCatalog(){return catalog;}
    CardStore commandStore(){return store;}
    boolean commandHasActiveDuels(){return !matches.isEmpty();}
    void commandOpen(ServerPlayerEntity player){openRequests.add(player.getUuid());send(player,"",0);}
    List<String> commandSpectatable(MinecraftServer server){
        LinkedHashSet<String> out=new LinkedHashSet<>();for(Match m:new LinkedHashSet<>(matches.values())){ServerPlayerEntity a=server.getPlayerManager().getPlayer(m.a);if(a!=null)out.add(a.getName().getString());if(!m.npc){ServerPlayerEntity b=server.getPlayerManager().getPlayer(m.b);if(b!=null)out.add(b.getName().getString());}}return List.copyOf(out);
    }
    void commandSpectate(ServerPlayerEntity viewer,ServerPlayerEntity target){
        check(!matches.containsKey(viewer.getUuid()),"Finish your current duel first.");Match m=matches.get(target.getUuid());check(m!=null,"That player is not in a duel.");
        commandSpectateLeave(viewer);spectating.put(viewer.getUuid(),m);m.spectators.add(viewer.getUuid());if(m.arena==null)prepareRealm(viewer.getServer(),m);duelRealm.enterSpectator(viewer,m.arena,m.spectators.size()-1);openRequests.add(viewer.getUuid());send(viewer,"",0);
    }
    void commandSpectateLeave(ServerPlayerEntity viewer){
        Match watched=spectating.remove(viewer.getUuid());if(watched!=null)watched.spectators.remove(viewer.getUuid());
        Match own=matches.get(viewer.getUuid());
        if(duelRealm!=null){if(own!=null&&own.arena!=null)duelRealm.enterDuelist(viewer,own.arena,own.seat(viewer.getUuid()));else duelRealm.restore(viewer);}
        openRequests.add(viewer.getUuid());send(viewer,"",0);
    }
    void commandQaSpectatorEnter(ServerPlayerEntity player){
        Match m=matches.get(player.getUuid());check(m!=null,"Start a duel first.");check(!spectating.containsKey(player.getUuid()),"Already in spectator preview.");
        spectating.put(player.getUuid(),m);m.spectators.add(player.getUuid());
        if(m.arena==null)prepareRealm(player.getServer(),m);
        duelRealm.enterSpectator(player,m.arena,Math.max(0,m.spectators.size()-1));openRequests.add(player.getUuid());send(player,"QA spectator preview.",0);
    }
    void commandQaSpellTrap(ServerPlayerEntity player,String stage){
        Match m=matches.get(player.getUuid());check(m!=null,"Start a duel first.");check(!spectating.containsKey(player.getUuid()),"Leave spectator mode first.");
        int seat=m.seat(player.getUuid());
        switch(stage){case "prepare"->m.duel.qaPrepareSpellTrapScenario(seat);case "chain"->m.duel.qaOpenSpellTrapChain(seat);case "resolve"->m.duel.qaResolveSpellTrapChain(seat);default->throw new IllegalArgumentException("Unknown Spell/Trap QA stage.");}
        openRequests.add(player.getUuid());send(player,"QA Spell/Trap "+stage+".",0);
    }
    void commandQaCreation(ServerPlayerEntity player,String stage){
        Match m=matches.get(player.getUuid());check(m!=null,"Start a duel first.");check(!spectating.containsKey(player.getUuid()),"Leave spectator mode first.");
        int seat=m.seat(player.getUuid());
        switch(stage){case "prepare"->m.duel.qaPrepareCreationScenario(seat);case "summon"->m.duel.qaSummonCreation(seat);case "tick"->m.duel.qaTickCreation(seat);default->throw new IllegalArgumentException("Unknown Creation QA stage.");}
        openRequests.add(player.getUuid());send(player,"QA Creation "+stage+".",0);
    }
    void commandQaPosition(ServerPlayerEntity player){
        Match m=matches.get(player.getUuid());check(m!=null,"Start a duel first.");check(!spectating.containsKey(player.getUuid()),"Leave spectator mode first.");
        int seat=m.seat(player.getUuid());m.duel.qaPreparePositionScenario(seat);openRequests.add(player.getUuid());send(player,"QA monster positions prepared.",0);
    }
    String commandRealmStatus(MinecraftServer server){if(duelRealm==null)duelRealm=new DuelRealmService(server);return duelRealm.status();}
    void commandRealmReturn(ServerPlayerEntity player){if(duelRealm!=null)duelRealm.restore(player);}
    private void prepareRealm(MinecraftServer server,Match m){if(duelRealm==null)duelRealm=new DuelRealmService(server);m.arena=duelRealm.allocate(m.id);openRequests.add(m.a);ServerPlayerEntity a=server.getPlayerManager().getPlayer(m.a);if(a!=null)duelRealm.enterDuelist(a,m.arena,0);if(!m.npc){openRequests.add(m.b);ServerPlayerEntity b=server.getPlayerManager().getPlayer(m.b);if(b!=null)duelRealm.enterDuelist(b,m.arena,1);}}
    private void teardownRealm(MinecraftServer server,Match m){if(duelRealm==null)return;for(UUID id:new LinkedHashSet<>(m.spectators)){ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}for(UUID id:List.of(m.a,m.b)){ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}duelRealm.release(m.arena);}
    synchronized void commandReload(MinecraftServer server)throws Exception{
        vn.svarcade.tcg.integration.CardWorldsIntegrations.reload();placeholderCache.clear();
        commandMessages(server).reload();
        if(!matches.isEmpty())throw new IllegalStateException("ACTIVE_DUELS");
        if(vn.svarcade.tcg.physical.BlankCapture.hasActiveAttempts())throw new IllegalStateException("ACTIVE_CAPTURES");
        Path config=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg");
        Files.createDirectories(config);
        Path file=config.resolve("catalog.json");
        Catalog next=CobblemonCatalogHydrator.expand(Catalog.load(file));
        Path db=server.getSavePath(WorldSavePath.ROOT).resolve("svarcade-tcg/cards.db");
        Catalog previousCatalog=catalog;
        CardStore previousStore=store;
        if(previousStore!=null)previousStore.close();
        try{
            store=new CardStore(db,next,rng);
            catalog=next;vn.svarcade.tcg.physical.PhysicalCards.bind(catalog);
        }catch(Exception ex){
            catalog=previousCatalog;
            store=previousCatalog==null?null:new CardStore(db,previousCatalog,rng);
            throw ex;
        }
        economyRewards=new vn.svarcade.tcg.integration.EconomyRewards();
    }
    long placeholderCoins(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).coins();
    }
    int placeholderRating(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).rating();
    }
    int placeholderCards(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).owned();
    }
    int placeholderDecks(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.deckNames(player.getUuidAsString()).size();
    }
    String placeholderDuelState(ServerPlayerEntity player){
        Match match=matches.get(player.getUuid());
        return match==null?"idle":match.npc?"pve":"pvp";
    }
    String placeholderBotDifficulty(ServerPlayerEntity player){
        Match match=matches.get(player.getUuid());
        return match==null||!match.npc?"none":match.botDifficulty.toLowerCase(Locale.ROOT);
    }

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
    private static void check(boolean v,String m){if(!v)throw new IllegalArgumentException(m);}
}
