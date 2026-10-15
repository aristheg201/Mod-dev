package dev.svextract.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.util.math.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Native asynchronous chunk loading; world and ticket calls remain on the server thread. */
public final class ChunkPreparation implements AutoCloseable {
    private static final ChunkTicketType<Long> OWNED=ChunkTicketType.create("svextract_preparation",Long::compareTo);
    private static final java.util.concurrent.atomic.AtomicLong IDENTITIES=new java.util.concurrent.atomic.AtomicLong();
    private final long identity=IDENTITIES.incrementAndGet();
    private final ServerWorld world;
    private final List<ChunkPos> positions;
    private final Map<ChunkPos,CompletableFuture<OptionalChunk<Chunk>>> pending=new LinkedHashMap<>();
    private int requested,completed;
    private boolean closed;
    public ChunkPreparation(ServerWorld world,List<ChunkPos> positions){this.world=world;this.positions=List.copyOf(positions);}
    public boolean tick(int budget){
        if(closed)throw new IllegalStateException("Chunk preparation is closed");
        int pendingLimit=budget;
        var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();if(!entry.getValue().isDone())continue;
            OptionalChunk<Chunk> result=entry.getValue().getNow(null);
            if(result==null||!result.isPresent())throw new IllegalStateException("Chunk preparation failed: "+entry.getKey()+" "+(result==null?"missing result":result.getError()));
            completed++;iterator.remove();
        }
        while(budget-->0&&requested<positions.size()&&pending.size()<pendingLimit){
            ChunkPos position=positions.get(requested++);
            // Level 33 keeps FULL chunks resident without forcing block/entity ticking.
            world.getChunkManager().addTicket(OWNED,position,0,identity);
            pending.put(position,world.getChunkManager().getChunkFutureSyncOnMainThread(position.x,position.z,ChunkStatus.FULL,true));
        }
        return completed==positions.size();
    }
    @Override public void close(){
        if(closed)return;closed=true;
        for(int i=0;i<requested;i++){ChunkPos position=positions.get(i);world.getChunkManager().removeTicket(OWNED,position,0,identity);}
        pending.clear();
    }
    public int retainedChunks(){return closed?0:requested;}
}
