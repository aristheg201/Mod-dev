package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemGuardMixin {
    @Inject(method="onPlayerCollision",at=@At("HEAD"),cancellable=true)
    private void svextract$pickup(PlayerEntity player,CallbackInfo ci){
        if(player instanceof ServerPlayerEntity p&&(SvExtract.locked(p)||SvExtract.isolated(p.getWorld())))ci.cancel();
    }
}
