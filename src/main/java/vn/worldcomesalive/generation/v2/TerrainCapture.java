package vn.worldcomesalive.generation.v2;
import net.minecraft.server.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Server-thread sampling after native chunk futures complete; bounded requests never block a chunk callback. */
public final class TerrainCapture implements AutoCloseable {
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("wca_generation_v2",Comparator.comparingLong(ChunkPos::toLong));
    private final ServerWorld world;private final int minX,minZ,size=127,step=3;private final ArrayDeque<ChunkPos> pending=new ArrayDeque<>();private final Map<ChunkPos,CompletableFuture<OptionalChunk<Chunk>>> inflight=new LinkedHashMap<>();private final Set<ChunkPos> retained=new HashSet<>();private final Map<Long,Chunk> ready=new HashMap<>();
    public TerrainCapture(ServerWorld w,int x,int z){world=w;minX=x-189;minZ=z-189;for(int cx=minX>>4;cx<=(minX+378)>>4;cx++)for(int cz=minZ>>4;cz<=(minZ+378)>>4;cz++)pending.add(new ChunkPos(cx,cz));}
    public boolean tick(){for(var item:new ArrayList<>(inflight.entrySet()))if(item.getValue().isDone()){Chunk chunk=item.getValue().join().orElse(null);if(chunk==null)throw new IllegalArgumentException("Terrain chunk unavailable");ready.put(item.getKey().toLong(),chunk);inflight.remove(item.getKey());}while(inflight.size()<4&&!pending.isEmpty()){ChunkPos pos=pending.removeFirst();world.getChunkManager().addTicket(TICKET,pos,33,pos);retained.add(pos);inflight.put(pos,world.getChunkManager().getChunkFutureSyncOnMainThread(pos.x,pos.z,ChunkStatus.FULL,true));}return pending.isEmpty()&&inflight.isEmpty();}
    public TerrainSnapshot snapshot(){if(!pending.isEmpty()||!inflight.isEmpty())throw new IllegalStateException("Terrain not complete");List<TerrainSnapshot.Sample> samples=new ArrayList<>();for(int z=0;z<size;z++)for(int x=0;x<size;x++){int wx=minX+x*step,wz=minZ+z*step;Chunk c=ready.get(ChunkPos.toLong(wx>>4,wz>>4));int y=c.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,wx&15,wz&15);var ground=c.getBlockState(new BlockPos(wx,y,wz));boolean water=!ground.getFluidState().isEmpty();double forest=ground.isIn(net.minecraft.registry.tag.BlockTags.LOGS)?1:0;samples.add(new TerrainSnapshot.Sample(y,water,forest));}return new TerrainSnapshot(minX,minZ,size,step,samples);}
    public int remaining(){return pending.size()+inflight.size();}
    public void close(){for(var pos:retained)world.getChunkManager().removeTicket(TICKET,pos,33,pos);retained.clear();ready.clear();}
}
