package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerGuardMixin {
    @Inject(method="dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",at=@At("HEAD"),cancellable=true)
    private void svextract$drop(ItemStack stack,boolean throwRandomly,boolean retainOwnership,CallbackInfoReturnable<ItemEntity> ci){
        if((Object)this instanceof ServerPlayerEntity p && SvExtract.restricted(p))ci.setReturnValue(null);
    }
}
