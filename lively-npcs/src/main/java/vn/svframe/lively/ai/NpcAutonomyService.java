package vn.svframe.lively.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.model.WorldSnapshot;
import vn.svframe.lively.navigation.WorldNavigationService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.schedule.ScheduleEngine;
import vn.svframe.lively.social.SocialEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Connects cognition/schedules/social state to physical NPCs while keeping worker decisions immutable. */
public final class NpcAutonomyService implements AutoCloseable {
    private static final int DECISIONS_PER_PULSE = 10;
    private final NpcRuntime npcs;
    private final NpcStateRegistry states;
    private final WorldNavigationService navigation;
    private final LivelyAiEngine engine = new LivelyAiEngine();
    private final ConcurrentHashMap<UUID, Long> socialCooldown = new ConcurrentHashMap<>();
    private AiScheduler scheduler;
    private int cursor;

    public NpcAutonomyService(NpcRuntime npcs, NpcStateRegistry states, WorldNavigationService navigation) {
        this.npcs=npcs; this.states=states; this.navigation=navigation;
    }

    public void tick(MinecraftServer server, long tick) {
        syncActors();
        if (tick % 20L == 0L) applySchedules(server);
        if (tick % 40L == 0L) simulateNeeds();
        if (tick % 80L == 0L) socialPulse(server, tick);
        if (tick % 20L != 0L) return;
        ensureScheduler(server);
        List<NpcDefinition> active=npcs.snapshot().values().stream().filter(NpcDefinition::spawned).filter(NpcDefinition::aiEnabled).sorted(Comparator.comparing(d->d.id().toString())).toList();
        if(active.isEmpty())return;
        for(int i=0;i<Math.min(DECISIONS_PER_PULSE,active.size());i++){
            NpcDefinition d=active.get((cursor+i)%active.size()); NpcState state=states.get(d.id()).orElse(null); if(state==null)continue;
            NpcSnapshot npc=state.snapshot(32); WorldSnapshot world=captureWorld(server,d);
            scheduler.submit(new AiScheduler.TaskKey(d.id(),"cognition"), AiScheduler.Priority.NORMAL,npc.revision(),state::revision,
                    ()->engine.decide(npc,world).orElse(null), decision->{if(decision!=null)applyDecision(server,d.id(),decision.action());});
        }
        cursor=(cursor+DECISIONS_PER_PULSE)%active.size();
    }

    private void syncActors(){for(NpcDefinition d:npcs.snapshot().values()){NpcState state=states.get(d.id()).orElse(null);if(state==null)continue;NpcSnapshot s=state.snapshot(1);Map<String,Double> social=new HashMap<>(s.traits());social.putAll(s.needs());LivelyApi.actors().upsert(new ActorId(d.id(),ActorId.Kind.NPC),d.name(),social,Map.of("role",d.role(),"world",d.world()),Set.of("npc",d.bodyType().name().toLowerCase(java.util.Locale.ROOT)));}}

    private void applySchedules(MinecraftServer server){for(NpcDefinition d:npcs.snapshot().values()){if(!d.spawned()||!d.aiEnabled())continue;ServerWorld world=world(server,d.world());if(world==null)continue;int minute=minuteOfDay(world.getTimeOfDay());ActorId actor=new ActorId(d.id(),ActorId.Kind.NPC);ScheduleEngine.ScheduleEntry entry=LivelyApi.schedules().current(actor,minute).orElse(null);if(entry==null||entry.semanticLocation()==null||entry.semanticLocation().isBlank())continue;if(navigation.status(d.id()).isEmpty()){navigation.goToStructure(d.id(),entry.semanticLocation());states.get(d.id()).ifPresent(s->s.remember("schedule_activity",Map.of("activity",entry.activity(),"location",entry.semanticLocation()),.18D,1D));}}}

    private void simulateNeeds(){for(NpcState state:states.all()){NpcSnapshot s=state.snapshot(1);state.setNeed("hunger",Math.min(1D,s.need("hunger")+.0025D));state.setNeed("fatigue",Math.min(1D,s.need("fatigue")+.0015D));state.setNeed("social",Math.min(1D,s.need("social")+.001D));}}

    private void socialPulse(MinecraftServer server,long tick){List<NpcDefinition> active=npcs.snapshot().values().stream().filter(NpcDefinition::spawned).filter(NpcDefinition::aiEnabled).toList();for(NpcDefinition a:active){if(socialCooldown.getOrDefault(a.id(),0L)>tick)continue;Vec3d pa=npcs.position(a.id()).orElse(null);if(pa==null)continue;NpcDefinition b=active.stream().filter(other->!other.id().equals(a.id())&&other.world().equals(a.world())).filter(other->npcs.position(other.id()).map(p->p.squaredDistanceTo(pa)<=16D).orElse(false)).findFirst().orElse(null);if(b==null)continue;NpcSnapshot sa=states.snapshot(a.id()).orElse(null),sb=states.snapshot(b.id()).orElse(null);if(sa==null||sb==null)continue;double friendliness=(sa.trait("friendly")+sb.trait("friendly"))/2D;ActorId aa=new ActorId(a.id(),ActorId.Kind.NPC),bb=new ActorId(b.id(),ActorId.Kind.NPC);LivelyApi.social().apply(aa,bb,new SocialEngine.SocialDelta(.006D+.006D*friendliness,.004D+.008D*friendliness,.002D,0D,.001D,0D,.015D,"routine_social_contact",Map.of()));states.get(a.id()).ifPresent(s->{s.setNeed("social",Math.max(0D,s.snapshot(1).need("social")-.08D));s.remember("npc_socialized",Map.of("with",b.id().toString()),.16D,1D);});states.get(b.id()).ifPresent(s->s.remember("npc_socialized",Map.of("with",a.id().toString()),.16D,1D));socialCooldown.put(a.id(),tick+200L);socialCooldown.put(b.id(),tick+200L);break;}}

    private WorldSnapshot captureWorld(MinecraftServer server,NpcDefinition d){Vec3d p=npcs.position(d.id()).orElse(new Vec3d(d.x(),d.y(),d.z()));List<WorldSnapshot.ObservedEntity> entities=new ArrayList<>();for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){if(player.getServerWorld().getRegistryKey().getValue().toString().equals(d.world())&&player.getPos().squaredDistanceTo(p)<=32D*32D)entities.add(new WorldSnapshot.ObservedEntity(player.getUuid(),"player",0D));}for(NpcDefinition other:npcs.snapshot().values()){if(!other.id().equals(d.id())&&other.spawned()&&other.world().equals(d.world())&&npcs.position(other.id()).map(q->q.squaredDistanceTo(p)<=24D*24D).orElse(false))entities.add(new WorldSnapshot.ObservedEntity(other.id(),"npc",0D));}return new WorldSnapshot(System.nanoTime(),d.world(),server.getTicks(),entities,Map.of());}

    private void applyDecision(MinecraftServer server,UUID npcId,AiAction action){NpcDefinition d=npcs.get(npcId).orElse(null);if(d==null)return;switch(action.type()){
        case "travel_home"->{String home=d.metadata().get("home.structure");if(home!=null)navigation.goToStructure(npcId,home);}
        case "perform_occupation"->{String work=d.metadata().get("work.structure");if(work!=null)navigation.goToStructure(npcId,work);}
        case "seek_food"->{nearestStructure(d,"restaurant","shop","market").ifPresent(id->navigation.goToStructure(npcId,id));}
        case "start_dialogue"->startNearbyDialogue(server,d);
        case "consume_food"->states.get(npcId).ifPresent(s->s.setNeed("hunger",Math.max(0D,s.snapshot(1).need("hunger")-.20D)));
        default->states.get(npcId).ifPresent(s->s.remember("ai_decision",Map.of("action",action.type()),.08D,1D));
    }}

    private java.util.Optional<String> nearestStructure(NpcDefinition d,String...types){Vec3d p=npcs.position(d.id()).orElse(new Vec3d(d.x(),d.y(),d.z()));Set<String>wanted=Set.of(types);return LivelyApi.structures().snapshot().structures().values().stream().filter(s->s.bounds().world().equals(d.world())&&wanted.contains(s.type().toLowerCase(java.util.Locale.ROOT))).min(Comparator.comparingDouble(s->center(s.bounds()).squaredDistanceTo(p))).map(SemanticStructureRegistry.Structure::id);}
    private void startNearbyDialogue(MinecraftServer server,NpcDefinition d){if(!Boolean.parseBoolean(d.metadata().getOrDefault("dialogue.auto","false")))return;DialogueService dialogues=LivelyApi.dialogues();if(dialogues==null)return;Vec3d p=npcs.position(d.id()).orElse(null);if(p==null)return;server.getPlayerManager().getPlayerList().stream().filter(player->player.getServerWorld().getRegistryKey().getValue().toString().equals(d.world())&&player.getPos().squaredDistanceTo(p)<=16D&&dialogues.session(player.getUuid()).isEmpty()).findFirst().ifPresent(player->dialogues.start(player,d.id(),d.name(),d.role()));}
    private static Vec3d center(SemanticStructureRegistry.Bounds b){return new Vec3d((b.minX()+b.maxX()+1)/2D,b.minY()+1D,(b.minZ()+b.maxZ()+1)/2D);}private static int minuteOfDay(long time){long ticks=Math.floorMod(time+6000L,24000L);return(int)(ticks*1440L/24000L);}private static ServerWorld world(MinecraftServer server,String key){net.minecraft.util.Identifier id=net.minecraft.util.Identifier.tryParse(key);return id==null?null:server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,id));}
    private void ensureScheduler(MinecraftServer server){if(scheduler==null)scheduler=new AiScheduler(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()/2)),1024,server::execute);}
    @Override public void close(){if(scheduler!=null)scheduler.close();socialCooldown.clear();}
}
