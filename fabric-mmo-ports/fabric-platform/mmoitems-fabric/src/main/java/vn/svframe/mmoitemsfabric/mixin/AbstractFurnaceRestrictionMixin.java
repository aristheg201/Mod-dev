package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsLegacyItemOptions;

/** Prevents hopper/automation paths from bypassing disable-smelting. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceRestrictionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void mmoitems$restrictSmelting(World world, BlockPos pos, BlockState state,
                                                   AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        if (world.isClient) return;
        ItemStack input = furnace.getStack(0);
        if (!input.isEmpty() && MMOItemsLegacyItemOptions.bool(input, "disable-smelting", false)) ci.cancel();
    }
}
