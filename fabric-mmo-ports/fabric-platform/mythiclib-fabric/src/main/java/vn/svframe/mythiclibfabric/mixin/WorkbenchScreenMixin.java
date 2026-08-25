package vn.svframe.mythiclibfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mythiclibfabric.MythicLibVanillaCraftingMod;
import vn.svframe.mythiclibfabric.MythicLibWorkbenchMod;

/** Owns MythicLib result-click semantics for custom and vanilla crafting stations. */
@Mixin(ScreenHandler.class)
public abstract class WorkbenchScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void mythiclib$craftingClick(int slotIndex, int button, SlotActionType actionType,
                                         PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (MythicLibWorkbenchMod.handleClick(serverPlayer, handler, slotIndex, actionType)
                || MythicLibVanillaCraftingMod.handleClick(serverPlayer, handler, slotIndex, actionType)) {
            ci.cancel();
        }
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void mythiclib$refreshVanillaCrafting(int slotIndex, int button, SlotActionType actionType,
                                                   PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity) MythicLibVanillaCraftingMod.refresh((ScreenHandler) (Object) this);
    }
}
