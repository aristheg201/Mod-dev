package vn.svframe.mmocorefabric.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreSpecialProfessionExperienceMod;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingProfessionMixin {
    @Unique private static final ThreadLocal<ItemStack[]> mmocore$before = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private static void mmocore$beforeBrew(World world, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
        if (!(world instanceof ServerWorld)) return;
        ItemStack[] before = new ItemStack[3];
        for (int i = 0; i < 3; i++) before[i] = blockEntity.getStack(i).copy();
        mmocore$before.set(before);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void mmocore$afterBrew(World world, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
        ItemStack[] before = mmocore$before.get();
        mmocore$before.remove();
        if (!(world instanceof ServerWorld serverWorld) || before == null) return;
        for (int i = 0; i < 3; i++) {
            ItemStack after = blockEntity.getStack(i);
            if (after.isEmpty() || ItemStack.areEqual(before[i], after)) continue;
            MMOCoreSpecialProfessionExperienceMod.awardBrew(serverWorld, pos, before[i], after.copy());
        }
    }
}
