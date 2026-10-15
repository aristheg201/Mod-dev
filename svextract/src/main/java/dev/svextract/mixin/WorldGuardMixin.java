package dev.svextract.mixin;

import dev.svextract.SvExtract;
import dev.svextract.world.WorldMutationScope;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldGuardMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void svextract$protect(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> ci){
        World world=(World)(Object)this;
        if(!world.isClient && SvExtract.runtime()!=null && SvExtract.isolated(world) && !WorldMutationScope.allowed())ci.setReturnValue(false);
    }
}
