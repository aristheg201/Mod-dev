package vn.worldcomesalive.server;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.ai.Cognition;
import vn.worldcomesalive.ai.CognitiveQueue;
import vn.worldcomesalive.ai.CognitiveQueue.Wake;
import vn.worldcomesalive.world.*;
import vn.worldcomesalive.WorldComesAlive;
import net.minecraft.server.*;
import net.minecraft.server.world.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.*;
import net.minecraft.util.math.*;
import net.minecraft.util.*;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import java.util.*;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.village.VillagerProfession;

/** Server-thread authority, budgeted cognition and block mutation, persistent logical lives at every LOD. */
public final class WorldSimulation {
    private static WorldSimulation active;
    public static WorldSimulation active(){return active;}
    private static final Logger LOG=LoggerFactory.getLogger("world-comes-alive");
    public final MinecraftServer server;
    public final ServerWorld world;
    public LivingWorld state;
    public WorldContent data;
    private final WorldStore store;
    private final RoadRoutes routes=new RoadRoutes();
    private final Map<UUID,CitizenEntity> entities=new HashMap<>();
    private final Map<UUID,com.cobblemon.mod.common.entity.pokemon.PokemonEntity> partners=new HashMap<>();
    private final CognitiveQueue wakes=new CognitiveQueue();
    private final ArrayDeque<SettlementStructures.Placement> placements=new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> discoveries=new ArrayDeque<>();
    private String buildingSettlement="";
    public long lastMicros,maxMicros,deferred,nearbyCount,urgentReactions;
    private long nextCheckpoint;
    private final java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"wca-pure-planning");t.setDaemon(true);return t;});
    private final Set<String> cardNights=new HashSet<>();
    public WorldSimulation(MinecraftServer server,WorldContent data)throws IOException{
        this.server=server;this.world=server.getOverworld();this.data=data;store=new WorldStore(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("world-comes-alive"));state=store.load();state.clock=world.getTime();active=this;
        for(Npc n:state.npcs.values())enqueue(n,Math.max(state.clock,n.nextCognition),3);
        for(Settlement s:state.settlements.values())if(!s.ready){queueBuild(s);break;}
        LOG.info("WCA_LOADED settlements={} npcs={} revision={} identities={}",state.settlements.size(),state.npcs.size(),state.revision,state.npcs.keySet());
    }
    public void reload(WorldContent content){data=content;routes.invalidate();for(Npc n:state.npcs.values()){n.plan.clear();var profession=data.professions.get(n.profession);if(profession!=null){n.schedule.put("work_start",profession.start());n.schedule.put("work_end",profession.end());}n.version++;enqueue(n,state.clock,3);}LOG.info("WCA_DATA_RELOADED professions={} dialogue={} archetypes={}",data.professions.size(),data.dialogue.size(),data.archetypes.size());}
    public static long siteSeed(long seed,int rx,int rz){return seed^(rx*341873128712L)^(rz*132897987541L)^0x51C0A113;}
    public static ChunkPos candidate(long seed,int rx,int rz,int cell){Random r=new Random(siteSeed(seed,rx,rz));return new ChunkPos(rx*cell+cell/2+r.nextInt(5)-2,rz*cell+cell/2+r.nextInt(5)-2);}
    public ChunkPos nearestCandidate(BlockPos pos){int rx=Math.floorDiv(pos.getX()>>4,data.regionChunks),rz=Math.floorDiv(pos.getZ()>>4,data.regionChunks);ChunkPos best=null;double distance=Double.MAX_VALUE;for(int x=rx-1;x<=rx+1;x++)for(int z=rz-1;z<=rz+1;z++){ChunkPos c=candidate(world.getSeed(),x,z,data.regionChunks);double d=c.getCenterAtY(0).getSquaredDistance(pos.getX(),0,pos.getZ());if(d<distance){distance=d;best=c;}}return best;}
    public void discover(ChunkPos pos){ChunkPos candidate=candidate(world.getSeed(),Math.floorDiv(pos.x,data.regionChunks),Math.floorDiv(pos.z,data.regionChunks),data.regionChunks);if(candidate.equals(pos)&&!state.surveyed.contains(pos.toString()))discoveries.add(pos);}
    private void generate(ChunkPos pos){
        String survey=pos.toString();if(!state.surveyed.add(survey))return;
        int x=pos.getCenterX(),z=pos.getCenterZ();world.getChunk(x>>4,z>>4);int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        if(y<=world.getSeaLevel()+1||y>180)return;
        int variance=0;for(int dx:new int[]{-35,0,35})for(int dz:new int[]{-35,0,35}){world.getChunk((x+dx)>>4,(z+dz)>>4);variance=Math.max(variance,Math.abs(world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz)-1-y));}if(variance>18)return;
        String biome=world.getBiome(new BlockPos(x,y,z)).getKey().map(k->k.getValue().toString()).orElse("plains");if(biome.contains("ocean")||biome.contains("river"))return;
        long seed=siteSeed(world.getSeed(),Math.floorDiv(pos.x,data.regionChunks),Math.floorDiv(pos.z,data.regionChunks));
        String archetype=data.archetypes.get((int)Math.floorMod(seed,Math.min(4,data.archetypes.size()))).id();
        Settlement s=SettlementBootstrap.create(seed,new Pos(x,y,z),biome,data,archetype);state.settlements.put(s.id,s);
        for(Settlement other:state.settlements.values())if(!other.id.equals(s.id)&&other.center.distance(s.center)<1200){s.routes.add(other.id);other.routes.add(s.id);}
        queueBuild(s);LOG.info("WCA_GENERATED id={} name={} archetype={} region={} origin={} plots={} manualSetup=false",s.id,s.name,s.archetype,s.region,s.center,s.buildings.size());
    }
    private void queueBuild(Settlement s){buildingSettlement=s.id;SettlementStructures.clearSite(s,world,placements);SettlementStructures.roads(s,world,placements);var region=data.regions.stream().filter(r->r.id().equals(s.region)).findFirst().orElse(data.regions.getLast());for(Building b:s.buildings.values())if(!b.built)SettlementStructures.building(b,region,world,placements);}
    public void tick(){
        long start=System.nanoTime();state.clock=world.getTime();
        for(int i=0;i<512&&!placements.isEmpty();i++){var p=placements.remove();world.setBlockState(p.pos(),p.state(),2);}
        if(placements.isEmpty()&&!buildingSettlement.isBlank()){
            Settlement s=state.settlements.get(buildingSettlement);s.buildings.values().forEach(b->b.built=true);SettlementBootstrap.populate(state,s,data,vn.svarcade.tcg.fabric.TcgMod.livingWorldDeck());for(UUID id:s.residents)enqueue(state.npcs.get(id),state.clock+Math.floorMod(id.hashCode(),200),3);buildingSettlement="";save();LOG.info("WCA_BOOTSTRAP_READY id={} households={} residents={} beds={} manualSetup=false",s.id,s.households.size(),s.residents.size(),s.buildings.values().stream().mapToInt(b->b.beds).sum());
        }
        if(placements.isEmpty()&&!discoveries.isEmpty())generate(discoveries.remove());
        int processed=0;long budget=System.nanoTime()+2_000_000;
        while(wakes.hasReady(state.clock)&&processed<64){
            if(System.nanoTime()>budget){deferred++;break;}
            Wake wake=wakes.remove();Npc n=state.npcs.get(wake.npc());if(n==null||wake.version()!=n.cognitionVersion)continue;
            think(n);processed++;state.decisions++;
        }
        if(state.clock%10==0)moveVisible();
        if(state.clock%20==0)relevance();
        if(state.clock%600==0)events();
        if(state.clock>=nextCheckpoint){save();nextCheckpoint=state.clock+1200;}
        lastMicros=(System.nanoTime()-start)/1000;maxMicros=Math.max(maxMicros,lastMicros);
    }
    private void enqueue(Npc n,long due,int priority){n.nextCognition=due;n.cognitionVersion++;wakes.add(new Wake(n.id,due,n.cognitionVersion,priority==3?(n.simulation.equals("full")?2:n.simulation.equals("reduced")?4:5):priority),state.clock);}
    private void think(Npc n){
        Settlement s=state.settlements.get(n.settlement);if(s==null||!s.ready)return;
        if(n.lifeStage.equals("deceased"))return;
        if(n.interactionUntil>state.clock&&n.interruptUntil<=state.clock){enqueue(n,n.interactionUntil,1);return;}
        if(n.travel!=null){if(!entities.containsKey(n.id)){n.location=n.travel.at(state.clock);if(n.travel.arrived(state.clock)){n.location=n.travel.route.getLast();n.travel=null;}}if(n.travel!=null){enqueue(n,state.clock+100,3);return;}}
        if(n.actionUntil>state.clock&&n.interruptUntil<=state.clock){enqueue(n,n.actionUntil,3);return;}
        if(n.plan.isEmpty()){
            Building market=s.service("bakery");var d=Cognition.decide(n,state.clock,data,market!=null&&market.stock.getOrDefault("minecraft:bread",0)>0);n.goal=d.goal();n.plan=new ArrayList<>(d.plan());
        }
        if(!n.plan.isEmpty()){
            String next=n.plan.removeFirst();var action=data.actions.stream().filter(a->a.id().equals(next)).findFirst().orElse(null);if(action!=null)execute(n,s,action.execution());
        }
        n.version++;enqueue(n,state.clock+(n.interruptUntil>state.clock?20:n.simulation.equals("full")?100:300),n.interruptUntil>state.clock?0:3);
    }
    private void execute(Npc n,Settlement s,String action){
        Building home=s.buildings.get(n.home),work=s.buildings.get(n.workplace),tavern=s.service("tavern"),market=s.service("bakery"),guard=s.service("guardhouse");
        switch(action){
            case "home"->travel(n,s,home,"returning home");
            case "work"->travel(n,s,work==null?home:work,"going to work");
            case "tavern"->travel(n,s,tavern==null?home:tavern,"visiting tavern");
            case "market"->travel(n,s,market==null?home:market,"shopping");
            case "guard"->{travel(n,s,guard==null?home:guard,"seeking safety");n.emotion="alarmed";n.plan.clear();}
            case "sleep"->{n.activity="sleeping";n.needs.put("fatigue",Math.max(0,n.need("fatigue")-.35));n.actionUntil=state.clock+1800;}
            case "buy"->{if(market!=null&&n.money>=3&&market.stock.getOrDefault("minecraft:bread",0)>0){market.stock.merge("minecraft:bread",-1,Integer::sum);market.money+=3;n.money-=3;n.inventory.merge("minecraft:bread",1,Integer::sum);state.transact(n.id.toString(),market.id,"minecraft:bread",1,3,"food purchase");}else n.plan.clear();}
            case "gather"->{n.inventory.merge("minecraft:bread",1,Integer::sum);state.transact("wilderness",n.id.toString(),"minecraft:bread",1,0,"foraged meal");}
            case "eat"->{if(n.inventory.getOrDefault("minecraft:bread",0)>0){n.inventory.merge("minecraft:bread",-1,Integer::sum);n.needs.put("hunger",Math.max(0,n.need("hunger")-.65));n.activity="dining";n.actionUntil=state.clock+400;}else n.plan.clear();}
            case "produce"->{n.activity="working";produce(n,work);n.actionUntil=state.clock+1200;}
            case "social"->{n.activity="socializing";n.needs.put("loneliness",Math.max(0,n.need("loneliness")-.3));List<Npc> company=s.residents.stream().map(state.npcs::get).filter(b->!b.id.equals(n.id)&&b.location.distance(n.location)<20).limit(4).toList();for(Npc friend:company){SocialRules.socialize(state,n,friend);if(SocialRules.acceptsCards(n,friend.id,state.clock,0)&&SocialRules.acceptsCards(friend,n.id,state.clock,0))abstractDuel(n,friend);}n.actionUntil=state.clock+800;}
            case "train"->{n.activity="training Pokémon";n.skills.merge("pokemon_training",.002,Double::sum);for(Partner p:n.pokemon)p.level=Math.min(100,12+(int)(n.skills.get("pokemon_training")*30));n.actionUntil=state.clock+600;}
        }
    }
    private void produce(Npc n,Building business){
        if(business==null)return;for(var r:data.recipes)if(r.profession().equals(n.profession)){
            if(!r.input().isBlank()&&business.stock.getOrDefault(r.input(),0)<r.consumed()){
                Settlement s=state.settlements.get(n.settlement);Building supplier=s.buildings.values().stream().filter(b->b.stock.getOrDefault(r.input(),0)>=r.consumed()).findFirst().orElse(null);
                if(supplier==null||routeBlocked(s.id)||business.money<r.consumed())continue;
                supplier.stock.merge(r.input(),-r.consumed(),Integer::sum);business.stock.merge(r.input(),r.consumed(),Integer::sum);business.money-=r.consumed();supplier.money+=r.consumed();state.transact(business.id,supplier.id,r.input(),r.consumed(),r.consumed(),"supply purchase");
            }
            if(!r.input().isBlank())business.stock.merge(r.input(),-r.consumed(),Integer::sum);business.stock.merge(r.output(),r.produced(),Integer::sum);
            long wage=Math.min(business.money,r.wage());business.money-=wage;n.money+=wage;n.skills.merge(data.professions.get(n.profession).skill(),.001,Double::sum);state.transact(business.id,n.id.toString(),r.output(),r.produced(),wage,"production and wage");
        }
    }
    private void abstractDuel(Npc a,Npc b){
        String key=a.id.compareTo(b.id)<0?a.id+":"+b.id:b.id+":"+a.id;if(!cardNights.add(key))return;
        List<String> deckA=List.copyOf(a.decks.get("casual")),deckB=List.copyOf(b.decks.get("casual"));long seed=state.clock^a.id.hashCode();double skillA=a.skills.getOrDefault("cards",.2),skillB=b.skills.getOrDefault("cards",.2);
        workers.submit(()->{try{int result=vn.svarcade.tcg.fabric.TcgMod.simulateLivingDuel(deckA,deckB,seed,skillA,skillB);server.execute(()->{
            cardNights.remove(key);if(active!=this||result<0||!deckA.equals(a.decks.get("casual"))||!deckB.equals(b.decks.get("casual")))return;
            Npc winner=result==0?a:b,loser=result==0?b:a;winner.skills.merge("cards",.005,Double::sum);loser.skills.merge("cards",.003,Double::sum);loser.relationship(winner.id).respect+=1;
            state.remember(loser,new Memory("lost_card_duel",winner.id,loser.settlement,state.clock,.5,-.1,1,"witnessed"));if(winner.skills.get("cards")>.7)winner.cardArchetype="tournament";
        });}catch(RuntimeException failure){server.execute(()->{cardNights.remove(key);LOG.warn("Card night deferred: {}",failure.getMessage());});}});
    }
    private void travel(Npc n,Settlement s,Building destination,String activity){
        if(destination==null)return;n.activity=activity;
        Pos target=activity.equals("going to work")?destination.point(Marker.WORKSTATION):activity.equals("returning home")?destination.point(Marker.DINING_POINT):destination.point(Marker.ENTRANCE);
        if(n.location.distance(target)<1.5)return;Travel t=new Travel();t.route=destination.contains(n.location)?new ArrayList<>(List.of(n.location,target)):routes.route(s,n.location,destination);if(t.route.getLast().distance(target)>.1)t.route.add(target);t.departure=state.clock;t.destination=destination.id;n.travel=t;
    }
    public void interrupt(Npc n,String event,UUID actor){if(n.interruptUntil>state.clock&&n.interrupt.equals(event))return;n.interrupt=event;n.interruptUntil=state.clock+400;n.plan.clear();n.travel=null;n.actionUntil=0;n.version++;state.remember(n,new Memory(event,actor,n.settlement,state.clock,event.equals("assault")?.95:.8,-.8,1,"witnessed"));n.relationship(actor==null?n.id:actor).fear+=8;enqueue(n,state.clock,0);urgentReactions++;}
    public void perceive(CitizenEntity entity){Npc n=state.npcs.get(entity.getUuid());if(n==null){entity.discard();return;}if(entity.isOnFire()){interrupt(n,"fire",null);return;}if(entity.age%20!=0)return;BlockPos p=entity.getBlockPos();if(world.getBlockState(p.down()).isOf(Blocks.FIRE)||world.getBlockState(p.east()).isOf(Blocks.FIRE))interrupt(n,"fire",null);}
    private void moveVisible(){
        for(var entry:new ArrayList<>(entities.entrySet())){
            Npc n=state.npcs.get(entry.getKey());CitizenEntity e=entry.getValue();if(e.isRemoved()){entities.remove(entry.getKey());continue;}
            n.location=new Pos(e.getX(),e.getY(),e.getZ());
            if(n.interactionUntil>state.clock&&n.interruptUntil<=state.clock){e.getNavigation().stop();if(n.travel!=null)n.travel.pausedTicks+=10;continue;}
            if(n.travel!=null){
                var route=n.travel.route;while(route.size()>1&&n.location.distance(route.get(1))<1.5)route.removeFirst();Pos target=route.size()>1?route.get(1):route.getFirst();
                if(route.size()==1&&n.location.distance(target)<2){n.travel=null;e.getNavigation().stop();enqueue(n,state.clock,2);}else if(e.getNavigation().isIdle())e.getNavigation().startMovingTo(target.x(),target.y(),target.z(),.65);
                // Rebase remaining route to the actual position for a seamless later dematerialization.
                if(route.size()>1){route.set(0,n.location);n.travel.departure=state.clock;n.travel.pausedTicks=0;}
            }
            var companion=partners.get(n.id);if(companion!=null&&companion.distanceTo(e)>4&&companion.getNavigation().isIdle())companion.getNavigation().startMovingTo(e,1);
            if(n.activity.equals("sleeping")&&!e.isSleeping()){Settlement homeSettlement=state.settlements.get(n.settlement);List<Pos> beds=homeSettlement.buildings.get(n.home).markers.get(Marker.BED);int bedIndex=homeSettlement.households.get(n.household).members.indexOf(n.id);Pos bed=beds.get(Math.floorMod(bedIndex,beds.size()));if(n.location.distance(bed)<5)e.sleep(BlockPos.ofFloored(bed.x(),bed.y(),bed.z()));}else if(!n.activity.equals("sleeping")&&e.isSleeping())e.wakeUp();
            String label=n.name+" · "+n.profession+" · "+n.activity;if(!e.getName().getString().equals(label))e.setCustomName(Text.literal(label));
            e.presentation(n.gender,n.appearance.hashCode());
            e.setVillagerData(e.getVillagerData().withProfession(switch(n.profession){case "farmer"->VillagerProfession.FARMER;case "blacksmith"->VillagerProfession.TOOLSMITH;case "baker","innkeeper"->VillagerProfession.BUTCHER;case "healer"->VillagerProfession.CLERIC;case "guard"->VillagerProfession.ARMORER;default->VillagerProfession.NONE;}));
        }
    }
    private void relevance(){
        List<net.minecraft.server.network.ServerPlayerEntity> players=world.getPlayers();nearbyCount=0;
        for(Npc n:state.npcs.values()){
            if(n.lifeStage.equals("deceased")){var dead=entities.remove(n.id);if(dead!=null)dead.discard();continue;}
            boolean relevant=players.stream().anyMatch(p->new Pos(p.getX(),p.getY(),p.getZ()).distance(n.location)<data.relevance)&&world.isChunkLoaded((int)n.location.x()>>4,(int)n.location.z()>>4);
            CitizenEntity e=entities.get(n.id);
            if(relevant){nearbyCount++;n.simulation="full";if(e==null){Entity old=world.getEntity(n.id);if(old instanceof CitizenEntity citizen)e=citizen;else{e=WorldComesAlive.CITIZEN.create(world);if(e==null)continue;if(n.travel!=null)n.location=n.travel.at(state.clock);e.setUuid(n.id);e.refreshPositionAndAngles(n.location.x(),n.location.y(),n.location.z(),0,0);e.setCustomName(Text.literal(n.name+" · "+n.profession));e.setCustomNameVisible(true);if(n.age<18)e.setBreedingAge(-24000);world.spawnEntity(e);state.materializations++;LOG.info("WCA_MATERIALIZE npc={} activity={} location={}",n.id,n.activity,n.location);}entities.put(n.id,e);}materializePartner(n,e);}
            else{n.simulation=world.isChunkLoaded((int)n.location.x()>>4,(int)n.location.z()>>4)?"reduced":"abstract";if(e!=null){n.location=new Pos(e.getX(),e.getY(),e.getZ());e.discard();entities.remove(n.id);var partner=partners.remove(n.id);if(partner!=null)partner.discard();state.dematerializations++;LOG.info("WCA_DEMATERIALIZE npc={} activity={} location={}",n.id,n.activity,n.location);}}
        }
    }
    private void materializePartner(Npc n,CitizenEntity owner){
        if(n.pokemon.isEmpty()||partners.containsKey(n.id)||n.activity.equals("sleeping"))return;
        Partner p=n.pokemon.getFirst();var existing=world.getEntity(p.id);if(existing instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity saved){partners.put(n.id,saved);return;}try{
            var properties=com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p.species+" level="+p.level);var pokemon=p.data.isBlank()?properties.create():new com.cobblemon.mod.common.pokemon.Pokemon();
            if(!p.data.isBlank())pokemon.loadFromJSON(world.getRegistryManager(),com.google.gson.JsonParser.parseString(p.data).getAsJsonObject());pokemon.setUuid(p.id);
            var e=new com.cobblemon.mod.common.entity.pokemon.PokemonEntity(world,pokemon,com.cobblemon.mod.common.CobblemonEntities.POKEMON);e.setUuid(p.id);e.refreshPositionAndAngles(owner.getX()+2,owner.getY(),owner.getZ(),0,0);e.setCustomName(Text.literal(n.name+"'s "+p.species+" · "+p.role));e.setCustomNameVisible(true);e.setPersistent();world.spawnEntity(e);partners.put(n.id,e);p.data=pokemon.saveToJSON(world.getRegistryManager(),new com.google.gson.JsonObject()).toString();LOG.info("WCA_POKEMON npc={} partner={} species={} role={}",n.id,p.id,p.species,p.role);
        }catch(Exception failure){LOG.warn("WCA_PARTNER_UNAVAILABLE species={} cause={}",p.species,failure.toString());}
    }
    private boolean routeBlocked(String settlement){return state.events.stream().anyMatch(e->e.settlement.equals(settlement)&&e.state.equals("active")&&e.type.equals("route_damage"));}
    private void events(){
        for(WorldEvent e:state.events)if(e.state.equals("active")&&state.clock>=e.ends){e.state="resolved";for(UUID id:e.witnesses){Npc n=state.npcs.get(id);if(n!=null){n.activeEvents.remove(e.id);state.remember(n,new Memory("event_resolved:"+e.type,null,e.settlement,state.clock,.7,.2,.9,"experienced"));}}LOG.info("WCA_EVENT_RESOLVED id={} type={}",e.id,e.type);}
        for(Settlement s:state.settlements.values())if(s.ready&&s.lastSocialDay<state.clock/24000){s.lastSocialDay=state.clock/24000;Random rng=new Random(s.seed^s.lastSocialDay);for(var def:data.events)if(rng.nextDouble()<def.chance()){WorldEvent e=new WorldEvent();e.id=s.id+"_"+def.type()+"_"+s.lastSocialDay;e.type=def.type();e.settlement=s.id;e.starts=state.clock;e.ends=state.clock+def.duration();e.witnesses.addAll(s.residents);state.events.add(e);for(UUID id:s.residents){Npc n=state.npcs.get(id);n.activeEvents.add(e.id);state.remember(n,new Memory("event:"+e.type,null,s.id,state.clock,.8,e.type.equals("festival")?.5:-.3,1,"experienced"));}}}
    }
    public Npc npc(UUID id){return state.npcs.get(id);}
    public Collection<CitizenEntity> visible(){return List.copyOf(entities.values());}
    public String status(){return "Settlements "+state.settlements.size()+" | Residents "+state.npcs.size()+" | Visible "+nearbyCount+" | Transactions "+state.transactions+" | Decisions "+state.decisions+" | AI " +lastMicros+" us | Max "+maxMicros+" us | Deferred "+deferred+" | Materializations "+state.materializations+" / dematerializations "+state.dematerializations;}
    public void save(){try{store.save(state);}catch(IOException failure){LOG.error("WCA_SAVE_FAILED: retaining in-memory state",failure);}}
    public void close(){workers.shutdownNow();save();for(var e:entities.values())e.discard();for(var e:partners.values())e.discard();active=null;LOG.info("WCA_SAVED settlements={} npcs={} transactions={} identities={}",state.settlements.size(),state.npcs.size(),state.transactions,state.npcs.keySet());}
}
