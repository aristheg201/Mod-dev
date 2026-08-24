package vn.svframe.mmocorefabric.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mmocorefabric.MMOCoreProfessionExperienceMod;

@Mixin(BlockItem.class)
public abstract class BlockItemProfessionMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mmocore$professionPlaceBlock(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted() || context.getWorld().isClient) return;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;
        BlockPos pos = context.getBlockPos();
        MMOCoreProfessionExperienceMod.markPlaced(
                player,
                context.getWorld().getRegistryKey().getValue().toString(),
                pos,
                context.getWorld().getBlockState(pos));
    }
}
