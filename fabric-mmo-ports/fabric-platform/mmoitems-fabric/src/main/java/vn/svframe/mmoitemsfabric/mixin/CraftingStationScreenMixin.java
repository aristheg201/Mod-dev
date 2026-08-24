package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsCraftingStationMod;

/** Makes vanilla generic-container slots act as server-authoritative crafting station controls. */
@Mixin(ScreenHandler.class)
public abstract class CraftingStationScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mmoitems$craftingStationClick(int slotIndex, int button, SlotActionType actionType,
                                                PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && MMOItemsCraftingStationMod.handleClick(serverPlayer, (ScreenHandler) (Object) this, slotIndex, actionType)) {
            ci.cancel();
        }
    }
}
