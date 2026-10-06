package vn.worldcomesalive.server;

import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.ai.Cognition;
import vn.worldcomesalive.ai.CognitiveQueue;
import vn.worldcomesalive.ai.NpcRoutineEngine;
import vn.worldcomesalive.ai.CognitiveQueue.Wake;
import vn.worldcomesalive.world.*;
import vn.worldcomesalive.WorldComesAlive;
import vn.worldcomesalive.generation.v2.*;
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
    public final vn.worldcomesalive.domestic.DomesticManager domestic;
    public final vn.worldcomesalive.agriculture.AgriculturalRuntime agriculture;
    public final vn.worldcomesalive.civilization.LodgingManager lodging;
    public final DungeonRuntime dungeons;
    public final vn.worldcomesalive.civilization.CivilizationManager civilization;
    private final WorldStore store;
    private final RoadRoutes routes=new RoadRoutes();
    private final Map<UUID,CitizenEntity> entities=new HashMap<>();
    private final Map<UUID,com.cobblemon.mod.common.entity.pokemon.PokemonEntity> partners=new HashMap<>();
    private final ArrayDeque<CitizenEntity> unloadedCitizens=new ArrayDeque<>();
    private final CognitiveQueue wakes=new CognitiveQueue();
    private final ArrayDeque<SettlementStructures.Placement> placements=new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> discoveries=new ArrayDeque<>();
    private String buildingSettlement="";
    private TerrainCapture terrainCapture;private TerrainSnapshot generationTerrain;private java.util.concurrent.CompletableFuture<SettlementPlan> generationFuture;private SettlementProgram generationProgram;private GenerationCatalog generationCatalog;private Spatial.Point generationCenter;private UUID pendingFounder;
    private final Set<Building> furnishing=new HashSet<>();
    public long lastMicros,maxMicros,deferred,nearbyCount,urgentReactions;
    private long nextCheckpoint;
    private final java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"wca-pure-planning");t.setDaemon(true);return t;});
    private final Set<String> cardNights=new HashSet<>();
    public WorldSimulation(MinecraftServer server,WorldContent data)throws IOException{
        this.server=server;this.world=server.getOverworld();this.data=data;store=new WorldStore(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("world-comes-alive"));state=store.load();state.clock=world.getTime();active=this;domestic=new vn.worldcomesalive.domestic.DomesticManager(this);agriculture=new vn.worldcomesalive.agriculture.AgriculturalRuntime(this);lodging=new vn.worldcomesalive.civilization.LodgingManager(this);civilization=new vn.worldcomesalive.civilization.CivilizationManager(this);dungeons=new DungeonRuntime(this);
        // Historical V1 settlements keep their state and geometry; no automatic spatial conversion.
        for(Settlement s:state.settlements.values())if(s.ready){lodging.initialize(s);domestic.initialize(s);}
        for(Npc n:state.npcs.values())enqueue(n,Math.max(state.clock,n.nextCognition),3);
        for(Settlement s:state.settlements.values())if(!s.ready&&s.generationVersion>=2){beginTerrain(s.center,s.generationPlan.program);break;}
        LOG.info("WCA_LOADED settlements={} npcs={} revision={} identities={}",state.settlements.size(),state.npcs.size(),state.revision,state.npcs.keySet());
    }
    public void reload(WorldContent content){data=content;routes.invalidate();for(Npc n:state.npcs.values()){n.plan.clear();var profession=data.professions.get(n.profession);if(profession!=null){n.schedule.put("work_start",profession.start());n.schedule.put("work_end",profession.end());}n.version++;enqueue(n,state.clock,3);}LOG.info("WCA_DATA_RELOADED professions={} dialogue={} archetypes={}",data.professions.size(),data.dialogue.size(),data.archetypes.size());}
    public static long siteSeed(long seed,int rx,int rz){return seed^(rx*341873128712L)^(rz*132897987541L)^0x51C0A113;}
    public static ChunkPos candidate(long seed,int rx,int rz,int cell){Random r=new Random(siteSeed(seed,rx,rz));return new ChunkPos(rx*cell+cell/2+r.nextInt(5)-2,rz*cell+cell/2+r.nextInt(5)-2);}
    public ChunkPos nearestCandidate(BlockPos pos){int rx=Math.floorDiv(pos.getX()>>4,data.regionChunks),rz=Math.floorDiv(pos.getZ()>>4,data.regionChunks);ChunkPos best=null;double distance=Double.MAX_VALUE;for(int x=rx-1;x<=rx+1;x++)for(int z=rz-1;z<=rz+1;z++){ChunkPos c=candidate(world.getSeed(),x,z,data.regionChunks);double d=c.getCenterAtY(0).getSquaredDistance(pos.getX(),0,pos.getZ());if(d<distance){distance=d;best=c;}}return best;}
    public void discover(ChunkPos pos){ChunkPos candidate=candidate(world.getSeed(),Math.floorDiv(pos.x,data.regionChunks),Math.floorDiv(pos.z,data.regionChunks),data.regionChunks);if(candidate.equals(pos)&&!state.surveyed.contains(pos.toString()))discoveries.add(pos);}
    private void generate(ChunkPos pos){
        String survey=pos.toString();if(!state.surveyed.add(survey))return;
        int x=pos.getCenterX(),z=pos.getCenterZ();if(!world.isChunkLoaded(pos.x,pos.z)){state.surveyed.remove(survey);discoveries.addLast(pos);return;}int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        if(y<=world.getSeaLevel()+1||y>180)return;
        // Detailed suitability is evaluated against the completed immutable terrain snapshot. Never synchronously load neighboring chunks here.
        String biome=world.getBiome(new BlockPos(x,y,z)).getKey().map(k->k.getValue().toString()).orElse("plains");if(biome.contains("ocean")||biome.contains("river"))return;
        long seed=siteSeed(world.getSeed(),Math.floorDiv(pos.x,data.regionChunks),Math.floorDiv(pos.z,data.regionChunks));
        String archetype=Math.floorMod(seed,3)==0?"market_town":"farming_village";
        beginTerrain(new Pos(x,y,z),SettlementProgram.create(seed,archetype,data.region(biome).id(),GenerationCatalog.active));
    }
    public boolean tryFoundSettlement(net.minecraft.server.network.ServerPlayerEntity player,BlockPos pos){
        if(generationPending()){player.sendMessage(Text.literal("A settlement survey is already being processed."),true);return false;}
        Pos here=new Pos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        Settlement nearby=state.settlements.values().stream().filter(v->v.ready&&v.center.distance(here)<480).findFirst().orElse(null);
        if(nearby!=null){player.sendMessage(Text.literal("This land is already within the sphere of "+nearby.name+"."),true);return false;}
        var life=state.players.computeIfAbsent(player.getUuid(),id->new PlayerLife());
        if(!life.foundedSettlement.isBlank()){player.sendMessage(Text.literal("You already founded "+life.foundedSettlement+"."),true);return false;}
        var plow=vn.worldcomesalive.civilization.SettlementFounding.nearbyPlowPokemon(player);
        if(plow==null){player.sendMessage(Text.literal("Bring your Pokemon with an attached settlement plow nearby."),true);return false;}
        int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())-1;
        if(y<=world.getSeaLevel()+1){player.sendMessage(Text.literal("This ground is unsuitable for a settlement."),true);return false;}
        String biome=world.getBiome(new BlockPos(pos.getX(),y,pos.getZ())).getKey().map(k->k.getValue().toString()).orElse("plains");
        long seed=siteSeed(world.getSeed(),pos.getX()>>4,pos.getZ()>>4)^player.getUuid().getMostSignificantBits();
        pendingFounder=player.getUuid();
        beginTerrain(new Pos(pos.getX(),y,pos.getZ()),SettlementProgram.create(seed,"farming_village",data.region(biome).id(),GenerationCatalog.active));
        player.sendMessage(Text.literal("Settlement survey started. The WCA planner will lay out roads, lots, farms and services from this site."),false);
        return true;
    }
    private void beginTerrain(Pos center,SettlementProgram program){generationCatalog=GenerationCatalog.active;generationProgram=program;generationCenter=new Spatial.Point(center.x(),center.z());terrainCapture=new TerrainCapture(world,(int)center.x(),(int)center.z());LOG.info("WCA_V2_TERRAIN_START seed={} archetype={} population={} foodCapacity={} center={}",program.seed(),program.archetype(),program.populationTarget(),program.foodCapacity(),center);}
    private void generationTick(){
        if(terrainCapture==null)return;
        try{
            if(generationFuture==null){if(!terrainCapture.tick())return;generationTerrain=terrainCapture.snapshot();var terrain=generationTerrain;var program=generationProgram;var center=generationCenter;var catalog=generationCatalog;generationFuture=java.util.concurrent.CompletableFuture.supplyAsync(()->SettlementPlanner.plan(program,center,terrain,catalog),workers);return;}
            if(!generationFuture.isDone())return;
            SettlementPlan plan=generationFuture.join();if(generationCatalog!=GenerationCatalog.active)throw new IllegalArgumentException("Generation catalog changed during planning; candidate will be resurveyed");
            Settlement existing=state.settlements.values().stream().filter(s->s.seed==plan.program.seed()).findFirst().orElse(null);Settlement s=existing!=null?existing:SemanticRegistration.create(plan,data);if(existing==null)state.settlements.put(s.id,s);
            s.roads.clear();for(var r:plan.roads)for(var point:r.points())s.roads.add(new Pos(Math.round(point.x())+.5,generationTerrain.heightAt(point.x(),point.z())+1,Math.round(point.z())+.5));
            SemanticRegistration.agriculture(s,generationTerrain);new BlockMaterializer(s,generationTerrain,generationCatalog,state,data).materialize(placements);buildingSettlement=s.id;
            for(Settlement other:state.settlements.values())if(!other.id.equals(s.id)&&other.center.distance(s.center)<1500){s.routes.add(other.id);other.routes.add(s.id);}
            generationFuture=null;LOG.info("WCA_V2_ACCEPTED id={} buildings={} fields={} quality={} placements={} manualSetup=false",s.id,s.buildings.size(),s.fields.size(),plan.quality,placements.size());
        }catch(java.util.concurrent.CompletionException rejected){Throwable cause=rejected.getCause()==null?rejected:rejected.getCause();if(cause instanceof PlanningRejectedException)LOG.info("WCA_V2_SITE_SKIPPED seed={} reason={}",generationProgram.seed(),cause.getMessage());else LOG.error("WCA_V2_PLANNER_FAILURE seed={}",generationProgram.seed(),cause);generationFuture=null;var failed=state.settlements.values().stream().filter(s->!s.ready&&s.seed==generationProgram.seed()).findFirst().orElse(null);if(failed!=null){state.settlements.remove(failed.id);state.rooms.values().removeIf(r->r.settlement.equals(failed.id));}placements.clear();buildingSettlement="";terrainCapture.close();terrainCapture=null;generationTerrain=null;}
        catch(PlanningRejectedException rejected){LOG.info("WCA_V2_SITE_SKIPPED seed={} reason={}",generationProgram.seed(),rejected.getMessage());generationFuture=null;var failed=state.settlements.values().stream().filter(s->!s.ready&&s.seed==generationProgram.seed()).findFirst().orElse(null);if(failed!=null){state.settlements.remove(failed.id);state.rooms.values().removeIf(r->r.settlement.equals(failed.id));}placements.clear();buildingSettlement="";terrainCapture.close();terrainCapture=null;generationTerrain=null;}
    }
    public boolean generationPending(){return terrainCapture!=null||!buildingSettlement.isBlank();}
    public void tick(){
        long start=System.nanoTime();state.clock=world.getTime();
        if(buildingSettlement.isBlank())generationTick();
        while(!unloadedCitizens.isEmpty())citizenUnloaded(unloadedCitizens.removeFirst());
        for(int i=0;i<512&&!placements.isEmpty();i++){var p=placements.remove();world.setBlockState(p.pos(),p.state(),2);}
        if(placements.isEmpty()&&!buildingSettlement.isBlank()){
            Settlement s=state.settlements.get(buildingSettlement);for(Building b:s.buildings.values())for(Pos bed:b.markers.getOrDefault(Marker.BED,List.of()))if(!(world.getBlockState(BlockPos.ofFloored(bed.x(),bed.y(),bed.z())).getBlock() instanceof net.minecraft.block.BedBlock))throw new IllegalStateException("Physical semantic bed missing before bootstrap: "+b.id+" "+bed);world.getChunkManager().save(true);s.buildings.values().forEach(b->{b.built=true;b.furnitureVersion=s.generationVersion>=2?20:vn.worldcomesalive.furniture.FurnitureLayout.VERSION;b.lodgingVersion=s.generationVersion>=2?2:1;});SettlementBootstrap.populate(state,s,data,vn.svarcade.tcg.fabric.TcgMod.livingWorldDeck());for(var building:s.buildings.values())if(building.sign!=null&&world.getBlockEntity(BlockPos.ofFloored(building.sign.x(),building.sign.y(),building.sign.z())) instanceof net.minecraft.block.entity.SignBlockEntity sign){sign.setText(sign.getFrontText().withMessage(0,Text.literal(building.type.replace("_"," "))).withMessage(1,Text.literal(s.name)),true);sign.markDirty();}for(UUID id:s.residents)enqueue(state.npcs.get(id),state.clock+Math.floorMod(id.hashCode(),200),3);domestic.initialize(s);agriculture.register(s);civilization.initialize(s);if(pendingFounder!=null){var life=state.players.computeIfAbsent(pendingFounder,id->new PlayerLife());life.foundedSettlement=s.id;var government=state.civilization.governments.get(s.id);if(government!=null){government.type="PLAYER_GOVERNED";government.playerLeader=pendingFounder;}s.archetype="player_founded";s.faction="player:"+pendingFounder;pendingFounder=null;}buildingSettlement="";if(terrainCapture!=null){terrainCapture.close();terrainCapture=null;generationTerrain=null;}save();LOG.info("WCA_BOOTSTRAP_READY id={} households={} residents={} beds={} manualSetup=false",s.id,s.households.size(),s.residents.size(),s.buildings.values().stream().mapToInt(b->b.beds).sum());
        }
        if(placements.isEmpty()&&!furnishing.isEmpty()){furnishing.forEach(b->{b.furnitureVersion=vn.worldcomesalive.furniture.FurnitureLayout.VERSION;b.lodgingVersion=1;});furnishing.clear();save();}
        if(terrainCapture==null&&placements.isEmpty()&&!discoveries.isEmpty())generate(discoveries.remove());
        int processed=0;long budget=System.nanoTime()+2_000_000;
        while(wakes.hasReady(state.clock)&&processed<64){
            if(System.nanoTime()>budget){deferred++;break;}
            Wake wake=wakes.remove();Npc n=state.npcs.get(wake.npc());if(n==null||wake.version()!=n.cognitionVersion)continue;
            think(n);processed++;state.decisions++;
        }
        domestic.tick();agriculture.tick();lodging.tick();civilization.tick();dungeons.tick();
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
        if(vn.svarcade.tcg.fabric.TcgMod.livingNpcBusy(n.id)){enqueue(n,state.clock+100,1);return;}
        if(n.interactionUntil>state.clock&&n.interruptUntil<=state.clock){enqueue(n,n.interactionUntil,1);return;}
        if(n.travel!=null){if(!entities.containsKey(n.id)){n.location=n.travel.at(state.clock);if(n.travel.arrived(state.clock)){n.location=n.travel.route.getLast();n.travel=null;}}if(n.travel!=null){enqueue(n,state.clock+100,3);return;}}
        if(n.actionUntil>state.clock&&n.interruptUntil<=state.clock){enqueue(n,n.actionUntil,3);return;}
        if(n.plan.isEmpty()){
            Building market=s.service("bakery");Household h=s.households.get(n.household);if(h!=null&&h.supplies.values().stream().anyMatch(v->v>0))n.inventory.put("pantry_available",1);else n.inventory.remove("pantry_available");
            var routine=NpcRoutineEngine.choose(n,s,state.clock);
            if(routine.isPresent()){
                var choice=routine.get();n.goal=choice.reason();execute(n,s,choice.execution());if(n.actionUntil<=state.clock&&n.travel==null)n.actionUntil=state.clock+choice.duration();
            }else{
                var d=Cognition.decide(n,state.clock,data,market!=null&&market.stock.getOrDefault("minecraft:bread",0)>0);n.goal=d.goal();n.plan=new ArrayList<>(d.plan());
            }
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
            case "work"->{var field=n.profession.equals("farmer")?vn.worldcomesalive.agriculture.Agriculture.workPlot(s,n):null;if(field!=null){Pos target=field.entrance==null?new Pos(field.origin.x()+field.width/2.0,field.origin.y()+1,field.origin.z()+field.depth/2.0):field.entrance;Travel t=new Travel();t.departure=state.clock;t.destination=field.id.toString();t.route=s.generationVersion>=2?vn.worldcomesalive.generation.v2.GraphNavigation.routeTo(s,n.location,target):new ArrayList<>(List.of(n.location,s.buildings.get(n.workplace).point(Marker.ENTRANCE),target));n.travel=t;n.activity="going to field";}else travel(n,s,work==null?home:work,"going to work");}
            case "tavern"->travel(n,s,tavern==null?home:tavern,"visiting tavern");
            case "market"->travel(n,s,market==null?home:market,"shopping");
            case "guard"->{travel(n,s,guard==null?home:guard,"seeking safety");n.emotion="alarmed";n.plan.clear();}
            case "sleep"->{if(n.location.distance(seatPoint(n,home,Marker.BED))>2){travel(n,s,home,"going to bed");repeat(n,"sleep");break;}n.activity="sleeping";n.needs.put("fatigue",Math.max(0,n.need("fatigue")-.35));n.actionUntil=state.clock+1800;}
            case "buy"->{if(market!=null&&n.money>=3&&market.stock.getOrDefault("minecraft:bread",0)>0){market.stock.merge("minecraft:bread",-1,Integer::sum);market.money+=3;n.money-=3;n.inventory.merge("minecraft:bread",1,Integer::sum);state.transact(n.id.toString(),market.id,"minecraft:bread",1,3,"food purchase");}else n.plan.clear();}
            case "gather"->{n.inventory.merge("minecraft:bread",1,Integer::sum);state.transact("wilderness",n.id.toString(),"minecraft:bread",1,0,"foraged meal");}
            case "eat"->{if(n.location.distance(seatPoint(n,home,Marker.DINING_POINT))>2){travel(n,s,home,"going to meal");repeat(n,"eat");break;}if(domestic.homeMeal(n,home)){n.activity="dining";n.actionUntil=state.clock+420;}else n.plan.clear();}
            case "store"->{var h=s.households.get(n.household);for(String id:new ArrayList<>(n.inventory.keySet()))if(id.contains("pickaxe")||id.contains("axe")||id.contains("hoe")){int count=n.inventory.remove(id);h.equipment.merge(id,count,Integer::sum);}n.activity="storing work equipment";n.actionUntil=state.clock+100;}
            case "family_meal"->{if(domestic.homeMeal(n,home)){n.activity="dining";n.actionUntil=state.clock+420;}}
            case "family_social"->{for(UUID id:s.households.get(n.household).members)if(!id.equals(n.id)){Npc relative=npc(id);if(relative.location.distance(n.location)<15)SocialRules.socialize(state,n,relative);}n.activity="family conversation";n.actionUntil=state.clock+200;}
            case "read"->{n.activity="reading";n.skills.merge("speech",.001,Double::sum);n.lastFamilyDay=state.clock/24000;n.actionUntil=state.clock+400;}
            case "produce"->{n.activity="working";produce(n,work);n.actionUntil=state.clock+1200;}
            case "social"->{n.activity="socializing";domestic.tavernVisit(n,s,tavern);n.needs.put("loneliness",Math.max(0,n.need("loneliness")-.3));List<Npc> company=s.residents.stream().map(state.npcs::get).filter(b->!b.id.equals(n.id)&&b.location.distance(n.location)<20).limit(4).toList();for(Npc friend:company){SocialRules.socialize(state,n,friend);if(SocialRules.acceptsCards(n,friend.id,state.clock,0)&&SocialRules.acceptsCards(friend,n.id,state.clock,0))abstractDuel(n,friend);}n.actionUntil=state.clock+800;}
            case "train"->{n.activity="training Pokémon";n.skills.merge("pokemon_training",.002,Double::sum);for(Partner p:n.pokemon)p.level=Math.min(100,12+(int)(n.skills.get("pokemon_training")*30));n.actionUntil=state.clock+600;}
            case "home_meal"->{if(n.location.distance(seatPoint(n,home,Marker.DINING_POINT))>2){travel(n,s,home,"going to meal");break;}if(domestic.homeMeal(n,home)){n.activity="dining at home";n.needs.put("hunger",Math.max(0,n.need("hunger")-.55));n.actionUntil=state.clock+420;}else{n.activity="looking for food";travel(n,s,market==null?home:market,"shopping");}}
            case "home_leisure"->{if(!home.contains(n.location)){travel(n,s,home,"returning home");break;}n.activity=n.interests.contains("pokemon")&&!n.pokemon.isEmpty()?"caring for Pokémon":"at home";n.needs.put("loneliness",Math.max(0,n.need("loneliness")-.05));n.actionUntil=state.clock+420;}
            case "visit_neighbor"->{Building target=neighborHome(n,s);if(target==null){travel(n,s,home,"returning home");break;}travel(n,s,target,"visiting neighbors");}
            case "community"->{Building target=communityPlace(s,n);travel(n,s,target==null?home:target,"community activity");}
            case "patrol"->{Building next=patrolTarget(n,s);travel(n,s,next==null?home:next,"patrolling");n.skills.merge("combat",.001,Double::sum);}
        }
    }
    private void produce(Npc n,Building business){
        if(business==null)return;if(n.profession.equals("farmer")){agriculture.work(n,state.settlements.get(n.settlement));return;}domestic.work(state.settlements.get(n.settlement),business,n.profession,n.skills.getOrDefault("cooking",.2));for(var r:data.recipes)if(r.profession().equals(n.profession)){
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
        if(vn.svarcade.tcg.fabric.TcgMod.livingNpcBusy(a.id)||vn.svarcade.tcg.fabric.TcgMod.livingNpcBusy(b.id))return;long versionA=a.version,versionB=b.version;
        String key=a.id.compareTo(b.id)<0?a.id+":"+b.id:b.id+":"+a.id;if(!cardNights.add(key))return;
        List<String> deckA=List.copyOf(a.decks.get("casual")),deckB=List.copyOf(b.decks.get("casual"));long seed=state.clock^a.id.hashCode();double skillA=domestic.cardSkill(a),skillB=domestic.cardSkill(b);
        workers.submit(()->{try{int result=vn.svarcade.tcg.fabric.TcgMod.simulateLivingDuel(deckA,deckB,seed,skillA,skillB);server.execute(()->{
            cardNights.remove(key);if(active!=this||result<0||a.version!=versionA||b.version!=versionB||!deckA.equals(a.decks.get("casual"))||!deckB.equals(b.decks.get("casual")))return;
            Npc winner=result==0?a:b,loser=result==0?b:a;winner.skills.merge("cards",.005,Double::sum);loser.skills.merge("cards",.003,Double::sum);loser.relationship(winner.id).respect+=1;
            state.remember(loser,new Memory("lost_card_duel",winner.id,loser.settlement,state.clock,.5,-.1,1,"witnessed"));if(winner.skills.get("cards")>.7)winner.cardArchetype="tournament";
        });}catch(RuntimeException failure){server.execute(()->{cardNights.remove(key);LOG.warn("Card night deferred: {}",failure.getMessage());});}});
    }
    private Building neighborHome(Npc n,Settlement s){
        return s.households.values().stream().filter(h->!h.id.equals(n.household)).map(h->s.buildings.get(h.home)).filter(Objects::nonNull)
            .min(Comparator.comparingDouble(b->b.point(Marker.ENTRANCE).distance(n.location)+Math.floorMod((b.id+n.id).hashCode(),9))).orElse(null);
    }
    private Building communityPlace(Settlement s,Npc n){
        List<String> preferred=n.roles.contains("Leader")?List.of("civic","market","tavern"):List.of("market","civic","tavern");
        for(String type:preferred){Building b=s.service(type);if(b!=null)return b;}
        return s.buildings.values().stream().filter(b->b.type.equals("public_space")).findFirst().orElse(null);
    }
    private Building patrolTarget(Npc n,Settlement s){
        List<Building> points=s.buildings.values().stream().filter(b->Set.of("guardhouse","trading_post","market","public_space","civic").contains(b.type)).toList();
        if(points.isEmpty())return s.buildings.get(n.workplace);
        return points.get(Math.floorMod((int)(state.clock/600)+n.id.hashCode(),points.size()));
    }
    private void repeat(Npc n,String execution){data.actions.stream().filter(a->a.execution().equals(execution)).findFirst().ifPresent(a->n.plan.addFirst(a.id()));}
    private Pos seatPoint(Npc n,Building b,Marker marker){
        List<Pos> points=b.activityAccess.getOrDefault(marker,b.markers.getOrDefault(marker,List.of(b.origin)));if(b.visitors.containsKey(n.id)&&marker!=Marker.BED)return b.visitors.get(n.id);
        var household=state.settlements.get(n.settlement).households.get(n.household);
        int index=b.id.equals(n.home)?household.members.indexOf(n.id):n.id.hashCode();
        return points.get(Math.floorMod(index,points.size()));
    }
    private void travel(Npc n,Settlement s,Building destination,String activity){
        if(destination==null)return;
        if(activity.equals("visiting tavern")){Pos seat=vn.worldcomesalive.furniture.VenueCapacity.reserve(destination,n);if(seat==null){destination=s.buildings.get(n.home);activity="returning home";}}
        else vn.worldcomesalive.furniture.VenueCapacity.release(s,n.id);
        n.activity=activity;
        Pos target=activity.equals("going to work")?seatPoint(n,destination,Marker.WORKSTATION):activity.equals("going to bed")?seatPoint(n,destination,Marker.BED):activity.equals("returning home")||activity.equals("going to meal")?seatPoint(n,destination,Marker.DINING_POINT):activity.equals("visiting tavern")?seatPoint(n,destination,Marker.SOCIAL_POINT):destination.point(Marker.ENTRANCE);
        if(n.location.distance(target)<1.5)return;Travel t=new Travel();t.route=destination.contains(n.location)?vn.worldcomesalive.generation.v2.LocalNavigation.route(destination,n.location,target):routes.route(s,n.location,destination);if(t.route.getLast().distance(target)>.1){var lastMile=vn.worldcomesalive.generation.v2.LocalNavigation.route(destination,t.route.getLast(),target);t.route.addAll(lastMile.subList(1,lastMile.size()));}t.departure=state.clock;t.destination=destination.id;n.travel=t;
    }
    public void interrupt(Npc n,String event,UUID actor){vn.worldcomesalive.furniture.VenueCapacity.release(state.settlements.get(n.settlement),n.id);if(n.interruptUntil>state.clock&&n.interrupt.equals(event))return;n.interrupt=event;n.interruptUntil=state.clock+400;n.plan.clear();n.travel=null;n.actionUntil=0;n.version++;state.remember(n,new Memory(event,actor,n.settlement,state.clock,event.equals("assault")?.95:.8,-.8,1,"witnessed"));if(event.equals("assault")&&actor!=null)vn.worldcomesalive.civilization.BountySystem.report(state,n.settlement,actor,n.id,"ASSAULT",.65,n.id,n.location);n.relationship(actor==null?n.id:actor).fear+=8;enqueue(n,state.clock,0);urgentReactions++;}
    public void perceive(CitizenEntity entity){Npc n=state.npcs.get(entity.getUuid());if(n==null){entity.discard();return;}if(entity.isOnFire()){interrupt(n,"fire",null);return;}if(entity.age%20!=0)return;BlockPos p=entity.getBlockPos();if(world.getBlockState(p.down()).isOf(Blocks.FIRE)||world.getBlockState(p.east()).isOf(Blocks.FIRE))interrupt(n,"fire",null);}
    private void moveVisible(){
        for(var entry:new ArrayList<>(entities.entrySet())){
            Npc n=state.npcs.get(entry.getKey());CitizenEntity e=entry.getValue();if(e.isRemoved()){citizenUnloaded(e);continue;}
            boolean sitting=n.travel==null&&Set.of("dining","drinking","socializing").contains(n.activity)&&n.interruptUntil<=state.clock;
            if(e.hasVehicle()&&!sitting){Entity seat=e.getVehicle();e.stopRiding();if(seat instanceof vn.worldcomesalive.furniture.SeatEntity)seat.discard();}
            if(sitting&&!e.hasVehicle()){
                Settlement s=state.settlements.get(n.settlement);var meal=domestic.meal(n.id);Building venue=meal!=null?s.buildings.get(meal.building):s.buildings.values().stream().filter(b->b.contains(n.location)).findFirst().orElse(s.buildings.get(n.home));
                if(venue!=null){Pos point=seatPoint(n,venue,Marker.DINING_POINT);if(new Pos(e.getX(),e.getY(),e.getZ()).distance(point)<2.5&&vn.worldcomesalive.furniture.FurnitureRegistry.sit(e,world,BlockPos.ofFloored(point.x(),point.y(),point.z())))e.getNavigation().stop();}
            }
            n.location=new Pos(e.getX(),e.getY(),e.getZ());
            if(n.travel!=null){
                Pos nearest=n.travel.route.stream().min(Comparator.comparingDouble(p->Math.hypot(p.x()-e.getX(),p.z()-e.getZ()))).orElse(null);
                boolean fluid=!world.getFluidState(e.getBlockPos()).isEmpty();
                boolean dropped=nearest!=null&&e.getY()<nearest.y()-4;
                if(fluid||dropped){
                    Pos recovery=n.travel.route.stream()
                        .sorted(Comparator.comparingDouble(p->Math.hypot(p.x()-e.getX(),p.z()-e.getZ())))
                        .filter(p->safeFor(e,p))
                        .findFirst().orElse(null);
                    if(recovery!=null){
                        e.getNavigation().stop();
                        e.refreshPositionAndAngles(recovery.x(),recovery.y(),recovery.z(),e.getYaw(),e.getPitch());
                        n.location=recovery;
                        if(!n.travel.route.isEmpty())n.travel.route.set(0,recovery);
                        n.travel.departure=state.clock;
                        n.travel.pausedTicks=0;
                        LOG.warn("WCA_NAV_RECOVER npc={} activity={} fluid={} dropped={} recovery={}",n.id,n.activity,fluid,dropped,recovery);
                    }
                }
            }
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
    /** Native chunk unloading is the same full-to-logical transition as leaving relevance. */
    public void queueCitizenUnload(CitizenEntity entity){unloadedCitizens.addLast(entity);}
    public void citizenUnloaded(CitizenEntity entity){
        if(entities.remove(entity.getUuid(),entity)){
            Npc n=npc(entity.getUuid());if(n==null)return;
            n.location=new Pos(entity.getX(),entity.getY(),entity.getZ());preserveLocalPath(n,entity);n.simulation="abstract";
            var partner=partners.remove(n.id);if(partner!=null)partner.discard();
            state.dematerializations++;
            LOG.info("WCA_DEMATERIALIZE npc={} activity={} location={} reason=chunk_unload",n.id,n.activity,n.location);
        }
    }
    public void citizenLoaded(CitizenEntity entity){
        Npc n=npc(entity.getUuid());if(n==null){entity.discard();return;}
        entity.presentation(n.gender,n.appearance.hashCode());
    }
    private void preserveLocalPath(Npc npc,CitizenEntity entity){var path=entity.getNavigation().getCurrentPath();if(npc.travel==null||path==null||path.isFinished())return;var remaining=new ArrayList<Pos>();remaining.add(npc.location);for(int i=path.getCurrentNodeIndex();i<path.getLength();i++){var node=path.getNode(i);remaining.add(new Pos(node.x+.5,node.y,node.z+.5));}if(npc.travel.route.size()>1)remaining.addAll(npc.travel.route.subList(1,npc.travel.route.size()));npc.travel.route=remaining;npc.travel.departure=state.clock;npc.travel.pausedTicks=0;}
    /** Materialize at the route position only when the native collision volume is safe; retry after travel progresses. */
    private boolean safeMaterialization(Npc npc,CitizenEntity entity){Pos logical=npc.travel==null?npc.location:npc.travel.at(state.clock);double floor=logical.y();var cell=BlockPos.ofFloored(logical.x(),floor,logical.z());if(world.getBlockState(cell).getBlock() instanceof net.minecraft.block.BedBlock)floor=cell.getY()+.5625;entity.refreshPositionAndAngles(logical.x(),floor,logical.z(),0,0);if(!world.isSpaceEmpty(entity)||!world.getFluidState(entity.getBlockPos()).isEmpty())return false;npc.location=new Pos(logical.x(),floor,logical.z());return true;}
    private void relevance(){
        List<net.minecraft.server.network.ServerPlayerEntity> players=world.getPlayers();nearbyCount=0;
        for(Npc n:state.npcs.values()){
            if(n.lifeStage.equals("deceased")){var dead=entities.remove(n.id);if(dead!=null)dead.discard();continue;}
            boolean relevant=players.stream().anyMatch(p->new Pos(p.getX(),p.getY(),p.getZ()).distance(n.location)<data.relevance)&&world.isChunkLoaded((int)n.location.x()>>4,(int)n.location.z()>>4);
            CitizenEntity e=entities.get(n.id);
            if(relevant){nearbyCount++;n.simulation="full";if(e==null){Entity old=world.getEntity(n.id);if(old instanceof CitizenEntity citizen&&!citizen.isRemoved()){e=citizen;if(n.travel!=null)n.location=n.travel.at(state.clock);e.refreshPositionAndAngles(n.location.x(),n.location.y(),n.location.z(),e.getYaw(),e.getPitch());e.presentation(n.gender,n.appearance.hashCode());state.materializations++;LOG.info("WCA_MATERIALIZE npc={} activity={} location={} reason=chunk_restore",n.id,n.activity,n.location);}else{e=WorldComesAlive.CITIZEN.create(world);if(e==null)continue;if(n.travel!=null)n.location=n.travel.at(state.clock);e.setUuid(n.id);e.presentation(n.gender,n.appearance.hashCode());if(!safeMaterialization(n,e)){n.simulation="reduced";continue;}e.setCustomName(Text.literal(n.name+" · "+n.profession));e.setCustomNameVisible(false);if(n.age<18)e.setBreedingAge(-24000);world.spawnEntity(e);state.materializations++;LOG.info("WCA_MATERIALIZE npc={} activity={} location={}",n.id,n.activity,n.location);}entities.put(n.id,e);}materializePartner(n,e);}
            else{n.simulation=world.isChunkLoaded((int)n.location.x()>>4,(int)n.location.z()>>4)?"reduced":"abstract";if(e!=null){n.location=new Pos(e.getX(),e.getY(),e.getZ());preserveLocalPath(n,e);entities.remove(n.id);e.discard();var partner=partners.remove(n.id);if(partner!=null)partner.discard();state.dematerializations++;LOG.info("WCA_DEMATERIALIZE npc={} activity={} location={}",n.id,n.activity,n.location);}}
        }
    }
    private void materializePartner(Npc n,CitizenEntity owner){
        if(partners.get(n.id)!=null&&partners.get(n.id).isRemoved())partners.remove(n.id);if(n.pokemon.isEmpty()||partners.containsKey(n.id)||n.activity.equals("sleeping"))return;
        Partner p=n.pokemon.getFirst();var existing=world.getEntity(p.id);if(existing instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity saved){partners.put(n.id,saved);return;}try{
            var properties=com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p.species+" level="+p.level);var pokemon=p.data.isBlank()?properties.create():new com.cobblemon.mod.common.pokemon.Pokemon();
            if(!p.data.isBlank())pokemon.loadFromJSON(world.getRegistryManager(),com.google.gson.JsonParser.parseString(p.data).getAsJsonObject());pokemon.setUuid(p.id);
            var e=new com.cobblemon.mod.common.entity.pokemon.PokemonEntity(world,pokemon,com.cobblemon.mod.common.CobblemonEntities.POKEMON);e.setUuid(p.id);
            Pos spawn=null;
            for(int radius=1;radius<=4&&spawn==null;radius++)for(int dx=-radius;dx<=radius&&spawn==null;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                for(int dy:new int[]{0,1,-1,2}){
                    Pos candidate=new Pos(owner.getX()+dx+.5,owner.getY()+dy,owner.getZ()+dz+.5);
                    if(safeFor(e,candidate)){spawn=candidate;break;}
                }
                if(spawn!=null)break;
            }
            if(spawn==null){LOG.warn("WCA_PARTNER_DEFERRED npc={} partner={} species={} cause=no_safe_spawn",n.id,p.id,p.species);return;}
            e.refreshPositionAndAngles(spawn.x(),spawn.y(),spawn.z(),0,0);e.setCustomName(Text.literal(n.name+"'s "+p.species+" · "+p.role));e.setCustomNameVisible(false);e.setPersistent();world.spawnEntity(e);partners.put(n.id,e);p.data=pokemon.saveToJSON(world.getRegistryManager(),new com.google.gson.JsonObject()).toString();LOG.info("WCA_POKEMON npc={} partner={} species={} role={} spawn={}",n.id,p.id,p.species,p.role,spawn);
        }catch(Exception failure){LOG.warn("WCA_PARTNER_UNAVAILABLE species={} cause={}",p.species,failure.toString());}
    }
    private boolean safeFor(Entity entity,Pos pos){
        double ox=entity.getX(),oy=entity.getY(),oz=entity.getZ();float yaw=entity.getYaw(),pitch=entity.getPitch();
        entity.refreshPositionAndAngles(pos.x(),pos.y(),pos.z(),yaw,pitch);
        boolean safe=world.isSpaceEmpty(entity)&&world.getFluidState(entity.getBlockPos()).isEmpty()&&world.getFluidState(entity.getBlockPos().down()).isEmpty();
        entity.refreshPositionAndAngles(ox,oy,oz,yaw,pitch);
        return safe;
    }
    private boolean routeBlocked(String settlement){return state.events.stream().anyMatch(e->e.settlement.equals(settlement)&&e.state.equals("active")&&e.type.equals("route_damage"));}
    private void events(){
        for(WorldEvent e:state.events)if(e.state.equals("active")&&state.clock>=e.ends){e.state="resolved";for(UUID id:e.witnesses){Npc n=state.npcs.get(id);if(n!=null){n.activeEvents.remove(e.id);state.remember(n,new Memory("event_resolved:"+e.type,null,e.settlement,state.clock,.7,.2,.9,"experienced"));}}LOG.info("WCA_EVENT_RESOLVED id={} type={}",e.id,e.type);}
        for(Settlement s:state.settlements.values())if(s.ready&&s.lastSocialDay<state.clock/24000){s.lastSocialDay=state.clock/24000;Random rng=new Random(s.seed^s.lastSocialDay);for(var def:data.events)if(rng.nextDouble()<def.chance()){WorldEvent e=new WorldEvent();e.id=s.id+"_"+def.type()+"_"+s.lastSocialDay;e.type=def.type();e.settlement=s.id;e.starts=state.clock;e.ends=state.clock+def.duration();e.witnesses.addAll(s.residents);state.events.add(e);for(UUID id:s.residents){Npc n=state.npcs.get(id);n.activeEvents.add(e.id);state.remember(n,new Memory("event:"+e.type,null,s.id,state.clock,.8,e.type.equals("festival")?.5:-.3,1,"experienced"));}}}
    }
    private List<vn.worldcomesalive.civilization.Lodging.Room> LodgingRooms(Building inn){return state.rooms.values().stream().filter(r->r.building.equals(inn.id)).toList();}
    public void queuePlacement(SettlementStructures.Placement placement){placements.add(placement);}
    public Npc npc(UUID id){return state.npcs.get(id);}
    public Collection<CitizenEntity> visible(){return List.copyOf(entities.values());}
    public String status(){return "Settlements "+state.settlements.size()+" | Residents "+state.npcs.size()+" | Visible "+nearbyCount+" | Transactions "+state.transactions+" | Meals "+domestic.mealsServed+" / drinks "+domestic.drinksConsumed+" / batches "+domestic.productionCompleted+" | Decisions "+state.decisions+" | AI " +lastMicros+" us | Max "+maxMicros+" us | Deferred "+deferred+" | Materializations "+state.materializations+" / dematerializations "+state.dematerializations;}
    public void save(){try{store.save(state);}catch(IOException failure){LOG.error("WCA_SAVE_FAILED: retaining in-memory state",failure);}}
    public void close(){if(terrainCapture!=null)terrainCapture.close();workers.shutdownNow();save();var visibleCitizens=List.copyOf(entities.values());entities.clear();for(var e:visibleCitizens)e.discard();for(var e:partners.values())e.discard();active=null;LOG.info("WCA_SAVED settlements={} npcs={} transactions={} identities={}",state.settlements.size(),state.npcs.size(),state.transactions,state.npcs.keySet());}
}
