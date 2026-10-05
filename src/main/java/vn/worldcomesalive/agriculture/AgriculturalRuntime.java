package vn.worldcomesalive.agriculture;
import vn.worldcomesalive.server.WorldSimulation;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementStructures;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
import net.minecraft.registry.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import java.util.*;

public final class AgriculturalRuntime {
    private final WorldSimulation sim;
    private record Due(long clock,String settlement,UUID plot){}
    private final PriorityQueue<Due> due=new PriorityQueue<>(Comparator.comparingLong(Due::clock));
    private final Set<Long> loadedChunks=new LinkedHashSet<>();
    public AgriculturalRuntime(WorldSimulation sim){this.sim=sim;for(Settlement s:sim.state.settlements.values())s.fields.values().forEach(p->due.add(new Due(p.due,s.id,p.id)));}
    public void register(Settlement s){s.fields.values().forEach(p->due.add(new Due(p.due,s.id,p.id)));}
    public void tick(){var batch=loadedChunks.stream().limit(8).toList();loadedChunks.removeAll(batch);
        // Reading a loaded block can complete other chunk futures and re-enter chunk().
        // Drain before touching the world; never retain a live set iterator across those reads.
        for(long key:batch){var chunk=new ChunkPos(key);for(Settlement s:sim.state.settlements.values())if(s.ready)for(var p:s.fields.values())if(p.cells.stream().anyMatch(c->((int)c.x()>>4)==chunk.x&&((int)c.z()>>4)==chunk.z))sync(p);}
        int budget=0;while(!due.isEmpty()&&due.peek().clock<=sim.state.clock&&budget++<8){var e=due.remove();Settlement s=sim.state.settlements.get(e.settlement);var p=s==null?null:s.fields.get(e.plot);if(p==null||p.due!=e.clock)continue;
        switch(p.state){case "GROWING","PLANTED"->p.state="MATURE";case "HARVESTED","PLOWED"->p.state="PLANTED";default->{continue;}}
        p.due=sim.state.clock+2400;due.add(new Due(p.due,s.id,p.id));sync(p);
    }if(sim.state.clock%60==0)animals();}
    public void work(Npc farmer,Settlement s){var p=Agriculture.workPlot(s,farmer);if(p==null)return;Building storage=s.buildings.get(p.storage);int yield=Agriculture.harvest(p,storage);if(yield>0){farmer.activity="harvesting field";p.due=sim.state.clock+400;due.add(new Due(p.due,s.id,p.id));sim.state.transact(p.id.toString(),storage.id,p.crop,yield,0,"registered field harvest");farmer.skills.merge("farming",.002,Double::sum);sync(p);}else farmer.activity=p.state.equals("HARVESTED")?"planting field":"tending field";
        for(var a:s.livestock.values())if(a.alive&&sim.state.clock-a.lastFed>2400&&storage.stock.getOrDefault("minecraft:wheat",0)>0){storage.stock.merge("minecraft:wheat",-1,Integer::sum);a.lastFed=sim.state.clock;storage.stock.merge(a.species.equals("cow")?"minecraft:milk_bucket":a.species.equals("chicken")?"minecraft:egg":"minecraft:white_wool",1,Integer::sum);sim.state.transact(storage.id,a.id.toString(),"minecraft:wheat",1,0,"livestock feeding and care");}
    }
    private void sync(Agriculture.Plot p){int age=p.state.equals("MATURE")?7:p.state.equals("GROWING")?4:p.state.equals("PLANTED")?1:0;for(Pos cell:p.cells){BlockPos ground=BlockPos.ofFloored(cell.x(),cell.y(),cell.z());if(!sim.world.isChunkLoaded(ground.getX()>>4,ground.getZ()>>4))continue;Block crop=Registries.BLOCK.get(net.minecraft.util.Identifier.of(p.crop.equals("minecraft:carrot")?"minecraft:carrots":p.crop.equals("minecraft:potato")?"minecraft:potatoes":"minecraft:wheat"));if(sim.world.getBlockState(ground).isOf(Blocks.FARMLAND)){var state=p.state.equals("HARVESTED")?Blocks.AIR.getDefaultState():crop.getDefaultState().with(CropBlock.AGE,age);sim.queuePlacement(new SettlementStructures.Placement(ground.up(),state));}}}
    /** Native chunk-load callbacks precede completion of their chunk future. Never read blocks there. */
    public void chunk(net.minecraft.util.math.ChunkPos chunk){loadedChunks.add(chunk.toLong());}
    public void refresh(Settlement s){for(var p:s.fields.values())sync(p);}
    private void animals(){for(Settlement s:sim.state.settlements.values())for(var a:s.livestock.values()){
        boolean relevant=a.alive&&sim.world.isChunkLoaded((int)a.location.x()>>4,(int)a.location.z()>>4)&&sim.world.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(a.location.x(),a.location.y(),a.location.z())<10000);Entity old=sim.world.getEntity(a.id);if(relevant&&old==null){EntityType<?> type=a.species.equals("cow")?EntityType.COW:a.species.equals("chicken")?EntityType.CHICKEN:EntityType.SHEEP;Entity e=type.create(sim.world);if(e!=null){e.setUuid(a.id);e.refreshPositionAndAngles(a.location.x(),a.location.y(),a.location.z(),0,0);if(e instanceof net.minecraft.entity.mob.MobEntity mob)mob.setPersistent();sim.world.spawnEntity(e);}}else if(old!=null){a.location=new Pos(old.getX(),old.getY(),old.getZ());if(!relevant)old.discard();}
    }}
}
