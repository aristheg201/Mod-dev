package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mission encounters control natural spawning without changing shared save game rules. */
@Mixin(SpawnHelper.class)
public abstract class NaturalSpawnGuardMixin {
    @Inject(method="spawn",at=@At("HEAD"),cancellable=true)
    private static void svextract$naturalSpawning(ServerWorld world,WorldChunk chunk,
            SpawnHelper.Info info,boolean animals,boolean monsters,boolean rare,CallbackInfo ci) {
        if(SvExtract.isolated(world))ci.cancel();
    }
}
