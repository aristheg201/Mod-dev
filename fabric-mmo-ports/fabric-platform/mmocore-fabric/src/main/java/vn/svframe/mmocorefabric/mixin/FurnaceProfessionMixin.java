package vn.svframe.mmocorefabric.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceProfessionMixin {
    @Unique private static final ThreadLocal<OutputBefore> mmocore$outputBefore = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private static void mmocore$beforeFurnaceTick(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        ItemStack output = furnace.getStack(2);
        mmocore$outputBefore.set(new OutputBefore(output.isEmpty() ? ItemStack.EMPTY : output.copy(), output.getCount()));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void mmocore$afterFurnaceTick(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        OutputBefore before = mmocore$outputBefore.get();
        mmocore$outputBefore.remove();
        if (!(world instanceof ServerWorld serverWorld) || before == null) return;
        ItemStack output = furnace.getStack(2);
        if (output.isEmpty()) return;
        boolean produced = before.stack().isEmpty()
                ? output.getCount() > 0
                : ItemStack.areItemsAndComponentsEqual(before.stack(), output) && output.getCount() > before.count();
        if (!produced) return;

        ServerPlayerEntity nearest = null;
        double nearestSq = 100.0;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            double distanceSq = player.squaredDistanceTo(x, y, z);
            if (distanceSq < nearestSq) {
                nearestSq = distanceSq;
                nearest = player;
            }
        }
        if (nearest != null) MMOCoreProfessionExperienceMod.awardSmelt(nearest, output.copyWithCount(1));
    }

    @Unique
    private record OutputBefore(ItemStack stack, int count) {}
}
