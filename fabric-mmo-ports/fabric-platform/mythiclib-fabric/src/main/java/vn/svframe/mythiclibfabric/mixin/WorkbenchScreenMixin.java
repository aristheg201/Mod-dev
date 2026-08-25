package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mythiclibfabric.MythicLibWorkbenchMod;

/** Protects custom workbench edge/result slots and owns sparse-grid shift-click semantics. */
@Mixin(ScreenHandler.class)
public abstract class WorkbenchScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mythiclib$workbenchClick(int slotIndex, int button, SlotActionType actionType,
                                           PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && MythicLibWorkbenchMod.handleClick(serverPlayer, (ScreenHandler) (Object) this, slotIndex, actionType)) {
            ci.cancel();
        }
    }
}
