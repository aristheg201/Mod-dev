package vn.svframe.mmoitemsfabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.mmoitemsfabric.MMOItemsRevisionRuntime;

/** Refreshes stale clicked/cursor items at the same inventory ingress used by the original listener. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerRevisionMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void mmoitems$refreshRevision(int slotIndex, int button, SlotActionType actionType,
                                          PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        ItemStack cursor = handler.getCursorStack();
        ItemStack cursorRefresh = MMOItemsRevisionRuntime.refresh(cursor, MMOItemsRevisionRuntime.Reason.CLICK);
        if (cursorRefresh != null) handler.setCursorStack(cursorRefresh);

        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;
        Slot slot = handler.getSlot(slotIndex);
        MMOItemsRevisionRuntime.Reason reason = slot instanceof CraftingResultSlot
                ? MMOItemsRevisionRuntime.Reason.CRAFT : MMOItemsRevisionRuntime.Reason.CLICK;
        ItemStack refreshed = MMOItemsRevisionRuntime.refresh(slot.getStack(), reason);
        if (refreshed != null) {
            slot.setStack(refreshed);
            slot.markDirty();
        }
    }
}
