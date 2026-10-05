package vn.worldcomesalive;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.world.CitizenEntity;
import vn.worldcomesalive.server.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.resource.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.*;
import net.minecraft.resource.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public final class WorldComesAlive implements ModInitializer {
    public static final EntityType<CitizenEntity> CITIZEN=Registry.register(Registries.ENTITY_TYPE,Identifier.of("worldcomesalive","citizen"),EntityType.Builder.create(CitizenEntity::new,SpawnGroup.CREATURE).dimensions(.6f,1.95f).maxTrackingRange(10).build("worldcomesalive:citizen"));
    public static WorldContent content=WorldContent.defaults();
    public static Interactions interactions;
    @Override public void onInitialize(){
        vn.worldcomesalive.furniture.FurnitureRegistry.initialize();
        vn.worldcomesalive.domestic.DomesticItems.initialize();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(CITIZEN,VillagerEntity.createVillagerAttributes());
        PayloadTypeRegistry.playC2S().register(InteractionPackets.Input.ID,InteractionPackets.Input.CODEC);PayloadTypeRegistry.playS2C().register(InteractionPackets.Snapshot.ID,InteractionPackets.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(InteractionPackets.Input.ID,(p,ctx)->ctx.server().execute(()->{if(interactions!=null)interactions.handle(ctx.player(),p);}));
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
            public Identifier getFabricId(){return Identifier.of("worldcomesalive","content");}
            public void reload(ResourceManager resources){try{var generation=resources.getResource(Identifier.of("worldcomesalive","generation/v2.json"));if(generation.isPresent())try(var reader=generation.get().getReader()){vn.worldcomesalive.generation.v2.GenerationCatalog.active=vn.worldcomesalive.generation.v2.GenerationCatalog.read(reader);}var furnishing=resources.getResource(Identifier.of("worldcomesalive","living_world/furnishing.json"));if(furnishing.isPresent())try(var reader=furnishing.get().getReader()){vn.worldcomesalive.furniture.FurnishingContent.active=vn.worldcomesalive.furniture.FurnishingContent.read(reader);}var domestic=resources.getResource(Identifier.of("worldcomesalive","living_world/domestic.json"));if(domestic.isPresent())try(var reader=domestic.get().getReader()){var next=vn.worldcomesalive.domestic.DomesticContent.read(reader);if(!next.goods.keySet().equals(vn.worldcomesalive.domestic.DomesticContent.active.goods.keySet()))throw new IllegalArgumentException("Domestic registry IDs must remain stable; change metadata or install new items before server initialization");vn.worldcomesalive.domestic.DomesticContent.active=next;}var resource=resources.getResource(Identifier.of("worldcomesalive","living_world/default.json"));if(resource.isPresent())try(var reader=resource.get().getReader()){WorldContent next=WorldContent.read(reader);content=next;var sim=WorldSimulation.active();if(sim!=null)sim.server.execute(()->sim.reload(next));}}catch(Exception invalid){org.slf4j.LoggerFactory.getLogger("world-comes-alive").error("Invalid datapack content; retaining the last validated snapshot",invalid);}}
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{new WorldSimulation(server,content);interactions=new Interactions(WorldSimulation.active());for(var p:server.getOverworld().getPlayers())WorldSimulation.active().discover(new net.minecraft.util.math.ChunkPos(p.getBlockPos()));}catch(Exception failure){throw new IllegalStateException("World Comes Alive could not load persistent state safely",failure);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(WorldSimulation.active()!=null)WorldSimulation.active().close();interactions=null;});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(WorldSimulation.active()!=null)WorldSimulation.active().tick();});
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{var sim=WorldSimulation.active();if(sim!=null&&world==sim.world){sim.discover(chunk.getPos());sim.agriculture.chunk(chunk.getPos());}});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof CitizenEntity citizen&&WorldSimulation.active()!=null)WorldSimulation.active().citizenLoaded(citizen);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof CitizenEntity citizen&&WorldSimulation.active()!=null)WorldSimulation.active().queueCitizenUnload(citizen);});
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{if(entity instanceof CitizenEntity citizen){if(!world.isClient&&player instanceof ServerPlayerEntity server&&interactions!=null)interactions.open(server,citizen);return ActionResult.SUCCESS;}return ActionResult.PASS;});
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{if(!world.isClient&&player instanceof ServerPlayerEntity server&&entity instanceof CitizenEntity&&interactions!=null){var n=WorldSimulation.active().npc(entity.getUuid());if(n!=null)interactions.crime(server,n,"assault",30);}return ActionResult.PASS;});
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{var sim=WorldSimulation.active();if(!world.isClient&&sim!=null&&player instanceof ServerPlayerEntity p&&!sim.lodging.permitted(p,hit.getBlockPos())){p.sendMessage(Text.literal("This guest room is private. Speak to the innkeeper."),true);return ActionResult.FAIL;}return ActionResult.PASS;});
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{var sim=WorldSimulation.active();return sim==null||!(player instanceof ServerPlayerEntity p)||sim.lodging.permitted(p,pos);});
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,blockEntity)->{
            var sim=WorldSimulation.active();if(sim==null||world!=sim.world)return;sim.domestic.breakContainer(world,pos);var life=sim.state.players.computeIfAbsent(player.getUuid(),id->new vn.worldcomesalive.model.LivingWorld.PlayerLife());String block=Registries.BLOCK.getId(state.getBlock()).toString();life.skills.merge(block.contains("ore")?"mining":block.contains("wheat")?"farming":block.contains("log")?"foraging":"crafting",.001,Double::sum);
            if(player instanceof ServerPlayerEntity actor)for(var s:sim.state.settlements.values())for(var b:s.buildings.values())if(b.contains(new vn.worldcomesalive.model.LivingWorld.Pos(pos.getX(),pos.getY(),pos.getZ()))&&!life.property.contains(b.id)){var victim=s.residents.stream().map(sim.state.npcs::get).filter(n->n.home.equals(b.id)).findFirst().orElse(null);if(victim!=null)interactions.crime(actor,victim,"theft",10);}
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(WorldSimulation.active()==null)return;WorldSimulation.active().dungeons.death(entity.getUuid());if(!(entity instanceof CitizenEntity)){for(var s:WorldSimulation.active().state.settlements.values()){var animal=s.livestock.get(entity.getUuid());if(animal!=null)animal.alive=false;}return;}
            var sim=WorldSimulation.active();var n=sim.npc(entity.getUuid());if(n==null)return;
            n.lifeStage="deceased";n.activity="deceased";n.goal="dead";n.plan.clear();n.travel=null;
            if(source.getAttacker() instanceof ServerPlayerEntity actor)interactions.crime(actor,n,"murder",100);
            for(UUID family:sim.state.settlements.get(n.settlement).households.get(n.household).members)if(!family.equals(n.id)){var relative=sim.npc(family);relative.emotion="grieving";sim.state.remember(relative,new vn.worldcomesalive.model.LivingWorld.Memory("family_death",n.id,n.home,sim.state.clock,1,-1,1,"household"));}
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(interactions!=null)interactions.disconnect(handler.player.getUuid());});
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("wca")
            .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(()->Text.literal(WorldSimulation.active().status()),false);return 1;}))
            .then(literal("locate").executes(ctx->{var sim=WorldSimulation.active();var p=sim.nearestCandidate(net.minecraft.util.math.BlockPos.ofFloored(ctx.getSource().getPosition()));ctx.getSource().sendFeedback(()->Text.literal("Living-world survey site: "+p.getCenterX()+" ~ "+p.getCenterZ()+". Settlements require suitable dry terrain."),false);return 1;}))
            .then(literal("save").requires(s->s.hasPermissionLevel(2)).executes(ctx->{WorldSimulation.active().save();ctx.getSource().sendFeedback(()->Text.literal("Living-world identities checkpointed."),false);return 1;}))));
    }
}
