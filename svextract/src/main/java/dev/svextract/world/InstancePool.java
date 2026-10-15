package dev.svextract.world;

import dev.svextract.config.ServerConfig;
import dev.svextract.performance.Telemetry;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Prepared map pools share global admission/mutation budgets. All world access stays on the server thread. */
public final class InstancePool {
    public enum State { PREPARING, READY, ADMITTING, ACTIVE, CLEANING, FAILED }
    public static final class Slot {
        public final int index,originX;
        public final String map;
        public State state=State.PREPARING;
        private int blockCursor;
        private final List<StationBlueprint.Change> blueprint;
        private final LinkedHashMap<BlockPos,net.minecraft.block.BlockState> restoration=new LinkedHashMap<>();
        private final List<ChunkPos> chunks=new ArrayList<>();
        private List<ChunkPos> admissionChunks;
        private ChunkPreparation preparation;
        private long preparationStart=System.nanoTime();
        public long preparationNanos,cleanupNanos;
        public String failure;
        private Slot(int index,int originX,String map,List<StationBlueprint.Change> blueprint){
            this.index=index;this.originX=originX;this.map=map;this.blueprint=blueprint;
            int min=map.equals("redline_station")?-3:-4,max=map.equals("redline_station")?2:3;
            int minZ=map.equals("redline_station")?-2:-4,maxZ=map.equals("redline_station")?1:3;
            for(int x=min;x<=max;x++)for(int z=minZ;z<=maxZ;z++)chunks.add(new ChunkPos((originX>>4)+x,z));
        }
        public BlockPos at(int x,int y,int z){return new BlockPos(originX+x,y,z);}
        public boolean contains(double x,double y,double z){
            return map.equals("redline_station")?Math.abs(x-originX)<=35&&Math.abs(z)<=27&&y>=63&&y<256
                :Math.abs(x-originX)<=51&&Math.abs(z)<=51&&y>=14&&y<128;
        }
        public int retainedChunks(){return preparation==null?0:preparation.retainedChunks();}
    }
    private final ServerWorld world;
    private final ServerConfig config;
    private final Telemetry metrics;
    private final List<Slot> slots=new ArrayList<>();
    private int roundRobin;
    public InstancePool(ServerWorld world,ServerConfig config,Telemetry metrics){this(world,config,metrics,BlackglassBlueprint.create());}
    public InstancePool(ServerWorld world,ServerConfig config,Telemetry metrics,List<StationBlueprint.Change> blackglass){
        this.world=world;this.config=config;this.metrics=metrics;
        List<StationBlueprint.Change> redline=DenseMapDecorator.redline(StationBlueprint.redline());
        for(int i=0;i<config.performance().instanceCapacity();i++){
            int first=i*2;
            slots.add(new Slot(first,first*config.mission().regionSpacing(),"redline_station",redline));
            slots.add(new Slot(first+1,(first+1)*config.mission().regionSpacing(),"blackglass_industrial",blackglass));
        }
    }
    public void tick(boolean permitPreparation){
        int blocks=config.performance().blocksPerTick(),chunks=config.performance().chunksPerTick();
        for(int n=0;n<slots.size()&&blocks>0;n++){
            Slot slot=slots.get((roundRobin+n)%slots.size());
            try{
            if(slot.state==State.CLEANING){
                Iterator<Map.Entry<BlockPos,net.minecraft.block.BlockState>> changes=slot.restoration.entrySet().iterator();
                while(blocks>0&&changes.hasNext()){
                    var change=changes.next();world.setBlockState(change.getKey(),change.getValue(),Block.NOTIFY_LISTENERS);
                    changes.remove();blocks--;metrics.blocks(1);
                }
                if(slot.restoration.isEmpty()){
                    slot.cleanupNanos=System.nanoTime()-slot.preparationStart;release(slot);slot.state=State.READY;
                }
            }else if(slot.state==State.ADMITTING||slot.state==State.PREPARING&&permitPreparation){
                if(slot.preparation==null)slot.preparation=new ChunkPreparation(world,slot.state==State.ADMITTING?slot.admissionChunks:slot.chunks);
                boolean loaded=slot.preparation.tick(chunks);chunks=0;
                if(!loaded)continue;
                if(slot.state==State.ADMITTING){slot.state=State.ACTIVE;continue;}
                while(blocks>0&&slot.blockCursor<slot.blueprint.size()){
                    StationBlueprint.Change change=slot.blueprint.get(slot.blockCursor++);
                    BlockPos position=change.position().add(slot.originX,0,0);
                    if(!world.getBlockState(position).equals(change.state())){
                        if(!world.setBlockState(position,change.state(),Block.NOTIFY_LISTENERS))throw new IllegalStateException("Map block mutation rejected at "+position);
                        metrics.blocks(1);
                    }
                    blocks--;
                }
                if(slot.blockCursor==slot.blueprint.size()){
                    slot.preparationNanos=System.nanoTime()-slot.preparationStart;release(slot);slot.state=State.READY;
                }
            }
            }catch(RuntimeException failure){
                slot.failure=failure.getMessage();slot.state=State.FAILED;
                try{release(slot);}catch(RuntimeException releaseFailure){failure.addSuppressed(releaseFailure);}
                dev.svextract.SvExtract.LOGGER.error("Instance {} map {} failed; its admissions are stopped and other instances continue",slot.index,slot.map,failure);
            }
        }
        roundRobin=(roundRobin+1)%slots.size();
    }
    public Optional<Slot> claim(){return claim("redline_station");}
    public Optional<Slot> claim(String map){
        long occupied=slots.stream().filter(s->s.state==State.ADMITTING||s.state==State.ACTIVE||s.state==State.CLEANING).count();
        if(occupied>=config.performance().instanceCapacity())return Optional.empty();
        for(Slot slot:slots)if(slot.map.equals(map)&&slot.state==State.READY){
            // Prior implementation prepared (viewDistance + mapRadius)^2 chunks at every admission,
            // e.g. 841 chunks at view distance 10. Limit pre-admission to the authored map footprint.
            // Normal vanilla player tracking loads the outer client view asynchronously as needed.
            int view=world.getServer().getPlayerManager().getViewDistance();
            int radius=Math.min(view,slot.map.equals("redline_station")?3:4);
            List<ChunkPos> tracking=new ArrayList<>((radius*2+1)*(radius*2+1));
            for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)tracking.add(new ChunkPos((slot.originX>>4)+x,z));
            slot.admissionChunks=List.copyOf(tracking);slot.state=State.ADMITTING;return Optional.of(slot);
        }
        return Optional.empty();
    }
    public void cleanup(Slot slot){
        if(slot.state!=State.ACTIVE&&slot.state!=State.ADMITTING)return;
        slot.state=State.CLEANING;slot.preparationStart=System.nanoTime();
    }
    public void beforeMutation(Slot slot,BlockPos position){
        if(slot.state!=State.ACTIVE||!slot.contains(position.getX(),position.getY(),position.getZ()))throw new IllegalArgumentException("Mutation is outside the active instance");
        if(!slot.restoration.containsKey(position)&&slot.restoration.size()>=slot.blueprint.size())throw new IllegalStateException("Restoration budget exceeded");
        slot.restoration.putIfAbsent(position.toImmutable(),world.getBlockState(position));
    }
    private void release(Slot slot){if(slot.preparation!=null){slot.preparation.close();slot.preparation=null;}}
    public void stop(){for(Slot slot:slots)release(slot);}
    public List<Slot> slots(){return Collections.unmodifiableList(slots);}
}
