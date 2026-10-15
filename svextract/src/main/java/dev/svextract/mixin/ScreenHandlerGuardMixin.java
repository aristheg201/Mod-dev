package dev.svextract.mixin;

import dev.svextract.SvExtract;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerGuardMixin {
    @Inject(method="onSlotClick",at=@At("HEAD"),cancellable=true)
    private void svextract$slot(int slot,int button,SlotActionType action,PlayerEntity player,CallbackInfo ci){
        if(player instanceof ServerPlayerEntity p && (SvExtract.locked(p)||
                (SvExtract.restricted(p)&&(p.currentScreenHandler!=p.playerScreenHandler||action==SlotActionType.THROW))))ci.cancel();
    }
}
