package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fires PLACE_BLOCK only after vanilla reports a successful BlockItem placement. */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceTriggerMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mythiclib$placedBlock(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted() || context.getWorld().isClient) return;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;

        BlockPos pos = context.getBlockPos();
        BlockState state = context.getWorld().getBlockState(pos);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("block", Registries.BLOCK.getId(state.getBlock()).toString());
        data.put("block-x", pos.getX());
        data.put("block-y", pos.getY());
        data.put("block-z", pos.getZ());
        data.put("hand", context.getHand().name());
        data.put("item", Registries.ITEM.getId(context.getStack().getItem()).toString());
        MythicLibPassiveMod.fire(player.getUuid(), "PLACE_BLOCK", player.getUuid(), data);
    }
}
