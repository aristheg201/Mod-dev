package vn.svarcade.tcg.mixin;
import net.minecraft.server.world.*;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;
/** The public SyncOnMainThread wrapper waits for completion; WCA budgets genuinely asynchronous requests. */
@Mixin(ServerChunkManager.class)
public interface GenerationChunkAccess {
    @Invoker("getChunkFuture") CompletableFuture<OptionalChunk<Chunk>> wca$requestChunk(int x,int z,ChunkStatus status,boolean create);
}
