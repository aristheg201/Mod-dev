package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsRevisionRuntime;

/** Refreshes a stale MMOItem immediately before the server gives it to a player. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityRevisionMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"))
    private void mmoitems$refreshPickup(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemStack refreshed = MMOItemsRevisionRuntime.refresh(itemEntity.getStack(), MMOItemsRevisionRuntime.Reason.PICKUP);
        if (refreshed != null) itemEntity.setStack(refreshed);
    }
}
